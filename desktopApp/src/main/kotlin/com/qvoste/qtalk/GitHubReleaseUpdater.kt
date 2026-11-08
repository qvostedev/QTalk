package com.qvoste.qtalk

import java.net.URI
import java.io.InputStream
import java.io.OutputStream
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

internal data class UpdatePlatform(
    val osName: String,
    val architecture: String,
    val installMethod: String?,
    val osRelease: String
)

internal data class LinuxUpdateTools(val pkexec: Path, val apt: Path)

internal sealed interface UpdatePlan {
    data object Windows : UpdatePlan
    data class Debian(val tools: LinuxUpdateTools) : UpdatePlan
    data class Manual(val status: String) : UpdatePlan
}

internal fun updatePlan(platform: UpdatePlatform, linuxTools: () -> LinuxUpdateTools?): UpdatePlan {
    if (platform.osName.contains("Windows", ignoreCase = true)) return UpdatePlan.Windows
    val release = platform.osRelease.lineSequence().mapNotNull { line ->
        val entry = line.trim()
        if (entry.startsWith('#') || '=' !in entry) return@mapNotNull null
        val key = entry.substringBefore('=').trim()
        val value = entry.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
        key to value.lowercase()
    }.toMap()
    if (platform.installMethod?.trim()?.equals("nix", ignoreCase = true) == true || release["ID"] == "nixos") {
        return UpdatePlan.Manual("QTalk установлен через Nix. Для установки в профиле выполните: nix profile upgrade qtalk. Если приложение управляется системой, обновите его через свою конфигурацию NixOS. Автоматическая установка не выполнялась.")
    }
    val family = listOfNotNull(release["ID"]) + release["ID_LIKE"].orEmpty().split(Regex("\\s+"))
    if (!platform.osName.contains("Linux", ignoreCase = true) || family.none { it == "debian" || it == "ubuntu" }) {
        return UpdatePlan.Manual("Автообновление доступно только для Debian/Ubuntu на amd64. Установите подходящую версию QTalk вручную для вашей системы.")
    }
    if (platform.architecture.lowercase() !in setOf("amd64", "x86_64")) {
        return UpdatePlan.Manual("Автообновление DEB доступно только для amd64. Для вашей архитектуры установите подходящую версию QTalk вручную.")
    }
    val tools = linuxTools() ?: return UpdatePlan.Manual(
        "Автообновление недоступно: нужны системные /usr/bin/apt и /usr/bin/pkexec с владельцем root, безопасными правами и setuid у pkexec. Установите обновление вручную через системный менеджер пакетов."
    )
    return UpdatePlan.Debian(tools)
}

private fun currentUpdatePlatform(): UpdatePlatform {
    val osName = System.getProperty("os.name", "")
    return UpdatePlatform(
        osName, System.getProperty("os.arch", ""), System.getenv("QTALK_INSTALL_METHOD"),
        if (osName.contains("Linux", ignoreCase = true)) {
            runCatching { Files.readString(Path.of("/etc/os-release")) }.getOrDefault("")
        } else ""
    )
}

internal fun isTrustedUpdateExecutable(uid: Int, mode: Int, executable: Boolean, requiresSetuid: Boolean): Boolean =
    uid == 0 && mode and 0b010010 == 0 && executable && (!requiresSetuid || mode and 0x800 != 0)

private fun systemUpdateTools(): LinuxUpdateTools? = runCatching {
    fun trustedDirectory(path: Path): Boolean = Files.isDirectory(path) &&
        (Files.getAttribute(path, "unix:uid") as Number).toInt() == 0 &&
        (Files.getAttribute(path, "unix:mode") as Number).toInt() and 0b010010 == 0

    fun executable(name: String, requiresSetuid: Boolean): Path {
        val source = Path.of("/usr/bin", name)
        val resolved = source.toRealPath()
        for (path in listOf(source, resolved)) {
            var parent = path.parent
            while (parent != null) {
                check(trustedDirectory(parent))
                parent = parent.parent
            }
        }
        check(Files.isRegularFile(resolved))
        check(isTrustedUpdateExecutable(
            (Files.getAttribute(resolved, "unix:uid") as Number).toInt(),
            (Files.getAttribute(resolved, "unix:mode") as Number).toInt(),
            Files.isExecutable(resolved), requiresSetuid
        ))
        return resolved
    }
    LinuxUpdateTools(executable("pkexec", true), executable("apt", false))
}.getOrNull()

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
    val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$").matchEntire(value) ?: return null
    return (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
}

