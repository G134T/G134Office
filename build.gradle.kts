import org.gradle.api.tasks.Sync
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    java
    application
    kotlin("jvm")
}

group = "org.example"
version = "1.0.1"

val javafxVersion = "26.0.2"
val javafxSdkUrl = "https://download2.gluonhq.com/openjfx/$javafxVersion/openjfx-${javafxVersion}_windows-x64_bin-sdk.zip"
val javafxJmodsUrl = "https://download2.gluonhq.com/openjfx/$javafxVersion/openjfx-${javafxVersion}_windows-x64_bin-jmods.zip"
val javafxSdkArchiveName = "openjfx-${javafxVersion}_windows-x64_bin-sdk.zip"
val javafxJmodsArchiveName = "openjfx-${javafxVersion}_windows-x64_bin-jmods.zip"
val javafxSdkSha256 = "02CC5FCE8925BDD0EC58AAF88D7C2B0042D1E914A48B5E1ABB6AE0D0364E326A"
val javafxJmodsSha256 = "8554A293273EAC20D172C18455FCF154A54B7879D3F00DE28344EBEFD8978672"
val javafxModules = listOf("base", "graphics", "controls", "swing", "media", "web")

val javafxRoot = layout.buildDirectory.dir("javafx")
val javafxSdkDir = layout.buildDirectory.dir("javafx/sdk/javafx-sdk-$javafxVersion")
val javafxJmodsDir = layout.buildDirectory.dir("javafx/jmods/javafx-jmods-$javafxVersion")
val javafxRuntimeDir = layout.buildDirectory.dir("javafx/runtime")
val applicationInputDir = layout.buildDirectory.dir("javafx/app-input")
val javafxSdkArchive = layout.buildDirectory.file("javafx/downloads/$javafxSdkArchiveName")
val javafxJmodsArchive = layout.buildDirectory.file("javafx/downloads/$javafxJmodsArchiveName")
val installLibDir = layout.buildDirectory.dir("install/G134Office/lib")

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        var count: Int
        while (input.read(buffer).also { count = it } >= 0) {
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    digest.digest().joinToString("") { byte -> "%02X".format(byte) }
}

