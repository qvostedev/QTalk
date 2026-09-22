import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
        jvmMain.dependencies {
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
