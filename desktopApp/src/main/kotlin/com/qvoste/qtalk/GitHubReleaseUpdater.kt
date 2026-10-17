package com.qvoste.qtalk

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val latestReleaseUrl = "https://api.github.com/repos/qvostedev/QTalk/releases/latest"
private const val maximumInstallerSize = 500L * 1024 * 1024

internal data class ReleaseAsset(val name: String, val url: URI, val size: Long, val digest: String?)
internal data class QTalkRelease(
    val version: String,
    val assets: List<ReleaseAsset>,
    val description: String = ""
)

internal fun parseQTalkRelease(json: String): QTalkRelease {
    val root = Json.parseToJsonElement(json).jsonObject
    val version = root.getValue("tag_name").jsonPrimitive.content.removePrefix("v")
    require(parseVersion(version) != null) { "Некорректная версия релиза" }
    val description = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    val assets = root.getValue("assets").jsonArray.map { element ->
        val asset = element.jsonObject
        ReleaseAsset(
            name = asset.getValue("name").jsonPrimitive.content,
            url = URI(asset.getValue("browser_download_url").jsonPrimitive.content),
            size = asset.getValue("size").jsonPrimitive.content.toLong(),
            digest = asset["digest"]?.jsonPrimitive?.content?.takeUnless { it == "null" }
        )
    }
    return QTalkRelease(version, assets, description)
}

internal fun isNewerVersion(candidate: String, installed: String): Boolean {
    val candidateParts = parseVersion(candidate) ?: return false
    val installedParts = parseVersion(installed) ?: return false
    return candidateParts.zip(installedParts).firstOrNull { (a, b) -> a != b }
        ?.let { (a, b) -> a > b } ?: false
}

private fun parseVersion(value: String): List<Int>? {
    val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value) ?: return null
    return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}

internal fun selectQTalkAsset(release: QTalkRelease, windows: Boolean): ReleaseAsset? =
    release.assets.firstOrNull {
        if (windows) it.name == "QTalk-Setup-${release.version}.exe"
        else it.name.startsWith("QTalk_") && it.name.contains(release.version) && it.name.endsWith(".deb")
    }

internal fun windowsUpdateScript(
    installer: Path,
    applicationPid: Long,
    applicationExecutable: Path,
    logFile: Path
): String {
    fun Path.powerShellLiteral(): String =
        "'${toAbsolutePath().toString().replace("'", "''")}'"

    return """
        ${'$'}ErrorActionPreference = 'Stop'
        ${'$'}installerPath = ${installer.powerShellLiteral()}
        ${'$'}applicationPath = ${applicationExecutable.powerShellLiteral()}
        ${'$'}logPath = ${logFile.powerShellLiteral()}

        try {
            Wait-Process -Id $applicationPid -Timeout 120 -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 500
            ${'$'}arguments = @(
                '/VERYSILENT',
                '/SUPPRESSMSGBOXES',
                '/NORESTART',
                '/SP-',
                '/FORCECLOSEAPPLICATIONS',
                ('/LOG="' + ${'$'}logPath + '"')
            )
            ${'$'}setup = Start-Process -FilePath ${'$'}installerPath -ArgumentList ${'$'}arguments -Wait -PassThru
            if (${'$'}setup.ExitCode -ne 0) {
                throw "QTalk installer exited with code ${'$'}(${'$'}setup.ExitCode)"
            }
            if (-not (Test-Path -LiteralPath ${'$'}applicationPath -PathType Leaf)) {
                throw "Updated QTalk executable was not found: ${'$'}applicationPath"
            }
            Start-Process -FilePath ${'$'}applicationPath
            Remove-Item -LiteralPath ${'$'}installerPath -Force -ErrorAction SilentlyContinue
        } catch {
            Add-Content -LiteralPath ${'$'}logPath -Value ("`r`nQTalk updater error: " + ${'$'}_.Exception.Message)
            if (Test-Path -LiteralPath ${'$'}applicationPath -PathType Leaf) {
                Start-Process -FilePath ${'$'}applicationPath
            }
        }
    """.trimIndent()
}

