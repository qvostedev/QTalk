import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Windows-зависимости можно собрать на Linux через -Pqtalk.targetOs=windows.
val targetOs = providers.gradleProperty("qtalk.targetOs")
    .orElse(System.getProperty("os.name"))
    .get()
    .lowercase()

dependencies {
    implementation(project(":shared"))

    implementation(
        if (targetOs.contains("windows")) compose.desktop.windows_x64
        else compose.desktop.currentOs
    )
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val prepareWindowsJpackageInput by tasks.registering(Copy::class) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("windows-jpackage-input"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

compose.desktop {
    application {
        mainClass = "com.qvoste.qtalk.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.qvoste.qtalk"
            packageVersion = "1.0.0"
        }
    }
}
