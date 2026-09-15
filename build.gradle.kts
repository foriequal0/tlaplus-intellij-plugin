import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.*

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
    id("de.undercouch.download") version "5.7.0"
}

group = "com.mayreh.intellij.plugins"
val snapshot = providers.gradleProperty("snapshot").map { it.toBoolean() }.getOrElse(false)
version = "${version}" + if (snapshot) { "-SNAPSHOT" } else { "" }

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val tla2tools = layout.buildDirectory.file("libs/tla2tools.jar")
val prepareLib = tasks.register<Download>("prepareLib") {
    src("https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar")
    dest(tla2tools)
    overwrite(false)
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("org.intellij.intelliLang")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }

    implementation(files(tla2tools).builtBy(prepareLib))
    // We need to use same version of lsp4j as tla2tools to avoid incompatibility
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.21.1")

    val lombokVersion = "1.18.48"
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild")
            untilBuild = provider { null }
        }
    }
    publishing {
        token = providers.gradleProperty("intellijPublishToken")
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val generatedRoot = layout.buildDirectory.dir("generated/sources/grammar")

data class LexerSpec(val component: String, val flex: String, val className: String);
val lexers = arrayOf(
    LexerSpec("tlaplus", "tlaplus.flex", "_TLAplusLexer"),
    LexerSpec("tlaplus", "tlaplus_modulebegin.flex", "_TLAplusModuleBeginLexer"),
    LexerSpec("tlaplus", "tlaplus_plus_cal_comment.flex", "_TLAplusPlusCalCommentLexer"),
    LexerSpec("tlc", "tlc_error_trace.flex", "_TLCErrorTraceLexer"),
    LexerSpec("tlc", "tlc_config.flex", "_TLCConfigLexer"),
    LexerSpec("pluscal", "plus_cal_algorithmbegin.flex", "_PlusCalAlgorithmBeginLexer"),
)

data class ParserSpec(val component: String, val bnf: String, val className: String);
val parsers = arrayOf(
    ParserSpec("tlaplus", "tlaplus.bnf", "TLAplusParser"),
    ParserSpec("tlc", "tlc_config.bnf", "TLCConfigParser")
)

val lexerTasks = lexers.map { spec ->
    tasks.register<GenerateLexerTask>("generate" + spec.className) {
        sourceFile.set(layout.projectDirectory.file("src/main/grammar/${spec.flex}"))
        targetRootOutputDir.set(generatedRoot)
        pathToClass.set("com/mayreh/intellij/plugin/${spec.component}/lexer/${spec.className}.java")
    }
}

val parserTasks = parsers.map { spec ->
    tasks.register<GenerateParserTask>("generate" + spec.className) {
        sourceFile.set(layout.projectDirectory.file("src/main/grammar/${spec.bnf}"))
        targetRootOutputDir.set(generatedRoot)
        pathToParser.set("com/mayreh/intellij/plugin/${spec.component}/parser/${spec.className}.java")
        pathToPsiRoot.set("com/mayreh/intellij/plugin/${spec.component}/psi")
    }
}

sourceSets {
    main {
        java.srcDir(files(generatedRoot)
                .builtBy(lexerTasks, parserTasks))
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events = setOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
            TestLogEvent.STANDARD_OUT)
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }
}
