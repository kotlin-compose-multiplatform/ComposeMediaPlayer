import java.util.*

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.google.osdetector") version "1.7.3"

}

java {
    modularity.inferModulePath.set(false)
}

group = "io.github.kdroidfilter.composemediaplayer.javafx-mediaplayer"
version = "0.0.1"

// Determine FX classifier based on OS
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

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.desktop.currentOs)
                implementation("org.openjfx:javafx-base:${javafx.version}:${fxClassifier}")
                implementation("org.openjfx:javafx-graphics:${javafx.version}:${fxClassifier}")
                implementation("org.openjfx:javafx-swing:${javafx.version}:${fxClassifier}")
                implementation("org.openjfx:javafx-media:${javafx.version}:${fxClassifier}")
            }
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

javafx {
    version = "21"

}

