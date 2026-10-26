package com.qvoste.qtalk.voip

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Exchanges room state with clients on a trusted LAN. */
internal class LanConferenceChannel(address: InetAddress) : AutoCloseable {
    data class Peer(
        val muted: Boolean,
        val speaking: Boolean,
        val seenAt: Long,
        val acknowledgement: String
    )

    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(address, 0), 8)
    }
    private val token = UUID.randomUUID().toString()
    val endpoint = "${address.hostAddress}:${server.localPort}/$token"
    val peers = ConcurrentHashMap<String, Peer>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile var snapshot: RoomMessage.Snapshot? = null
    @Volatile var instruction = ""

    private val workers = ThreadPoolExecutor(
        4, 4, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(12),
        { task -> Thread(task, "QTalk-RoomPeer").apply { isDaemon = true } }
    )

    init {
        Thread(::acceptConnections, "QTalk-RoomListener").apply {
            isDaemon = true
            start()
        }
    }

    private fun acceptConnections() {
        while (!server.isClosed) {
            try {
                val socket = server.accept()
                sockets.add(socket)
                try {
                    workers.execute {
                        try {
                            handle(socket)
                        } finally {
                            sockets.remove(socket)
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    sockets.remove(socket)
                    socket.close()
                }
            } catch (_: IOException) {
                if (!server.isClosed) close()
            }
        }
    }

    private fun handle(socket: Socket) = socket.use {
        try {
            if (!socket.inetAddress.isSiteLocalAddress) return@use
            socket.soTimeout = 1200
            val input = DataInputStream(socket.getInputStream())
            val request = readFrame(input, MAX_REQUEST_BYTES).split('\n')
            val state = snapshot ?: return@use
            if (!isValidRequest(request, state)) return@use

            val now = System.nanoTime()
            peers[request[2]] = Peer(request[3] == "1", request[4] == "1", now, request[5])
            val activePeers = peers.filterValues { now - it.seenAt < PEER_LIFETIME_NANOS }
            val muted = state.muted.filter { it == state.host }.toSet() +
                activePeers.filterValues { it.muted }.keys
            // Guests receive mixed audio; the host measures each incoming leg.
            val speaking = state.speaking - muted
            val response = ConferenceRoomWire.encode(state.copy(muted = muted, speaking = speaking)) +
                INSTRUCTION_SEPARATOR + instruction
            writeFrame(DataOutputStream(socket.getOutputStream()), response)
        } catch (_: IOException) {
            // A peer may disconnect or time out in the middle of a frame.
        }
    }

    private fun isValidRequest(request: List<String>, state: RoomMessage.Snapshot): Boolean =
        request.size == 6 &&
            MessageDigest.isEqual(request[0].toByteArray(), token.toByteArray()) &&
            request[1] == state.roomId &&
            request[2] in state.members && request[2] != state.host &&
            request[3] in setOf("0", "1") && request[4] in setOf("0", "1") &&
            request[5].length <= 80

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        workers.shutdownNow()
    }

    companion object {
        private const val MAX_REQUEST_BYTES = 1024
        private const val MAX_REPLY_BYTES = 8192
        private const val PEER_LIFETIME_NANOS = 2_000_000_000L
        private const val INSTRUCTION_SEPARATOR = "\nLAN/1\n"
        private val endpointPattern = Regex("((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5})/([a-f0-9-]{36})")

        data class Reply(val snapshot: RoomMessage.Snapshot, val instruction: String)

        fun parseEndpoint(endpoint: String): Pair<InetSocketAddress, String>? = runCatching {
            val match = endpointPattern.matchEntire(endpoint) ?: return null
            val host = match.groupValues[1]
            if (host.split('.').any { it.toInt() !in 0..255 || (it.length > 1 && it.startsWith('0')) }) return null
            val address = InetAddress.getByName(host)
            if (!address.isSiteLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return null
            val port = match.groupValues[2].toInt().takeIf { it in 1024..65535 } ?: return null
            InetSocketAddress(address, port) to match.groupValues[3]
        }.getOrNull()

        suspend fun exchange(
            endpoint: String,
            room: String,
            own: String,
            muted: Boolean,
            speaking: Boolean,
            acknowledgement: String
        ): Reply = withContext(Dispatchers.IO) {
            val (address, token) = checkNotNull(parseEndpoint(endpoint)) { "Invalid LAN conference endpoint" }
            Socket().use { socket ->
                socket.connect(address, 650)
                socket.soTimeout = 1000
                val request = listOf(
                    token, room, own, if (muted) "1" else "0", if (speaking) "1" else "0", acknowledgement
                ).joinToString("\n")
                writeFrame(DataOutputStream(socket.getOutputStream()), request)
                val response = readFrame(DataInputStream(socket.getInputStream()), MAX_REPLY_BYTES)
                val parts = response.split(INSTRUCTION_SEPARATOR, limit = 2)
                val state = checkNotNull(ConferenceRoomWire.decode(parts[0]) as? RoomMessage.Snapshot)
                Reply(state, parts.getOrElse(1) { "" })
            }
        }

        private fun readFrame(input: DataInputStream, maximumSize: Int): String {
            val size = input.readInt()
            if (size !in 1..maximumSize) throw IOException("Invalid room frame size")
            val bytes = ByteArray(size)
            input.readFully(bytes)
            return bytes.toString(Charsets.UTF_8)
        }

        private fun writeFrame(output: DataOutputStream, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
    }
}
