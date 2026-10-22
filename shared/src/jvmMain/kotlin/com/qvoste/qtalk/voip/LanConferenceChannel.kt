package com.qvoste.qtalk.voip

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded room-capability channel. Directory readers retain the deployment's trust level. */
internal class LanConferenceChannel(address: InetAddress) : AutoCloseable {
    data class Peer(val muted: Boolean, val speaking: Boolean, val seenAt: Long, val acknowledgement: String)
    private val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(address, 0), 8) }
    private val token = UUID.randomUUID().toString()
    val endpoint = "${address.hostAddress}:${server.localPort}/$token"
    val peers = ConcurrentHashMap<String, Peer>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile var snapshot: RoomMessage.Snapshot? = null
    @Volatile var instruction = ""
    private val workers = Executors.newFixedThreadPool(4) { runnable -> Thread(runnable, "QTalk-RoomPeer").apply { isDaemon = true } }
    private val listener = Thread({
        while (!server.isClosed) {
            try {
                val socket = server.accept()
                if ((workers as java.util.concurrent.ThreadPoolExecutor).queue.size > 12) socket.close()
                else {
                    sockets.add(socket)
                    workers.execute { try { handle(socket) } finally { sockets.remove(socket) } }
                }
            } catch (_: java.io.IOException) { if (!server.isClosed) close() }
        }
    }, "QTalk-RoomListener").apply { isDaemon = true; start() }

    private fun handle(socket: Socket) = socket.use {
        try {
            if (!socket.inetAddress.isSiteLocalAddress) return@use
            socket.soTimeout = 1200
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt()
            if (size !in 1..1024) return@use
            val bytes = ByteArray(size); input.readFully(bytes)
            val request = bytes.toString(Charsets.UTF_8).split('\n')
            val state = snapshot ?: return@use
            if (request.size != 6 || !MessageDigest.isEqual(request[0].toByteArray(), token.toByteArray()) ||
                request[1] != state.roomId || request[2] !in state.members || request[2] == state.host ||
                request[3] !in setOf("0", "1") || request[4] !in setOf("0", "1") || request[5].length > 80) return@use
            peers[request[2]] = Peer(request[3] == "1", request[4] == "1", System.nanoTime(), request[5])
            val now = System.nanoTime()
            val active = peers.filterValues { now - it.seenAt < 2_000_000_000L }
            val muted = state.muted.filter { it == state.host }.toSet() + active.filterValues { it.muted }.keys
            // The mixer has a separate incoming audio level for each remote leg.
            // Guests receive the combined audio, so share the host's per-leg detection.
            val speaking = state.speaking - muted
            val body = ConferenceRoomWire.encode(state.copy(muted = muted, speaking = speaking)) + "\nLAN/1\n" + instruction
            val output = DataOutputStream(socket.getOutputStream())
            val payload = body.toByteArray(Charsets.UTF_8)
            output.writeInt(payload.size); output.write(payload); output.flush()
        } catch (_: java.io.IOException) { }
    }

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        workers.shutdownNow()
    }

    companion object {
        data class Reply(val snapshot: RoomMessage.Snapshot, val instruction: String)
        fun parseEndpoint(endpoint: String): Pair<InetSocketAddress, String>? = runCatching {
            val match = Regex("((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5})/([a-f0-9-]{36})").matchEntire(endpoint) ?: return null
            if (match.groupValues[1].split('.').any { it.toInt() !in 0..255 || (it.length > 1 && it.startsWith('0')) }) return null
            val address = InetAddress.getByName(match.groupValues[1])
            if (!address.isSiteLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return null
            val port = match.groupValues[2].toInt().takeIf { it in 1024..65535 } ?: return null
            InetSocketAddress(address, port) to match.groupValues[3]
        }.getOrNull()

        suspend fun exchange(endpoint: String, room: String, own: String, muted: Boolean, speaking: Boolean, acknowledgement: String): Reply =
            withContext(Dispatchers.IO) {
                val (address, token) = checkNotNull(parseEndpoint(endpoint)) { "Invalid LAN conference endpoint" }
                Socket().use { socket ->
                    socket.connect(address, 650); socket.soTimeout = 1000
                    val request = listOf(token, room, own, if (muted) "1" else "0", if (speaking) "1" else "0", acknowledgement)
                        .joinToString("\n").toByteArray(Charsets.UTF_8)
                    val output = DataOutputStream(socket.getOutputStream())
                    output.writeInt(request.size); output.write(request); output.flush()
                    val input = DataInputStream(socket.getInputStream())
                    val size = input.readInt(); check(size in 1..8192)
                    val bytes = ByteArray(size); input.readFully(bytes)
                    val parts = bytes.toString(Charsets.UTF_8).split("\nLAN/1\n", limit = 2)
                    Reply(checkNotNull(ConferenceRoomWire.decode(parts[0]) as? RoomMessage.Snapshot), parts.getOrElse(1) { "" })
                }
            }
    }
}
