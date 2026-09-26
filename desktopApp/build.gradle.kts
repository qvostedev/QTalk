import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile

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

        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.qvoste.qtalk"
            packageVersion = "1.0.0"
            windows {
                iconFile.set(project.file("src/main/resources/logo_black.ico"))
            }
        }
    }
}

// Проверяет JNI-классы и DLL до упаковки Windows-установщика.
tasks.register("verifyWindowsUberJar") {
    notCompatibleWithConfigurationCache("Проверка читает готовый ZIP напрямую")
    dependsOn("packageUberJarForCurrentOS")
    doLast {
        check(targetOs.contains("windows")) { "Запустите задачу с -Pqtalk.targetOs=windows" }
        val jar = layout.buildDirectory.dir("compose/jars").get().asFile
            .listFiles().orEmpty()
            .filter { it.extension == "jar" && !it.name.contains("release") }
            .maxByOrNull { it.lastModified() }
            ?: error("Windows uber JAR не найден")
        val requiredEntries = listOf(
            "org/linphone/core/tools/java/JavaPlatformHelper.class",
            "org/linphone/core/tools/java/LibraryLoader.class",
            "liblinphone.dll",
            "skiko-windows-x64.dll"
        )
        ZipFile(jar).use { zip ->
            requiredEntries.forEach { entry ->
                check(zip.getEntry(entry) != null) { "В Windows JAR отсутствует $entry" }
            }
        }
        println("QTalk: Windows uber JAR содержит обязательные JNI-компоненты")
    }
}
