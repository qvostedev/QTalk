package com.qvoste.qtalk.support

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*

const val MAX_SUPPORT_ZIP = 8 * 1024 * 1024
internal data class PreparedSupport(val reportId: String, val report: String, val zip: ByteArray)

class DiagnosticsCollector(private val logDirectories: List<Path>, private val sanitizer: DiagnosticSanitizer,
    private val appVersion: String, private val maxRawBytes: Int = 4 * 1024 * 1024,
    private val installMethod: String = currentInstallMethod()) {
    internal fun prepare(draft: SupportDraft): PreparedSupport {
        if (draft.title.trim().length > 160 || draft.description.trim().length > 12000)
            throw SupportFailure(SupportError.INVALID_DRAFT)
        val id = UUID.randomUUID().toString()
        // An improvement suggestion carries no diagnostics; the label keeps the Telegram caption self-explanatory.
        val improvement = draft.kind == SupportKind.IMPROVEMENT
        val title = ((if (improvement) "Предложение: " else "") + draft.title.trim()).take(160)
        val report = buildJsonObject {
            put("schemaVersion", 1); put("reportId", id); put("title", title); put("description", draft.description.trim())
            put("appVersion", appVersion); put("platform", platform()); put("createdAt", Instant.now().toString())
            put("screenshotAttached", draft.attachment != null)
        }.toString()
        val entries = linkedMapOf("report.json" to report.toByteArray())
        if (improvement) {
            entries["manifest.json"] = buildJsonObject {
                put("diagnostics", false)
                put("policy", "Improvement suggestion: no QTalk logs, system information or account data are collected.")
            }.toString().toByteArray()
            draft.attachment?.let { entries["screenshot.png"] = it.pngBytes() }
            return PreparedSupport(id, report, archive(entries))
        }
        entries["system.json"] = buildJsonObject {
            for (key in listOf("os.name", "os.version", "os.arch", "java.version", "java.vendor"))
                put(key, sanitizer.clean(System.getProperty(key, "unknown").take(160)))
            put("appVersion", appVersion); put("installMethod", installMethod.takeIf { it in setOf("desktop", "installer", "nix", "deb", "portable", "test") } ?: "desktop")
        }.toString().toByteArray()
        var budget = maxRawBytes.coerceIn(0, 4 * 1024 * 1024)
        var omitted = 0
        var truncated = 0
        var unreadable = 0
        var logIndex = 0
        val candidates = mutableListOf<Path>()
        for (root in logDirectories.take(3)) {
            if (!safePath(root) || !Files.isDirectory(root, NOFOLLOW_LINKS)) continue
            // Exact fixed names only; never enumerate a home directory or recurse.
            for (prefix in listOf("qtalk-", "qtalk-app-")) for (index in 0..3) {
                val file = root.resolve("$prefix$index.log")
                if (safePath(file) && Files.isRegularFile(file, NOFOLLOW_LINKS)) candidates.add(file)
            }
        }
        for (file in candidates.sortedByDescending { runCatching { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toMillis() }.getOrDefault(0) }.take(24)) {
            if (budget <= 0) { omitted++; continue }
            try {
                Files.newByteChannel(file, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
                    val size = channel.size()
                    val count = minOf(size, budget.toLong(), 512 * 1024L).toInt()
                    val start = (size - count).coerceAtLeast(0)
                    if (start > 0) truncated++
                    channel.position(start)
                    val buffer = ByteBuffer.allocate(count)
                    while (buffer.hasRemaining() && channel.read(buffer) > 0) { /* bounded */ }
                    budget -= buffer.position()
                    var raw = buffer.array().copyOf(buffer.position()).toString(Charsets.UTF_8)
                    // Avoid starting in a folded secret or protocol body when tailing a file.
                    if (start > 0) {
                        val marker = Regex("(?m)^\\d{4}-\\d{2}-\\d{2}[^\\n]*QTalk ").find(raw)
                        raw = if (marker == null) "[tail omitted: no safe record boundary]" else raw.substring(marker.range.first)
                    }
                    entries["logs/qtalk-${logIndex++}.txt"] = sanitizer.clean(raw).toByteArray()
                }
            } catch (_: Exception) { unreadable++ }
        }
        entries["manifest.json"] = buildJsonObject {
            put("omitted", omitted); put("truncated", truncated); put("unreadable", unreadable)
            put("policy", "Exact QTalk logs only; bounded tails; best-effort cleaning may miss personal data. No native SIP dumps.")
        }.toString().toByteArray()
        draft.attachment?.let { entries["screenshot.png"] = it.pngBytes() }
        return PreparedSupport(id, report, archive(entries))
    }
    private fun archive(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name).apply { time = 0 }); zip.write(bytes); zip.closeEntry()
                if (output.size() > MAX_SUPPORT_ZIP) throw SupportFailure(SupportError.COLLECTION)
            }
        }
        if (output.size() > MAX_SUPPORT_ZIP) throw SupportFailure(SupportError.COLLECTION)
        return output.toByteArray()
    }
    companion object {
        fun defaultLogDirectories(
            osName: String = System.getProperty("os.name", ""),
            userHome: String = System.getProperty("user.home", ""),
            localAppData: String? = System.getenv("LOCALAPPDATA"),
            xdgStateHome: String? = System.getenv("XDG_STATE_HOME")
        ): List<Path> {
            fun absolute(value: String?): Path? = value?.takeIf(String::isNotBlank)?.let {
                runCatching { Path.of(it).takeIf { path -> path.isAbsolute } }.getOrNull()
            }
            val home = absolute(userHome)
            val directory = when {
                osName.contains("windows", ignoreCase = true) ->
                    (absolute(localAppData) ?: home?.resolve("AppData/Local"))?.resolve("QTalk/logs")
                osName.contains("mac", ignoreCase = true) -> home?.resolve("Library/Logs/QTalk")
                else -> (absolute(xdgStateHome) ?: home?.resolve(".local/state"))?.resolve("QTalk/logs")
            }
            return listOfNotNull(directory)
        }
        fun currentInstallMethod(): String = System.getenv("QTALK_INSTALL_METHOD")?.trim()?.lowercase()
            ?.takeIf { it in setOf("installer", "nix", "deb", "portable", "test") } ?: "desktop"
        internal fun safePath(path: Path): Boolean = generateSequence(path.toAbsolutePath().normalize()) { it.parent }.none { component ->
            // NTFS directory junctions are isOther=true, isSymbolicLink=false.
            // Reject either kind at every ancestor, including a parent of the log directory.
            try {
                val attributes = Files.readAttributes(component, java.nio.file.attribute.BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                attributes.isSymbolicLink || attributes.isOther
            } catch (_: java.nio.file.NoSuchFileException) { false }
            catch (_: Exception) { true }
        }
        internal fun platform(): String {
            val os = System.getProperty("os.name", "").lowercase()
            return when { os.contains("windows") -> "windows"; os.contains("linux") -> "linux"; os.contains("mac") -> "macos"; else -> "other" }
        }
    }
}
