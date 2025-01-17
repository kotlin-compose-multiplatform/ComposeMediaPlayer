@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.google.osdetector)

}

group = "io.github.kdroidfilter.composemediaplayer"
version = "0.0.1"

val osClassifier = project.extensions.getByType<com.google.gradle.osdetector.OsDetector>().classifier

kotlin {
    jvmToolchain(17)

    androidTarget { publishLibraryVariants("release") }
    jvm()
    wasmJs { browser() }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.androidcontextprovider)
            implementation(libs.kotlinx.coroutines.android)

        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.gst1.java.core)
            implementation(libs.gst1.java.swing)
        }

        iosMain.dependencies {
        }

    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }

}

android {
    namespace = "io.github.kdroidfilter.composemediaplayer"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }
}
