package com.qvoste.qtalk.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

/** Explicit loopback HTTP test double. Never contacts support or Telegram. */
class DesktopSupportServiceTest {
    private val draft = SupportDraft("Test title", "Synthetic description")
    private fun collector() = DiagnosticsCollector(emptyList(), DiagnosticSanitizer(), "test")
    @Test fun endpointPolicy() {
        for (endpoint in listOf("http://example.org/v1/reports", "https://user:pass@example.org/v1/reports", "https://example.org/v1/reports?q=a", "https://example.org/v1/reports#frag", "https://example.org/other", "ftp://example.org/v1/reports"))
            assertFailsWith<SupportFailure> { supportEndpoint(endpoint, true) }
        assertFailsWith<SupportFailure> { supportEndpoint("http://127.0.0.1:123/v1/reports", false) }
        assertEquals("https", supportEndpoint("https://support.example.org/v1/reports", false).scheme)
        assertEquals("http", supportEndpoint("http://127.0.0.1:123/v1/reports", true).scheme)
    }
    @Test fun rejectsShortTitleBeforeNetworkAndConfigBeforeCollection() = runBlocking {
        val service = DesktopSupportService(null, collector())
        assertEquals(SupportError.INVALID_DRAFT, assertFailsWith<SupportFailure> { service.send(draft.copy(title = "ab")) {} }.error)
        assertEquals(SupportError.NOT_CONFIGURED, assertFailsWith<SupportFailure> { service.send(draft) {} }.error)
    }
    @Test fun multipartReceiptAndIdenticalRetrySnapshot() = runBlocking {
        val bodies = mutableListOf<ByteArray>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/reports") { exchange ->
            val bytes = exchange.requestBody.readBytes(); bodies.add(bytes)
            val multipart = bytes.toString(Charsets.ISO_8859_1)
            val json = multipart.substringAfter("\r\n\r\n").substringBefore("\r\n--")
            val id = Json.parseToJsonElement(json).jsonObject.getValue("reportId").jsonPrimitive.content
            val response = if (bodies.size == 1) "{\"ok\":false,\"code\":\"delivery_uncertain\"}" else "{\"ok\":true,\"ticketId\":\"QTK-test123\",\"reportId\":\"$id\"}"
            val data = response.toByteArray(); exchange.sendResponseHeaders(if (bodies.size == 1) 502 else 200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }; exchange.close()
        }
        server.start()
        try {
            val service = DesktopSupportService("http://127.0.0.1:${server.address.port}/v1/reports", collector(), allowLocal = true)
            assertEquals(SupportError.DELIVERY_UNCERTAIN, assertFailsWith<SupportFailure> { service.send(draft) {} }.error)
            val stages = mutableListOf<SupportStage>()
            assertEquals("QTK-test123", service.send(draft) { stages.add(it) }.ticketId)
            assertContentEquals(bodies[0], bodies[1])
            val fixture = java.nio.file.Path.of("build", "support-local", "native-support-multipart.bin")
            Files.createDirectories(fixture.parent)
            Files.write(fixture, bodies[0])
            val multipart = bodies[0].toString(Charsets.ISO_8859_1)
            assertEquals(2, Regex("Content-Disposition: form-data;").findAll(multipart).count())
            val zipStart = multipart.indexOf("PK\u0003\u0004")
            val zipEnd = multipart.lastIndexOf("\r\n--")
            assertTrue(unzip(bodies[0].copyOfRange(zipStart, zipEnd)).containsKey("report.json"))
            assertTrue(stages.containsAll(listOf(SupportStage.UPLOADING, SupportStage.WAITING)))
            service.send(draft.copy(description = "A changed description")) {}
            assertFalse(bodies[0].contentEquals(bodies[2]))
        } finally { server.stop(0) }
    }
    @Test fun rejectsFakeSuccessAndMapsErrorsWithoutLeakingServerContent() = runBlocking {
        val scenarios = listOf(
            200 to "{\"ok\":true,\"ticketId\":\"QTK-fake\",\"reportId\":\"wrong\"}",
            200 to "{\"ok\":true}", 200 to "secret server exception", 202 to "{\"ok\":false,\"code\":\"pending\"}",
            503 to "{\"ok\":false,\"code\":\"missing_config\"}", 429 to "{\"ok\":false,\"code\":\"rate_limited\"}",
            200 to "x".repeat(20000),
            500 to "unrecognized server body",
            400 to "{\"ok\":false,\"code\":\"unknown_future_code\"}",
            502 to "{\"ok\":false,\"code\":\"upstream_rejected\"}")
        val expected = listOf(SupportError.INVALID_RESPONSE, SupportError.INVALID_RESPONSE, SupportError.INVALID_RESPONSE,
            SupportError.DELIVERY_UNCERTAIN, SupportError.NOT_CONFIGURED, SupportError.RATE_LIMITED, SupportError.INVALID_RESPONSE,
            SupportError.INVALID_RESPONSE, SupportError.INVALID_RESPONSE, SupportError.REJECTED)
        val index = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/reports") { exchange ->
            exchange.requestBody.readBytes()
            val (status, body) = scenarios[index.getAndIncrement()]
            val data = body.toByteArray(); exchange.sendResponseHeaders(status, data.size.toLong())
            exchange.responseBody.use { it.write(data) }; exchange.close()
        }
        server.start()
        try {
            val service = DesktopSupportService("http://127.0.0.1:${server.address.port}/v1/reports", collector(), allowLocal = true)
            for (error in expected) assertEquals(error, assertFailsWith<SupportFailure> { service.send(draft) {} }.error)
        } finally { server.stop(0) }
    }
    @Test fun timeoutIsUncertainAndBusyDoubleSubmitIsBlocked() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/reports") { exchange ->
            exchange.requestBody.readBytes(); entered.complete(Unit); Thread.sleep(600); exchange.close()
        }
        server.start()
        try {
            val service = DesktopSupportService("http://127.0.0.1:${server.address.port}/v1/reports", collector(), true, timeoutMillis = 250)
            val first = async { assertFailsWith<SupportFailure> { service.send(draft) {} }.error }
            entered.await()
            assertEquals(SupportError.BUSY, assertFailsWith<SupportFailure> { service.send(draft) {} }.error)
            assertEquals(SupportError.DELIVERY_UNCERTAIN, first.await())
        } finally { server.stop(0) }
    }
    @Test fun sessionRetainsDraftOnErrorAndChooserCancellation() = runBlocking {
        val attachment = SupportAttachment("test.png", byteArrayOf(1))
        val service = object : SupportService {
            override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt = throw SupportFailure(SupportError.DELIVERY_UNCERTAIN)
            override suspend fun chooseScreenshot(): SupportAttachment? = null
        }
        val session = SupportSession(service)
        session.edit { draft.copy(attachment = attachment) }; session.send(); session.chooseScreenshot()
        assertEquals(attachment, session.state.value.draft.attachment)
        assertEquals(draft.title, session.state.value.draft.title)
        assertFalse(session.state.value.busy)
    }
}
