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

val qTalkPackageVersion = "1.0.3"

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation(
        if (targetOs.contains("windows")) compose.desktop.windows_x64
        else compose.desktop.currentOs
    )
    // Used only by the guarded Windows DWM title-bar integration.
    implementation("net.java.dev.jna:jna:5.17.0")

    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.compose.uiToolingPreview)
    testImplementation(libs.kotlin.testJunit)
}

tasks.jar {
    manifest.attributes["Implementation-Version"] = qTalkPackageVersion
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Deb)
            modules("java.net.http")
            packageName = "QTalk"
            packageVersion = qTalkPackageVersion
            description = "QTalk desktop SIP client"
            vendor = "Qvoste"
            windows {
                iconFile.set(project.file("src/main/resources/logo_black.ico"))
                menu = true
                menuGroup = "QTalk"
                shortcut = true
                dirChooser = true
                perUserInstall = false
                upgradeUuid = "a915f19b-b466-4db7-bc2f-5cc05ec43e75"
            }
        }
    }
}

val windowsInstallerDirectory = rootProject.layout.projectDirectory.dir("installer/windows")
val windowsInstallerArtwork = listOf(
    windowsInstallerDirectory.file("wizard-large.bmp").asFile,
    windowsInstallerDirectory.file("wizard-small.bmp").asFile
)

val prepareQTalkInstallerArtwork by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Generates the branded QTalk artwork used by the Windows installer"

    inputs.file(project.file("src/main/resources/logo_black.ico"))
    inputs.file(windowsInstallerDirectory.file("generate-installer-art.ps1"))
    outputs.files(windowsInstallerArtwork)

    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", windowsInstallerDirectory.file("generate-installer-art.ps1").asFile.absolutePath,
        "-IconPath", project.file("src/main/resources/logo_black.ico").absolutePath,
        "-OutputDirectory", windowsInstallerDirectory.asFile.absolutePath
    )
}

tasks.register<Exec>("packageQTalkInstaller") {
    group = "distribution"
    description = "Builds the branded QTalk Windows EXE installer"
    notCompatibleWithConfigurationCache("The task discovers the local Inno Setup compiler at execution time")
    dependsOn("createDistributable", prepareQTalkInstallerArtwork)

    val installerFile = layout.buildDirectory.file(
        "compose/binaries/main/exe/QTalk-Setup-$qTalkPackageVersion.exe"
    )
    val applicationImage = layout.buildDirectory.dir("compose/binaries/main/app/QTalk")
    inputs.file(windowsInstallerDirectory.file("QTalk.iss"))
    inputs.files(windowsInstallerArtwork)
    inputs.dir(applicationImage)
    outputs.file(installerFile)

    doFirst {
        check(targetOs.contains("windows")) {
            "The QTalk EXE installer can only be built on Windows"
        }

        val configuredCompiler = providers.environmentVariable("INNO_SETUP_COMPILER")
            .orNull
            ?.let(::file)
        val compiler = sequenceOf(
            configuredCompiler,
            file("C:/Program Files (x86)/Inno Setup 6/ISCC.exe"),
            file("C:/Program Files/Inno Setup 6/ISCC.exe"),
            file("${System.getenv("LOCALAPPDATA")}/Programs/Inno Setup 6/ISCC.exe")
        ).filterNotNull().firstOrNull(File::isFile)
            ?: error(
                "Inno Setup 6 is required. Install it once or set INNO_SETUP_COMPILER to ISCC.exe"
            )

        val appDirectory = applicationImage.get().asFile
        check(appDirectory.isDirectory) {
            "QTalk application image was not created at ${appDirectory.absolutePath}"
        }

        val outputDirectory = installerFile.get().asFile.parentFile.apply { mkdirs() }
        executable = compiler.absolutePath
        args(
            "/DSourceDir=${appDirectory.absolutePath}",
            "/DOutputDir=${outputDirectory.absolutePath}",
            "/DAppVersion=$qTalkPackageVersion",
            windowsInstallerDirectory.file("QTalk.iss").asFile.absolutePath
        )
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