fun downloadVerified(url: String, destination: File, expectedSha256: String) {
    destination.parentFile.mkdirs()
    if (!destination.isFile) {
        val temporary = File(destination.parentFile, "${destination.name}.part")
        try {
            URI(url).toURL().openStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }
    val actual = sha256(destination)
    require(actual.equals(expectedSha256, ignoreCase = true)) {
        "Неверный SHA-256 для ${destination.name}: $actual, ожидался $expectedSha256"
    }
}

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
    applicationDistribution.from("LICENSE")
    applicationDistribution.from("NOTICE")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(files(javafxModules.map { module ->
        javafxSdkDir.map { directory -> directory.file("lib/javafx.$module.jar") }
    }))

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

val prepareJavafxArchives = tasks.register("prepareJavafxArchives") {
    group = "distribution"
    description = "Скачивает и проверяет официальный JavaFX SDK и JMODs"
    outputs.files(javafxSdkArchive, javafxJmodsArchive)
    outputs.upToDateWhen { false }
    doLast {
        require(System.getProperty("os.name").contains("win", ignoreCase = true)) {
            "Эта сборка использует официальный JavaFX Windows SDK/JMODs"
        }
        downloadVerified(javafxSdkUrl, javafxSdkArchive.get().asFile, javafxSdkSha256)
        downloadVerified(javafxJmodsUrl, javafxJmodsArchive.get().asFile, javafxJmodsSha256)
    }
}

val prepareJavafxSdk = tasks.register<Sync>("prepareJavafxSdk") {
    group = "distribution"
    description = "Распаковывает JavaFX SDK для компиляции"
    dependsOn(prepareJavafxArchives)
    from({ zipTree(javafxSdkArchive.get().asFile) })
    into(javafxRoot.map { it.dir("sdk") })
    includeEmptyDirs = false
}

val prepareJavafxJmods = tasks.register<Sync>("prepareJavafxJmods") {
    group = "distribution"
    description = "Распаковывает JavaFX JMODs для jlink"
    dependsOn(prepareJavafxArchives)
    from({ zipTree(javafxJmodsArchive.get().asFile) })
    into(javafxRoot.map { it.dir("jmods") })
    includeEmptyDirs = false
}

tasks.named("compileKotlin") {
    dependsOn(prepareJavafxSdk)
}

tasks.named("compileJava") {
    dependsOn(prepareJavafxSdk)
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

val jlinkRuntime = tasks.register("jlinkRuntime") {
    group = "distribution"
    description = "Собирает контролируемый JavaFX runtime через jlink"
    dependsOn(prepareJavafxJmods)
    inputs.dir(javafxJmodsDir)
    outputs.dir(javafxRuntimeDir)
    doLast {
        val launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(26))
        }.get()
        val jlinkName = if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            "jlink.exe"
        } else {
            "jlink"
        }
        val jlink = launcher.executablePath.asFile.resolveSibling(jlinkName)
        val jdkJmods = launcher.metadata.installationPath.asFile.resolve("jmods")
        val javafxJmods = javafxJmodsDir.get().asFile
        val runtime = javafxRuntimeDir.get().asFile
        require(jlink.isFile) { "jlink не найден рядом с JDK: ${jlink.parent}. Нужен JDK 26." }
        require(jdkJmods.isDirectory) { "JDK JMODs не найдены: ${jdkJmods.absolutePath}" }
        require(javafxJmods.resolve("javafx.controls.jmod").isFile) {
            "JavaFX JMODs не распакованы: ${javafxJmods.absolutePath}"
        }
        if (runtime.exists() && !runtime.deleteRecursively()) {
            throw GradleException("Не удалось заменить runtime в $runtime")
        }
        runtime.parentFile.mkdirs()
        val command = listOf(
            jlink.absolutePath,
            "--module-path", "${javafxJmods.absolutePath}${File.pathSeparator}${jdkJmods.absolutePath}",
            "--add-modules", "java.se,jdk.crypto.ec,jdk.localedata,jdk.unsupported,javafx.controls,javafx.media,javafx.swing,javafx.web",
            "--bind-services",
            "--strip-debug",
            "--compress=2",
            "--no-header-files",
            "--no-man-pages",
            "--output", runtime.absolutePath
        )
        val result = ProcessBuilder(command).inheritIO().start().waitFor()
        if (result != 0) {
            throw GradleException("jlink завершился с кодом $result")
        }
        require(runtime.resolve("lib/modules").isFile) {
            "jlink не создал модульный runtime: ${runtime.absolutePath}"
        }
    }
}

val prepareApplicationInput = tasks.register<Sync>("prepareApplicationInput") {
    group = "distribution"
    description = "Готовит classpath приложения без JavaFX SDK JARs"
    dependsOn(tasks.named("installDist"))
    from(installLibDir) {
        exclude("javafx*.jar")
    }
    into(applicationInputDir)
}

val packageApp = tasks.register("packageApp") {
    group = "distribution"
    description = "Собирает папку G134Office с jlink-runtime (jpackage app-image)"
    dependsOn(prepareApplicationInput, jlinkRuntime)
    val launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
    val destDir = layout.buildDirectory.dir("package")
    val jarName = tasks.named<Jar>("jar").flatMap { it.archiveFileName }
    val appVersion = version.toString().substringBefore("-")
    val iconFile = layout.projectDirectory.file("g134.ico").asFile
    inputs.dir(applicationInputDir)
    inputs.dir(javafxRuntimeDir)
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
            "--input", applicationInputDir.get().asFile.absolutePath,
            "--main-jar", jarName.get(),
            "--main-class", "org.example.Launcher",
            "--runtime-image", javafxRuntimeDir.get().asFile.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
            "--java-options", "-Dstdout.encoding=UTF-8",
            "--java-options", "-Dstderr.encoding=UTF-8",
            "--java-options", "-Xmx1024m",
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--java-options", "--add-modules=javafx.controls,javafx.media,javafx.swing,javafx.web"
        )
        val result = ProcessBuilder(command).inheritIO().start().waitFor()
        if (result != 0) {
            throw GradleException("jpackage завершился с кодом $result")
        }
        require(dest.resolve("G134Office/runtime/lib/modules").isFile) {
            "jpackage не включил jlink-runtime в app-image"
        }
        // jlink ships the JDK's DLL bytes without the vendor Authenticode overlay.
        // Smart App Control then blocks runtime\bin\net.dll and the same copies.
        // Put the signed JDK original back only when the code image is identical.
        val runtimeBin = dest.resolve("G134Office/runtime/bin")
        val restored = restoreVendorSignatures(runtimeBin, jpackage.parentFile)
        val vendorNet = jpackage.parentFile.resolve("net.dll")
        if (vendorNet.isFile) {
            require(restored.contains("net.dll") && runtimeBin.resolve("net.dll").length() == vendorNet.length()) {
                "runtime/bin/net.dll остался без подписи поставщика JDK"
            }
        }
        logger.lifecycle("Возвращены подписи поставщика JDK: ${restored.size}")
    }
}

