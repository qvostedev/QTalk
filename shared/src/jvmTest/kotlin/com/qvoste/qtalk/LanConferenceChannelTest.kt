package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import java.net.NetworkInterface
import java.net.Inet4Address
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class LanConferenceChannelTest {
    @Test fun sharesLiveMicrophonesAndSpeechAndRejectsWrongRoomCapabilities(): Unit = runBlocking {
        val address = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        assumeTrue("A private LAN interface is required", address != null)
        LanConferenceChannel(checkNotNull(address)).use { server ->
            val room = RoomMessage.Snapshot("11111111-1111-1111-1111-111111111111", 1, "101", listOf("101", "102", "103"),
                emptyList(), emptySet(), setOf("101"), server.endpoint)
            server.snapshot = room
            val endpoint = checkNotNull(LanConferenceChannel.parseEndpoint(server.endpoint)).first
            java.net.Socket().use { socket ->
                socket.connect(endpoint, 1000)
                socket.soTimeout = 1000
                val output = java.io.DataOutputStream(socket.getOutputStream())
                output.writeInt(1025)
                output.flush()
                assertFails { java.io.DataInputStream(socket.getInputStream()).readInt() }
            }
            val first = LanConferenceChannel.exchange(server.endpoint, room.roomId, "102", true, false, "")
            assertEquals(setOf("102"), first.snapshot.muted)
            assertEquals(setOf("101"), first.snapshot.speaking)
            server.snapshot = room.copy(speaking = setOf("101", "103"))
            val second = LanConferenceChannel.exchange(server.endpoint, room.roomId, "103", false, true, "")
            assertEquals(setOf("101", "103"), second.snapshot.speaking)
            assertEquals(setOf("102"), second.snapshot.muted)
            server.instruction = "prepare|22222222-2222-2222-2222-222222222222|102|102,103"
            val third = LanConferenceChannel.exchange(server.endpoint, room.roomId, "102", false, true, "ack")
            assertEquals(server.instruction, third.instruction)
            assertTrue(third.snapshot.muted.isEmpty())
            assertEquals(setOf("101", "103"), third.snapshot.speaking)
            assertEquals("ack", server.peers["102"]?.acknowledgement)
            assertFails { LanConferenceChannel.exchange(server.endpoint, room.roomId, "999", false, false, "") }
            assertFails { LanConferenceChannel.exchange(server.endpoint, "22222222-2222-2222-2222-222222222222", "102", false, false, "") }
            assertFails { LanConferenceChannel.exchange(server.endpoint.substringBefore('/') + "/33333333-3333-3333-3333-333333333333", room.roomId, "102", false, false, "") }
        }
    }

    @Test fun rejectsPublicLoopbackAndMalformedDestinations() {
        val token = "11111111-1111-1111-1111-111111111111"
        listOf("8.8.8.8:1234/$token", "127.0.0.1:1234/$token", "169.254.169.254:1234/$token",
            "localhost:1234/$token", "192.168.1.1:80/$token", "192.168.1.1:70000/$token").forEach {
            assertNull(LanConferenceChannel.parseEndpoint(it))
        }
        assertNotNull(LanConferenceChannel.parseEndpoint("192.168.1.2:12000/$token"))
    }
}
