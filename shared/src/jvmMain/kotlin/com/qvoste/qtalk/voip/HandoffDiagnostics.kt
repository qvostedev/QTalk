package com.qvoste.qtalk.voip

import com.qvoste.qtalk.QTalkBuildInfo

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.Level


internal class HandoffDiagnostics(directory: Path, limit: Int = 262144, count: Int = 4) : AutoCloseable {
    private val handler = runCatching {
        Files.createDirectories(directory)
        FileHandler(directory.resolve("qtalk-%g.log").toString(), limit, count, true).apply {
            encoding = "UTF-8"
            formatter = object : Formatter() {
                override fun format(record: LogRecord) = "${Instant.ofEpochMilli(record.millis)} QTalk ${QTalkBuildInfo.VERSION} LAN2 ${record.message}\n"
            }
        }
    }.getOrNull()

    fun event(code: String, extension: String = "", transfer: String? = null, phase: String = "",
        peer: String = "", state: String = "", reason: String = "", protocol: Int = 0, target: LanSipEndpoint? = null) {
        runCatching {
            fun safe(value: String) = value.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{0,80}")) } ?: "invalid"
            fun number(value: String) = value.takeIf { it.matches(Regex("[0-9]{1,20}")) } ?: "unknown"
            val correlation = transfer?.let { id -> MessageDigest.getInstance("SHA-256")
                .digest(id.toByteArray()).take(8).joinToString("") { "%02x".format(it) } } ?: "none"
            val line = "event=${safe(code)} ext=${number(extension)} tx=$correlation phase=${safe(phase)} " +
                "peer=${number(peer)} state=${safe(state)} reason=${safe(reason)} protocol=$protocol target=${target ?: "none"}"
            handler?.publish(LogRecord(Level.INFO, line)); handler?.flush()
        }
    }
    override fun close() { runCatching { handler?.close() } }
}

internal object Lan2Diagnostics {
    private val sink by lazy { runCatching {
        val local = System.getenv("LOCALAPPDATA") ?: return@runCatching null
        HandoffDiagnostics(Path.of(local, "QTalk", "logs"))
    }.getOrNull() }
    fun event(code: String, extension: String = "", transfer: String? = null, phase: String = "",
        peer: String = "", state: String = "", reason: String = "", protocol: Int = 0, target: LanSipEndpoint? = null) {
        sink?.event(code, extension, transfer, phase, peer, state, reason, protocol, target)
    }
}
