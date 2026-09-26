import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    application
    kotlin("jvm")
}

group = "org.example"
version = "1.0.0"

kotlin {
    jvmToolchain(26)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

repositories {
    mavenCentral()
}

application {
    applicationName = "G134Office"
    mainClass.set("org.example.Launcher")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Xmx1024m",
        "--enable-native-access=ALL-UNNAMED"
    )
    applicationDistribution.from("README.md")
}

dependencies {
    implementation(kotlin("stdlib"))
    listOf("base", "graphics", "controls", "swing", "media", "web").forEach { module ->
        implementation("org.openjfx:javafx-$module:26.0.2:win")
    }

    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.apache.poi:poi-scratchpad:5.5.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    val lt = "6.8"
    implementation("org.languagetool:languagetool-core:$lt")
    implementation("org.languagetool:language-en:$lt")
    implementation("org.languagetool:language-ru:$lt")
    implementation("org.languagetool:language-uk:$lt")
    implementation("org.languagetool:language-be:$lt")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}


tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.example.Launcher",
            "Implementation-Title" to "G134Office",
            "Implementation-Version" to version
        )
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(Regex("set CLASSPATH=.*\\r?\\n")) {
                "set CLASSPATH=%APP_HOME%\\lib\\*\r\n"
            }
        )
        unixScript.writeText(
            unixScript.readText().replace(Regex("CLASSPATH=.*\\n")) {
                "CLASSPATH=${'$'}APP_HOME/lib/*\n"
            }
        )
    }
}

tasks.named<Tar>("distTar") {
    enabled = false
}

val packageApp = tasks.register("packageApp") {
    group = "distribution"
    description = "Собирает папку G134Office с встроенной JVM (jpackage app-image)"
    dependsOn(tasks.named("installDist"))
    val launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
    val libDir = layout.buildDirectory.dir("install/G134Office/lib")
    val destDir = layout.buildDirectory.dir("package")
    val jarName = tasks.named<Jar>("jar").flatMap { it.archiveFileName }
    val appVersion = version.toString().substringBefore("-")
    val iconFile = layout.projectDirectory.file("g134.ico").asFile
    inputs.dir(libDir)
    inputs.file(iconFile)
    outputs.dir(destDir)
    doLast {
        require(iconFile.exists()) {
            "Нет g134.ico в корне проекта: ${iconFile.absolutePath}"
        }
        val dest = destDir.get().asFile
        if (dest.exists() && !dest.deleteRecursively()) {
            throw GradleException("Не удалось заменить сборку в $dest. Закройте G134Office и повторите сборку.")
        }
        dest.mkdirs()
        val jpackageName = if (System.getProperty("os.name").lowercase().contains("win")) {
            "jpackage.exe"
        } else {
            "jpackage"
        }
        val jpackage = launcher.get().executablePath.asFile.resolveSibling(jpackageName)
        require(jpackage.exists()) {
            "jpackage не найден рядом с JDK: ${jpackage.parent}. Нужен JDK 26."
        }
        val command = listOf(
            jpackage.absolutePath,
            "--type", "app-image",
            "--name", "G134Office",
            "--app-version", appVersion,
            "--vendor", "G134Office",
            "--description", "G134Office - document and PDF editor",
            "--icon", iconFile.absolutePath,
            "--dest", dest.absolutePath,
            "--input", libDir.get().asFile.absolutePath,
            "--main-jar", jarName.get(),
            "--main-class", "org.example.Launcher",
            "--java-options", "-Dfile.encoding=UTF-8",
            "--java-options", "-Dstdout.encoding=UTF-8",
            "--java-options", "-Dstderr.encoding=UTF-8",
            "--java-options", "-Xmx1024m",
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        )
        val result = ProcessBuilder(command).inheritIO().start().waitFor()
        if (result != 0) {
            throw GradleException("jpackage завершился с кодом $result")
        }
        // JavaFX on Windows first looks in ../bin relative to its JAR.
        // Ship DLLs as individual files so the release publisher can Authenticode-sign
        // them before building the installer. Moving files alone does not establish trust.
        val nativeDir = dest.resolve("G134Office/bin")
        copy {
            from(libDir.get().asFile.listFiles().orEmpty()
                .filter { it.name.startsWith("javafx-") && it.name.endsWith("-win.jar") }
                .map { zipTree(it) })
            include("*.dll")
            into(nativeDir)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
        require(nativeDir.resolve("glass.dll").isFile) { "JavaFX glass.dll missing from package" }
    }
}

tasks.register("release") {
    group = "distribution"
    description = "Тесты, ZIP без JVM и папка приложения со встроенной JVM"
    dependsOn(tasks.named("check"), tasks.named("distZip"), packageApp)
}

tasks.named("assemble") {
    dependsOn(tasks.named("distZip"))
}
