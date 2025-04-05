@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
}

group = "io.github.kdroidfilter.composemediaplayer"
version = "0.5.3"




kotlin {
    jvmToolchain(17)
    androidTarget { publishLibraryVariants("release") }
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            api(libs.filekit.core)
            implementation(libs.kotlinx)
            implementation(libs.kotlin.logging)
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            implementation(libs.androidcontextprovider)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.datasource)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.gst1.java.core)
            implementation(libs.gst1.java.swing)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.slf4j.simple)
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

