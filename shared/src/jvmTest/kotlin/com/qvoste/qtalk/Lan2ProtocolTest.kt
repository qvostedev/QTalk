package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class Lan2ProtocolTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private fun room(endpoint: String = "") = RoomMessage.Snapshot(id, 1, "101", listOf("101", "102", "103"),
        emptyList(), emptySet(), emptySet(), endpoint)

    @Test fun endpointAndProtocolNeverAcceptPublicDnsOrLegacyTargets() {
        listOf("8.8.8.8:5060", "127.0.0.1:5060", "169.254.1.1:5060", "224.1.1.1:5060",
            "localhost:5060", "10.0.0.01:5060", "10.0.0.1:0", "10.0.0.1:65536", "10.0.0.1:5060/path").forEach {
            assertNull(LanSipEndpoint.parse(it), it)
        }
        val prefix = ConferenceRoomWire.encode(room())
        assertNull(LanConferenceChannel.decodeReply(prefix + "\nLAN/1\nprepare|secret"))
        listOf("102=8.8.8.8:5060", "999=10.0.0.1:5060", "102=10.0.0.1:5060\n102=10.0.0.2:5060").forEach {
            assertNull(LanConferenceChannel.decodeReply(prefix + "\nLAN/2\n\n" + it))
        }
        assertEquals(5061, LanConferenceChannel.decodeReply(prefix + "\nLAN/2\n\n102=10.0.0.2:5061")?.endpoints?.get("102")?.port)
    }

    @Test fun observedSourceBindsEndpointsAndLegacyCannotReceivePrepare(): Unit = runBlocking {
        val address = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.first { it is Inet4Address && it.isSiteLocalAddress }
        LanConferenceChannel(address).use { channel ->
            val state = room(channel.endpoint); channel.snapshot = state
            val own = assertNotNull(LanSipEndpoint.parse("${address.hostAddress}:15060"))
            val reply = LanConferenceChannel.exchange(channel.endpoint, id, "102", false, false, "", own)
            assertEquals(own, reply.endpoints["102"])
            assertEquals("LAN2", channel.peers["102"]?.capability)
            assertFails { LanConferenceChannel.exchange(channel.endpoint, id, "103", false, false, "",
                assertNotNull(LanSipEndpoint.parse("10.254.254.254:5060"))) }
            assertNull(channel.peers["103"])
            channel.handoffEndpoints = reply.endpoints
            assertFails { LanConferenceChannel.exchange(channel.endpoint, id, "102", false, false, "",
                assertNotNull(LanSipEndpoint.parse("${address.hostAddress}:15061"))) }
            channel.instruction = "LAN2|prepare|$id|102|102,103"
            val (target, token) = assertNotNull(LanConferenceChannel.parseEndpoint(channel.endpoint))
            Socket().use { socket ->
                socket.connect(target, 1000); socket.soTimeout = 1500
                val request = listOf(token, id, "103", "0", "0", "").joinToString("\n").toByteArray()
                DataOutputStream(socket.getOutputStream()).apply { writeInt(request.size); write(request); flush() }
                val input = DataInputStream(socket.getInputStream())
                val bytes = ByteArray(input.readInt()); input.readFully(bytes)
                assertFalse(bytes.toString(Charsets.UTF_8).contains("prepare"))
            }
            assertEquals("", channel.peers["103"]?.capability)
        }
    }

    @Test fun admissionBindsHeaderRoomOriginalEndpointAndSingleUse() {
        val endpoint = assertNotNull(LanSipEndpoint.parse("192.168.1.2:5067"))
        val other = assertNotNull(LanSipEndpoint.parse("192.168.1.3:5067"))
        val permit = ConferenceHandoffPermit(id, "102", "room", 100)
        fun accept(header: String? = id, room: String = "room", now: Long = 10,
            staged: Boolean = false, original: Boolean = true, contact: LanSipEndpoint? = endpoint,
            from: String = endpoint.host, remote: String = "102") = LanHandoffAdmission.accepts(
                permit, remote, header, room, now, staged, original, from, contact, endpoint)
        assertTrue(accept())
        assertFalse(accept(header = null)); assertFalse(accept(header = "foreign"))
        assertFalse(accept(room = "other")); assertFalse(accept(now = 100))
        assertFalse(accept(staged = true)); assertFalse(accept(original = false))
        assertFalse(accept(contact = other)); assertFalse(accept(contact = null))
        assertFalse(accept(from = other.host)); assertFalse(accept(remote = "999"))
    }

    @Test fun streamsRunningWithoutReceivedRtpCannotClaimReady() {
        fun call(received: Long, sent: Long): org.linphone.core.Call {
            val stats = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
                arrayOf(org.linphone.core.CallStats::class.java)) { _, method, _ ->
                when (method.name) { "getRtpPacketRecv" -> received; "getRtpPacketSent" -> sent; else -> null }
            } as org.linphone.core.CallStats
            return java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
                arrayOf(org.linphone.core.Call::class.java)) { _, method, _ ->
                when (method.name) { "getState" -> org.linphone.core.Call.State.StreamsRunning
                    "getAudioStats" -> stats; else -> null }
            } as org.linphone.core.Call
        }
        assertFalse(LanHandoffSip.ready(listOf(call(0, 20)), 1, null, false))
        assertFalse(LanHandoffSip.ready(listOf(call(20, 0)), 1, null, false))
        assertTrue(LanHandoffSip.ready(listOf(call(20, 20)), 1, null, false))
    }

}
