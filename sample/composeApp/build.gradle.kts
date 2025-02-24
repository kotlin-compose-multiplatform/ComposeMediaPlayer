@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.util.Locale

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
}

val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
val osArch = System.getProperty("os.arch").lowercase(Locale.getDefault())

val fxClassifier = when {
    osName.contains("linux") && osArch.contains("aarch64") -> "linux-aarch64"
    osName.contains("linux") -> "linux"
    osName.contains("windows") -> "win"
    osName.contains("mac") && osArch.contains("aarch64") -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    else -> throw IllegalStateException("Unsupported OS: $osName")
}

val javafxVersion = "22.0.1"


kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm()
    wasmJs {
        moduleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(project(":mediaplayer"))
            implementation(compose.materialIconsExtended)
            implementation(libs.filekit.dialog.compose)
            implementation(libs.platformtools.darkmodedetector)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activityCompose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation("org.openjfx:javafx-base:${javafxVersion}:${fxClassifier}")
            implementation("org.openjfx:javafx-graphics:${javafxVersion}:${fxClassifier}")
            implementation("org.openjfx:javafx-swing:${javafxVersion}:${fxClassifier}")
            implementation("org.openjfx:javafx-media:${javafxVersion}:${fxClassifier}")
        }
    }
}

android {
    namespace = "sample.app"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        targetSdk = 35

        applicationId = "sample.app.androidApp"
        versionCode = 1
        versionName = "1.0.0"
    }
}

compose.desktop {
    application {
        mainClass = "sample.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "sample"
            packageVersion = "1.0.0"
            linux {
                modules("jdk.security.auth")
            }
            macOS {
                jvmArgs(
                    "-Dapple.awt.application.appearance=system"
                )
            }
        }
    }
}
