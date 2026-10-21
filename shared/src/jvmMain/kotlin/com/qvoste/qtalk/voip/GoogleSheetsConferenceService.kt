package com.qvoste.qtalk.voip

import com.qvoste.qtalk.ui.calls.model.isPresenceEndpoint
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Temporary room signalling; audio never passes through this service. */
internal class GoogleSheetsConferenceService(val endpoint: String) {
    val enabled get() = isPresenceEndpoint(endpoint)

    suspend fun request(action: String, fields: Map<String, String>): String = withContext(Dispatchers.IO) {
        require(enabled)
        val body = (fields + ("action" to action)).entries.joinToString("&") {
            URLEncoder.encode(it.key, UTF_8) + "=" + URLEncoder.encode(it.value, UTF_8)
        }
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(UTF_8)) }
            check(connection.responseCode in 200..299) { "Conference service unavailable" }
            connection.inputStream.bufferedReader(UTF_8).use { reader ->
                val buffer = CharArray(8193)
                var count = 0
                while (count < buffer.size) {
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read < 0) break
                    count += read
                }
                check(count <= 8192) { "Conference response too large" }
                String(buffer, 0, count)
            }
        } finally {
            connection.disconnect()
        }
    }
}
