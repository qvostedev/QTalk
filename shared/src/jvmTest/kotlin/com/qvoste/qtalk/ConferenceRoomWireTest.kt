package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlin.test.*

class ConferenceRoomWireTest {
    private val snapshot = RoomMessage.Snapshot("12345678-1234-1234-1234-123456789abc", 7, "101",
        listOf("101", "102", "103"), listOf("104"), setOf("102"), setOf("103"))
    @Test fun roundTripsRosterAndMicrophoneWithoutContactPersonalData() {
        assertEquals(snapshot, ConferenceRoomWire.decode(ConferenceRoomWire.encode(snapshot)))
        val mute = RoomMessage.Microphone(snapshot.roomId, true)
        assertEquals(mute, ConferenceRoomWire.decode(ConferenceRoomWire.encode(mute)))
    }
    @Test fun rejectsMalformedOrOversizedMessages() {
        assertNull(ConferenceRoomWire.decode("x".repeat(4097)))
        assertNull(ConferenceRoomWire.decode(ConferenceRoomWire.encode(snapshot.copy(host = "999"))))
        assertNull(ConferenceRoomWire.decode(ConferenceRoomWire.encode(snapshot.copy(muted = setOf("999")))))
        assertNull(ConferenceRoomWire.decode(ConferenceRoomWire.encode(snapshot.copy(roomId = "bad"))))
        assertNull(ConferenceRoomWire.decode(ConferenceRoomWire.encode(snapshot.copy(revision = -1))))
    }
}