/**
 * jlink writes the unsigned image. The JDK bin copy is that image plus an
 * Authenticode overlay: checksum and certificate-table fields differ, the
 * signature sits past the original end of the file. Returns restored names.
 */
fun restoreVendorSignatures(runtimeBin: File, jdkBin: File): List<String> {
    if (!runtimeBin.isDirectory || !jdkBin.isDirectory) return emptyList()
    val restored = mutableListOf<String>()
    runtimeBin.listFiles { file -> file.isFile && file.extension.equals("dll", true) }?.forEach { shipped ->
        val vendor = File(jdkBin, shipped.name)
        if (!vendor.isFile || !sameImagePlusSignature(shipped, vendor)) return@forEach
        vendor.copyTo(shipped, overwrite = true)
        restored += shipped.name
    }
    return restored
}

private fun sameImagePlusSignature(unsignedFile: File, signedFile: File): Boolean {
    val unsigned = unsignedFile.readBytes()
    val signed = signedFile.readBytes()
    val overlay = authenticodeOverlayOffset(signed) ?: return false
    if (overlay != unsigned.size) return false
    val pe = readU32(signed, 0x3C).toInt()
    val optional = pe + 24
    val directories = if (readU16(signed, optional) == 0x20b) optional + 112 else optional + 96
    val ignored = listOf(optional + 64 until optional + 68, directories + 32 until directories + 40)
    for (index in 0 until overlay) {
        if (ignored.any { index in it }) continue
        if (unsigned[index] != signed[index]) return false
    }
    return true
}

private fun authenticodeOverlayOffset(bytes: ByteArray): Int? {
    if (bytes.size < 0x40 || bytes[0] != 0x4D.toByte() || bytes[1] != 0x5A.toByte()) return null
    val pe = readU32(bytes, 0x3C).toInt()
    if (pe <= 0 || pe + 24 >= bytes.size || readU32(bytes, pe) != 0x4550L) return null
    val optional = pe + 24
    val directories = when (readU16(bytes, optional)) {
        0x10b -> optional + 96
        0x20b -> optional + 112
        else -> return null
    }
    val entry = directories + 32
    if (entry + 8 > bytes.size) return null
    val offset = readU32(bytes, entry).toInt()
    val size = readU32(bytes, entry + 4).toInt()
    if (offset <= 0 || size <= 0) return null
    if (offset > bytes.size || offset.toLong() + size > bytes.size) return null
    return offset
}

private fun readU16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun readU32(bytes: ByteArray, offset: Int): Long =
    (bytes[offset].toLong() and 0xff) or
        ((bytes[offset + 1].toLong() and 0xff) shl 8) or
        ((bytes[offset + 2].toLong() and 0xff) shl 16) or
        ((bytes[offset + 3].toLong() and 0xff) shl 24)

tasks.register("release") {
    group = "distribution"
    description = "Тесты, ZIP без JVM и папка приложения с jlink-runtime"
    dependsOn(tasks.named("check"), tasks.named("distZip"), packageApp)
}

tasks.named("assemble") {
    dependsOn(tasks.named("distZip"))
}
