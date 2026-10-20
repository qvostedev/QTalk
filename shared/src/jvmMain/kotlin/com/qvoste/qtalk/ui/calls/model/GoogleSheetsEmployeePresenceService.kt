package com.qvoste.qtalk.ui.calls.model

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSheetsEmployeePresenceService(
    endpoint: String? = System.getProperty("qtalk.presenceUrl"),
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/presence")
) : EmployeePresenceService {
    @Volatile private var configuredEndpoint = (endpoint ?: preferences.get("endpointUrl", "")).trim()
    override val enabled: Boolean get() = isPresenceEndpoint(configuredEndpoint)

    override fun loadEndpoint(): String = configuredEndpoint

    override fun saveEndpoint(url: String) {
        val normalized = url.trim()
        require(normalized.isEmpty() || isPresenceEndpoint(normalized)) {
            "Вставьте HTTPS-ссылку развёрнутого Google Apps Script, заканчивающуюся на /exec"
        }
        preferences.put("endpointUrl", normalized)
        preferences.flush()
        configuredEndpoint = normalized
    }

    override suspend fun publish(extension: String, status: EmployeeStatus) = withContext(Dispatchers.IO) {
        if (!enabled || extension.none(Char::isDigit)) return@withContext
        val endpoint = configuredEndpoint
        val body = "extension=${encode(extension.filter(Char::isDigit))}&status=${encode(status.sheetValue)}"
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", "QTalk/1.0")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            check(connection.responseCode in 200..299) {
                "Сервис статусов вернул HTTP ${connection.responseCode}"
            }
            connection.inputStream.close()
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun load(): Map<String, EmployeeStatus> = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext emptyMap()
        val endpoint = configuredEndpoint
        val separator = if ('?' in endpoint) '&' else '?'
        val connection = URI("$endpoint${separator}action=statuses").toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "QTalk/1.0")
            check(connection.responseCode in 200..299) {
                "Сервис статусов вернул HTTP ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val parts = line.split(',', limit = 2)
                    val extension = parts.getOrNull(0)?.filter(Char::isDigit).orEmpty()
                    if (extension.isBlank()) null else extension to
                        EmployeeStatus.fromSheet(parts.getOrNull(1).orEmpty())
                }.toMap()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

}

internal fun isPresenceEndpoint(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "script.google.com" &&
        uri.userInfo == null && uri.port == -1 && uri.query == null && uri.fragment == null &&
        Regex("/macros/s/[A-Za-z0-9_-]+/exec").matches(uri.path)
}.getOrDefault(false)
