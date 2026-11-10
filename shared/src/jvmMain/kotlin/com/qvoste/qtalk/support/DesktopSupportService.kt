package com.qvoste.qtalk.support

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.time.Duration
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun supportEndpoint(value: String, allowLocal: Boolean): URI {
    val uri = runCatching { URI(value) }.getOrNull() ?: throw SupportFailure(SupportError.NOT_CONFIGURED)
    val local = allowLocal && uri.scheme == "http" && uri.host in setOf("127.0.0.1", "[::1]", "::1", "localhost")
    if ((uri.scheme != "https" && !local) || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.rawPath != "/v1/reports" || uri.port == 0)
        throw SupportFailure(SupportError.NOT_CONFIGURED)
    return uri
}

class DesktopSupportService(private val endpoint: String?, private val collector: DiagnosticsCollector,
    private val allowLocal: Boolean = false, private val timeoutMillis: Long = 45_000,
    private val fileChooser: suspend () -> java.nio.file.Path? = ::chooseSupportFile) : SupportService {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()
    private val mutex = Mutex()
    private var snapshot: PreparedSupport? = null
    private var fingerprint: String? = null

    override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt {
        if (!draft.canSend) throw SupportFailure(SupportError.INVALID_DRAFT)
        val uri = supportEndpoint(endpoint?.takeIf { it.isNotBlank() } ?: throw SupportFailure(SupportError.NOT_CONFIGURED), allowLocal)
        if (!mutex.tryLock()) throw SupportFailure(SupportError.BUSY)
        try {
            stage(SupportStage.COLLECTING)
            val prepared = withContext(Dispatchers.IO) {
                val key = draftFingerprint(draft)
                if (key != fingerprint || snapshot == null) {
                    // Invalidate both together; no UUID is ever reused with new archive bytes.
                    snapshot = null; fingerprint = null
                    snapshot = collector.prepare(draft); fingerprint = key
                }
                snapshot!!
            }
            val boundary = "QTalk-${prepared.reportId}"
            val body = withContext(Dispatchers.IO) { multipart(prepared, boundary) }
            stage(SupportStage.UPLOADING)
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "multipart/form-data; boundary=$boundary").header("Accept", "application/json")
                .POST(ReportingBody(body) { stage(SupportStage.WAITING) }).build()
            val response = try {
                withTimeout(timeoutMillis) {
                    val future = http.sendAsync(request) { LimitedBody(16 * 1024) }
                    future.awaitSupport()
                }
            } catch (_: TimeoutCancellationException) { throw SupportFailure(SupportError.DELIVERY_UNCERTAIN) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                if (generateSequence(error as Throwable?) { it.cause }.any { it is ResponseTooLarge })
                    throw SupportFailure(SupportError.INVALID_RESPONSE)
                throw SupportFailure(SupportError.DELIVERY_UNCERTAIN)
            }
            val confirmed = receipt(response.statusCode(), response.body(), prepared.reportId)
            snapshot = null
            fingerprint = null
            return confirmed
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: SupportFailure) { throw failure }
        catch (_: Exception) { throw SupportFailure(SupportError.COLLECTION) }
        finally { mutex.unlock() }
    }
    override suspend fun chooseScreenshot(): SupportAttachment? {
        val path = fileChooser() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                if (!DiagnosticsCollector.safePath(path) || !Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.size(path) > 4 * 1024 * 1024)
                    throw SupportFailure(SupportError.INVALID_IMAGE)
                val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(4 * 1024 * 1024 + 1) }
                normalizeScreenshot(path.fileName.toString(), bytes)
            } catch (_: Exception) { throw SupportFailure(SupportError.INVALID_IMAGE) }
        }
    }
    private fun draftFingerprint(draft: SupportDraft): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (value in listOf(draft.title.trim(), draft.description.trim())) {
            val bytes = value.toByteArray(); digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes)
        }
        digest.update(if (draft.attachment == null) 0.toByte() else 1.toByte())
        draft.attachment?.let { digest.update(it.pngBytes()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun multipart(prepared: PreparedSupport, boundary: String): ByteArray = ByteArrayOutputStream().apply {
        write("--$boundary\r\nContent-Disposition: form-data; name=\"report\"\r\nContent-Type: application/json\r\n\r\n${prepared.report}\r\n".toByteArray())
        write("--$boundary\r\nContent-Disposition: form-data; name=\"diagnostics\"; filename=\"diagnostics.zip\"\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
        write(prepared.zip); write("\r\n--$boundary--\r\n".toByteArray())
    }.toByteArray()

    private fun receipt(status: Int, bytes: ByteArray, reportId: String): SupportReceipt {
        val json = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()
        val code = json?.get("code")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (status == 429) throw SupportFailure(SupportError.RATE_LIMITED)
        if (status != 200) {
            if (json?.get("ok") != JsonPrimitive(false)) throw SupportFailure(SupportError.INVALID_RESPONSE)
            val error = when (code) {
                "missing_config" -> if (status == 503) SupportError.NOT_CONFIGURED else SupportError.INVALID_RESPONSE
                "pending", "delivery_uncertain", "uncertain", "temporarily_unavailable" -> SupportError.DELIVERY_UNCERTAIN
                "upstream_rejected", "id_conflict", "invalid_report", "invalid_zip", "invalid_parts", "invalid_multipart",
                "invalid_content_type", "invalid_body", "too_large", "request_timeout", "not_found", "method_not_allowed" -> SupportError.REJECTED
                else -> SupportError.INVALID_RESPONSE
            }
            throw SupportFailure(error)
        }
        try {
            val value = json ?: throw IllegalArgumentException()
            val ticket = value.getValue("ticketId").jsonPrimitive
            val id = value.getValue("reportId").jsonPrimitive
            if (value.getValue("ok") != JsonPrimitive(true) || !id.isString || id.content != reportId ||
                !ticket.isString || !ticket.content.matches(Regex("QTK-[A-Za-z0-9-]{3,80}"))) throw IllegalArgumentException()
            return SupportReceipt(reportId, ticket.content)
        } catch (_: Exception) { throw SupportFailure(SupportError.INVALID_RESPONSE) }
    }
}

private suspend fun <T> CompletableFuture<T>.awaitSupport(): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel(true) }
    whenComplete { value, error ->
        if (error != null) continuation.resumeWithException(error) else continuation.resume(value)
    }
}
private class ResponseTooLarge : Exception()
private class ReportingBody(bytes: ByteArray, private val uploaded: () -> Unit) : HttpRequest.BodyPublisher {
    private val delegate = HttpRequest.BodyPublishers.ofByteArray(bytes)
    override fun contentLength(): Long = delegate.contentLength()
    override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
        delegate.subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(subscription: Flow.Subscription) = subscriber.onSubscribe(subscription)
            override fun onNext(item: ByteBuffer) = subscriber.onNext(item)
            override fun onError(error: Throwable) = subscriber.onError(error)
            override fun onComplete() { uploaded(); subscriber.onComplete() }
        })
    }
}
private class LimitedBody(private val limit: Int) : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val output = ByteArrayOutputStream()
    private var subscription: Flow.Subscription? = null
    override fun getBody(): CompletionStage<ByteArray> = result
    override fun onSubscribe(value: Flow.Subscription) { subscription = value; value.request(1) }
    override fun onNext(buffers: List<ByteBuffer>) {
        for (buffer in buffers) {
            if (output.size() + buffer.remaining() > limit) { subscription?.cancel(); result.completeExceptionally(ResponseTooLarge()); return }
            val bytes = ByteArray(buffer.remaining()); buffer.get(bytes); output.write(bytes)
        }
        subscription?.request(1)
    }
    override fun onError(error: Throwable) { result.completeExceptionally(error) }
    override fun onComplete() { result.complete(output.toByteArray()) }
}