internal class GitHubReleaseUpdater(private val installedVersion: String) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    suspend fun availableVersion(): String? {
        return availableRelease()?.version
    }

    suspend fun availableRelease(): QTalkRelease? {
        val release = fetchLatestRelease() ?: return null
        if (!isNewerVersion(release.version, installedVersion)) return null
        val windows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
        val linux = System.getProperty("os.name").contains("Linux", ignoreCase = true)
        if (!windows && !linux) return null
        return release.takeIf { selectQTalkAsset(it, windows) != null }
    }

    suspend fun checkAndInstall(onStatus: (String, Float?) -> Unit): Boolean {
        onStatus("Проверка обновлений…", null)
        val release = fetchLatestRelease()
        if (release == null) {
            onStatus("У вас установлена последняя версия", null)
            return false
        }
        if (!isNewerVersion(release.version, installedVersion)) {
            onStatus("У вас установлена последняя версия", null)
            return false
        }
        val windows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
        val linux = System.getProperty("os.name").contains("Linux", ignoreCase = true)
        check(windows || linux) { "Автообновление для этой системы пока недоступно" }
        val asset = selectQTalkAsset(release, windows)
            ?: error("Для версии ${release.version} не опубликован установщик для вашей системы")

        val expectedDigest = asset.digest?.removePrefix("sha256:")
        check(expectedDigest != null && expectedDigest.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Установщик релиза не содержит контрольную сумму SHA-256"
        }
        check(asset.size in 1..maximumInstallerSize) { "Некорректный размер установщика" }
        check(asset.url.scheme == "https" && asset.url.host == "github.com" &&
            asset.url.path.startsWith("/qvostedev/QTalk/releases/download/")) {
            "Некорректный адрес установщика"
        }

        onStatus("Загрузка версии ${release.version}…", 0f)
        val installer = withContext(Dispatchers.IO) {
            downloadAndVerify(asset, expectedDigest, if (windows) ".exe" else ".deb") { downloaded, total ->
                val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
                val downloadedMb = downloaded / (1024.0 * 1024.0)
                val totalMb = total / (1024.0 * 1024.0)
                val detail = if (total > 0) {
                    "Загрузка версии ${release.version}: %.1f из %.1f МБ".format(downloadedMb, totalMb)
                } else {
                    "Загрузка версии ${release.version}: %.1f МБ".format(downloadedMb)
                }
                onStatus(detail, progress)
            }
        }
        onStatus("Проверка загруженного установщика…", 1f)
        onStatus("Установка версии ${release.version}… QTalk перезапустится автоматически", null)
        if (windows) {
            withContext(Dispatchers.IO) { launchWindowsInstaller(installer) }
            return true
        }
        withContext(Dispatchers.IO) { installLinuxPackage(installer) }
        onStatus("Обновление установлено. Перезапустите QTalk.", null)
        return false
    }

    private suspend fun fetchLatestRelease(): QTalkRelease? {
        val response = withContext(Dispatchers.IO) {
            http.send(
                HttpRequest.newBuilder(URI(latestReleaseUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "QTalk-Updater")
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
        }
        if (response.statusCode() == 404) return null
        check(response.statusCode() == 200) { "GitHub вернул код ${response.statusCode()}" }
        return parseQTalkRelease(response.body())
    }

    private fun downloadAndVerify(
        asset: ReleaseAsset,
        expectedDigest: String,
        extension: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Path {
        val file = Files.createTempFile("qtalk-update-", extension)
        try {
            val response = http.send(
                HttpRequest.newBuilder(asset.url).timeout(Duration.ofMinutes(5))
                    .header("User-Agent", "QTalk-Updater")
                    .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream()
            )
            check(response.statusCode() == 200) { "Не удалось скачать установщик: ${response.statusCode()}" }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            response.body().use { input ->
                Files.newOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, asset.size)
                    }
                }
            }
            check(downloaded == asset.size) { "Размер загруженного установщика не совпадает" }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualDigest.equals(expectedDigest, ignoreCase = true)) {
                "Контрольная сумма установщика не совпадает"
            }
            return file
        } catch (error: Exception) {
            Files.deleteIfExists(file)
            throw error
        }
    }

    private fun launchWindowsInstaller(file: Path) {
        val applicationPid = ProcessHandle.current().pid()
        val applicationExecutable = ProcessHandle.current().info().command()
            .map(Path::of)
            .orElseThrow { IllegalStateException("Не удалось определить путь к QTalk") }
        val helper = Files.createTempFile("qtalk-update-helper-", ".ps1")
        val logFile = Path.of(System.getProperty("java.io.tmpdir"), "QTalk-update.log")
        Files.writeString(
            helper,
            windowsUpdateScript(file, applicationPid, applicationExecutable, logFile)
        )
        val escapedHelper = helper.toAbsolutePath().toString().replace("'", "''")
        val command = "Start-Process -FilePath 'powershell.exe' " +
            "-ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File','$escapedHelper') " +
            "-Verb RunAs -WindowStyle Hidden"
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Не удалось запустить обновление: $output" }
    }

    private fun installLinuxPackage(file: Path) {
        val process = ProcessBuilder("pkexec", "apt", "install", "-y", file.toAbsolutePath().toString())
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Не удалось установить обновление: $output" }
    }
}
