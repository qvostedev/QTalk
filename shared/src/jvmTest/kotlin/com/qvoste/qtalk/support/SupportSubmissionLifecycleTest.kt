package com.qvoste.qtalk.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals

/** Loopback receiver only: checks local submission lifecycle, not real Telegram delivery. */
class SupportSubmissionLifecycleTest {
    @Test
    fun confirmedSessionDoesNotResubmitOnAnotherClick() = runBlocking {
        var deliveries = 0
        val service = object : SupportService {
            override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt {
                deliveries++
                return SupportReceipt("test-report", "QTK-lifecycle-test")
            }
            override suspend fun chooseScreenshot(): SupportAttachment? = null
        }
        val session = SupportSession(service)
        session.edit { SupportDraft("Repeated click", "A synthetic test report") }
        session.send()
        session.send()
        assertEquals(1, deliveries, "Confirmed receipt must disable repeat sends until a new draft is started")
    }

    @Test
    fun aNewSubmissionAfterConfirmedDeliveryGetsNewSnapshotEvenForIdenticalText() = runBlocking {
        val reportIds = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/reports") { exchange ->
            val multipart = exchange.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            val metadata = multipart.substringAfter("\r\n\r\n").substringBefore("\r\n--")
            val id = Json.parseToJsonElement(metadata).jsonObject.getValue("reportId").jsonPrimitive.content
            reportIds += id
            val response = """{"ok":true,"ticketId":"QTK-lifecycle-test","reportId":"$id"}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            val collector = DiagnosticsCollector(emptyList(), DiagnosticSanitizer(), "local-test")
            val service = DesktopSupportService(
                "http://127.0.0.1:${server.address.port}/v1/reports", collector, allowLocal = true
            )
            val draft = SupportDraft("Repeated issue", "A synthetic issue happened again")
            val first = service.send(draft) {}
            val second = service.send(draft) {}
            assertNotEquals(first.reportId, second.reportId,
                "A confirmed report must release its cached snapshot; only uncertain retries reuse it")
            assertNotEquals(reportIds[0], reportIds[1])
        } finally {
            server.stop(0)
        }
    }
}
