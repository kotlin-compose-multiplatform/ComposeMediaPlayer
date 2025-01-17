@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.internal.os.OperatingSystem.current
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

plugins {
    alias(libs.plugins.multiplatform)
}

group = "io.github.kdroidfilter.composemediaplayer.gstreamer-windows"
version = "0.0.1"


val gstreamerVersion = "1.24.11"
val gstreamerInstallerName = "gstreamer-1.0-msvc-x86_64-$gstreamerVersion.msi"
val gstreamerBaseUrl = "https://gstreamer.freedesktop.org/data/pkg/windows/$gstreamerVersion/msvc/"

val gstreamerDownloadUrl = "$gstreamerBaseUrl$gstreamerInstallerName"
val gstreamerChecksumUrl = "$gstreamerBaseUrl$gstreamerInstallerName.sha256sum"


kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        commonMain.dependencies {
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }
}

abstract class DownloadFileTask : DefaultTask() {
    @get:Input
    abstract val sourceUrl: Property<String>

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun download() {
        val destFile = destination.get().asFile
        destFile.parentFile.mkdirs()

        URL(sourceUrl.get()).openStream().use { input ->
            Files.copy(input, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        logger.lifecycle("Downloaded ${sourceUrl.get()} to ${destFile.absolutePath}")
    }
}

tasks.register<DownloadFileTask>("downloadGStreamerInstaller") {
    sourceUrl.set(gstreamerDownloadUrl)
    destination.set(layout.buildDirectory.file("downloads/gstreamer-windows/$gstreamerInstallerName"))
    description = "Downloads the GStreamer MSI installer if not present in cache."
}

tasks.register<DownloadFileTask>("downloadGStreamerChecksum") {
    sourceUrl.set(gstreamerChecksumUrl)
    destination.set(layout.buildDirectory.file("downloads/gstreamer-windows/$gstreamerInstallerName.sha256sum"))
    description = "Downloads the SHA256 checksum file for GStreamer MSI."
}

abstract class VerifyChecksumTask : DefaultTask() {
    @get:InputFile
    abstract val fileToVerify: RegularFileProperty

    @get:InputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val file = fileToVerify.get().asFile
        val checksum = checksumFile.get().asFile

        if (!checksum.exists()) {
            throw GradleException("Checksum file not found: ${checksum.absolutePath}")
        }

        val expectedChecksum = checksum.readText().trim().split("\\s+".toRegex())[0]
        val actualChecksum = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

        if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
            logger.lifecycle("Checksum mismatch! Re-downloading file...")
            // Re-download the file
            URL(project.property("gstreamerDownloadUrl").toString()).openStream().use { input ->
                Files.copy(input, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            // Verify the new download
            val newChecksum = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }

            if (!newChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw GradleException("Checksum verification failed after re-download!\nExpected: $expectedChecksum\nActual  : $newChecksum")
            }
        }

        logger.lifecycle("Checksum verification successful")
    }
}

tasks.register<VerifyChecksumTask>("verifyGStreamerChecksum") {
    dependsOn("downloadGStreamerInstaller", "downloadGStreamerChecksum")

    fileToVerify.set(layout.buildDirectory.file("downloads/gstreamer-windows/$gstreamerInstallerName"))
    checksumFile.set(layout.buildDirectory.file("downloads/gstreamer-windows/$gstreamerInstallerName.sha256sum"))
}

// First, define the task class outside of any configuration block
abstract class GStreamerInstallTask : DefaultTask() {
    @get:InputFile
    abstract val installerFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun install() {
        val installer = installerFile.get().asFile
        val targetDir = outputDir.get().asFile

        // Ensure target directory exists
        targetDir.mkdirs()

        // Use ProcessBuilder instead of project.exec
        val process = ProcessBuilder(
            "msiexec",
            "/passive",
            "INSTALLDIR=${targetDir.absolutePath}",
            "/i",
            installer.absolutePath
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("GStreamer installation failed with exit code: $exitCode")
        }

        logger.lifecycle("GStreamer installed to ${targetDir.absolutePath}")
    }
}

// Then register the task with proper configuration
tasks.register<GStreamerInstallTask>("installGStreamerToResources") {
    description = "Installs GStreamer to the JVM resources directory"
    group = "gstreamer"

    installerFile.set(layout.buildDirectory.file("downloads/gstreamer-windows/$gstreamerInstallerName"))
    outputDir.set(layout.projectDirectory.dir("src/jvmMain/resources/gstreamer"))

    // Only run on Windows
    onlyIf {
        current().isWindows
    }

    // Add dependency on checksum verification
    dependsOn("verifyGStreamerChecksum")
}