internal fun selectQTalkAsset(release: QTalkRelease, windows: Boolean): ReleaseAsset? =
    release.assets.firstOrNull {
        if (windows) it.name == "QTalk-Setup-${release.version}.exe"
        else it.name.startsWith("QTalk_${release.version}-") && it.name.endsWith("_amd64.deb")
    }

internal fun copyInstallerPayload(
    input: InputStream,
    output: OutputStream,
    expectedSize: Long,
    onProgress: (Long, Long) -> Unit
): String {
    require(expectedSize in 1..maximumInstallerSize) { "Некорректный размер установщика" }
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var downloaded = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        check(count.toLong() <= expectedSize - downloaded) { "Размер загруженного установщика превышает ожидаемый" }
        output.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        downloaded += count
        onProgress(downloaded, expectedSize)
    }
    check(downloaded == expectedSize) { "Размер загруженного установщика не совпадает" }
    return digest.digest().joinToString("") { "%02x".format(it) }
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

internal class GitHubReleaseUpdater(
    private val installedVersion: String,
    private val platform: () -> UpdatePlatform = ::currentUpdatePlatform,
    private val linuxTools: () -> LinuxUpdateTools? = ::systemUpdateTools,
    private val releaseSource: (suspend () -> QTalkRelease?)? = null,
    private val installerDownload: ((ReleaseAsset, String, String, (Long, Long) -> Unit) -> Path)? = null,
    private val linuxInstaller: ((Path, LinuxUpdateTools) -> Unit)? = null
) {
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    suspend fun availableVersion(): String? {
        return availableRelease()?.version
    }

    suspend fun availableRelease(): QTalkRelease? {
        val plan = updatePlan(platform(), linuxTools)
        if (plan is UpdatePlan.Manual) return null
        val release = latestRelease() ?: return null
        if (!isNewerVersion(release.version, installedVersion)) return null
        return release.takeIf { selectQTalkAsset(it, plan == UpdatePlan.Windows) != null }
    }

    suspend fun checkAndInstall(onStatus: (String, Float?) -> Unit): Boolean {
        val plan = updatePlan(platform(), linuxTools)
        if (plan is UpdatePlan.Manual) {
            onStatus(plan.status, null)
            return false
        }
        onStatus("Проверка обновлений…", null)
        val release = latestRelease()
        if (release == null) {
            onStatus("У вас установлена последняя версия", null)
            return false
        }
        if (!isNewerVersion(release.version, installedVersion)) {
            onStatus("У вас установлена последняя версия", null)
            return false
        }
        val windows = plan == UpdatePlan.Windows
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
            (installerDownload ?: ::downloadAndVerify)(asset, expectedDigest, if (windows) ".exe" else ".deb") { downloaded, total ->
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
        if (windows) {
            onStatus("Установка версии ${release.version}… QTalk перезапустится автоматически", null)
            withContext(Dispatchers.IO) { launchWindowsInstaller(installer) }
            return true
        }
        onStatus("Установка обновления… Нужен графический агент авторизации. Проверка прав apt/pkexec не гарантирует возможность повышения привилегий в текущем сеансе.", null)
        withContext(Dispatchers.IO) { (linuxInstaller ?: ::installLinuxPackage)(installer, (plan as UpdatePlan.Debian).tools) }
        onStatus("Обновление установлено. Перезапустите QTalk.", null)
        return false
    }

    private suspend fun latestRelease(): QTalkRelease? = if (releaseSource != null) releaseSource.invoke() else fetchLatestRelease()

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
            val actualDigest = response.body().use { input ->
                check(response.statusCode() == 200) { "Не удалось скачать установщик: ${response.statusCode()}" }
                Files.newOutputStream(file).use { output ->
                    copyInstallerPayload(input, output, asset.size, onProgress)
                }
            }
            check(actualDigest.equals(expectedDigest, ignoreCase = true)) {
                "Контрольная сумма установщика не совпадает"
            }
            return file
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(file) }
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

    private fun installLinuxPackage(file: Path, tools: LinuxUpdateTools) {
        val process = ProcessBuilder(tools.pkexec.toString(), tools.apt.toString(), "install", "-y", file.toAbsolutePath().toString())
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Не удалось установить обновление: $output" }
    }
}
