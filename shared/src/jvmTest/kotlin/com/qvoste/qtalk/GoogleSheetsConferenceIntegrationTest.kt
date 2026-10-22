package com.qvoste.qtalk

import com.qvoste.qtalk.voip.ConferenceRoomWire
import com.qvoste.qtalk.voip.GoogleSheetsConferenceService
import com.qvoste.qtalk.voip.RoomMessage
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** Opt-in deployment check. Creates only synthetic rooms and removes them in finally. */
class GoogleSheetsConferenceIntegrationTest {
    @Test fun deployedServiceRoundTripsRosterAndGuestMicrophones() = runBlocking {
        val endpoint = System.getenv("QTALK_ROOM_TEST_ENDPOINT").orEmpty()
        assumeTrue("Set QTALK_ROOM_TEST_ENDPOINT for a live deployment check", endpoint.isNotBlank())
        val service = GoogleSheetsConferenceService(endpoint)
        val id = UUID.randomUUID().toString()
        val token = UUID.randomUUID().toString()
        val snapshot = RoomMessage.Snapshot(id, 1, "99000001", listOf("99000001", "99000002", "99000003"),
            emptyList(), emptySet(), emptySet(), "192.168.100.10:18000/" + UUID.randomUUID().toString())
        val owner = mapOf("role" to "host", "token" to token)
        try {
            assertEquals("peers\n", service.request("roomSync", owner + ("snapshot" to ConferenceRoomWire.encode(snapshot))))
            suspend fun guest(number: String, muted: Boolean) = service.request("roomSync", mapOf(
                "extension" to number, "host" to snapshot.host, "muted" to if (muted) "1" else "0"))
            assertEquals(snapshot, ConferenceRoomWire.decode(guest("99000002", false)))
            val muted = ConferenceRoomWire.decode(guest("99000003", true)) as RoomMessage.Snapshot
            assertEquals(snapshot.members, muted.members)
            assertEquals(setOf("99000003"), muted.muted)
            val acknowledgement = service.request("roomSync", owner + ("snapshot" to ConferenceRoomWire.encode(snapshot)))
            assertTrue("99000002,0" in acknowledgement)
            assertTrue("99000003,1" in acknowledgement)
            assertEquals("none", guest("99000004", false))
            assertEquals("denied", service.request("roomClose", mapOf("role" to "host", "token" to UUID.randomUUID().toString(), "room" to id)))
        } finally {
            assertEquals("ok", service.request("roomClose", owner + ("room" to id)))
        }
        assertEquals("none", service.request("roomSync", mapOf("extension" to "99000002", "host" to snapshot.host, "muted" to "0")))
    }
}
