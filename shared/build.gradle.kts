import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val generateQTalkBuildInfo by tasks.registering {
    val displayVersion = providers.gradleProperty("qtalkVersion")
    val generatedDirectory = layout.buildDirectory.dir("generated/qtalkBuildInfo/commonMain")
    inputs.property("qtalkVersion", displayVersion)
    outputs.dir(generatedDirectory)
    doLast {
        val source = generatedDirectory.get().file("com/qvoste/qtalk/QTalkBuildInfo.kt").asFile
        source.parentFile.mkdirs()
        source.writeText("""
            package com.qvoste.qtalk

            object QTalkBuildInfo {
                const val VERSION = "${displayVersion.get()}"
            }
        """.trimIndent() + "\n")
    }
}

kotlin {
    jvm()

    android {
        namespace = "com.qvoste.qtalk.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // Для релиза можно выбрать зависимости другой ОС через -Pqtalk.targetOs.
    val targetOs = providers.gradleProperty("qtalk.targetOs")
        .orElse(System.getProperty("os.name"))
        .get()
        .lowercase()

    // Linphone содержит отдельные нативные библиотеки для каждой ОС.
    val linphoneClassifier = when {
        targetOs.contains("linux") -> "ubuntu.24.04"
        targetOs.contains("windows") -> "windows"

        else -> error(
            "Unsupported desktop OS for Linphone SDK: $targetOs"
        )
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateQTalkBuildInfo) }
        jvmMain.dependencies {
            implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
            implementation(
                "org.linphone:linphone-sdk:5.5.23:$linphoneClassifier"
            )
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            runtimeOnly(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.0")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Native SDK tests must not share extracted DLLs with an installed, running QTalk.
tasks.withType<Test>().configureEach {
    val nativeTemp = layout.buildDirectory.dir("native-test-tmp").get().asFile
    systemProperty("java.io.tmpdir", nativeTemp.absolutePath)
    systemProperty("qtalk.version", providers.gradleProperty("qtalkVersion").get())
    doFirst { nativeTemp.mkdirs() }
}
