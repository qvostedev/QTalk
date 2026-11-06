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
        val acknowledgement: String,
        val capability: String = "",
        val sipEndpoint: LanSipEndpoint? = null
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
    @Volatile var handoffEndpoints: Map<String, LanSipEndpoint> = emptyMap()

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
            if (!socket.inetAddress.isSiteLocalAddress) { return@use }
            socket.soTimeout = ConferenceHandoffTiming.CONTROL_REQUEST_TIMEOUT_MILLIS
            val input = DataInputStream(socket.getInputStream())
            val request = readFrame(input, MAX_REQUEST_BYTES).split('\n')
            val state = snapshot ?: return@use
            if (!isValidRequest(request, state)) { return@use }
            val capability = request.getOrElse(6) { "" }
            val advertised = request.getOrElse(7) { "" }
            val sipEndpoint = if (advertised.isEmpty()) null else LanSipEndpoint.parse(advertised)
            // An endpoint is authoritative only when its host matches this authenticated
            // room-capability connection's observed source. No arbitrary third-party targets.
            if (advertised.isNotEmpty() && (sipEndpoint == null || sipEndpoint.host != socket.inetAddress.hostAddress)) { return@use }
            val previous = peers[request[2]]
            if (previous?.sipEndpoint != null && previous.sipEndpoint != sipEndpoint && handoffEndpoints.isNotEmpty()) { return@use }

            val now = System.nanoTime()
            peers.keys.retainAll(state.members.toSet())
            peers[request[2]] = Peer(request[3] == "1", request[4] == "1", now, request[5], capability, sipEndpoint)
            val activePeers = peers.filterValues { now - it.seenAt < PEER_LIFETIME_NANOS }
            val muted = state.muted.filter { it == state.host }.toSet() +
                activePeers.filterValues { it.muted }.keys
            // Guests receive mixed audio; the host measures each incoming leg.
            val speaking = state.speaking - muted
            val endpoints = handoffEndpoints.ifEmpty {
                activePeers.mapNotNull { (number, peer) ->
                    peer.sipEndpoint?.takeIf { peer.capability == CAPABILITY }?.let { number to it }
                }.toMap()
            }
            val response = ConferenceRoomWire.encode(state.copy(muted = muted, speaking = speaking)) +
                if (capability == CAPABILITY) INSTRUCTION_SEPARATOR + instruction + "\n" +
                    endpoints.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
                else "\nLAN/1\n" // Legacy peers receive room state, never handoff commands.
            writeFrame(DataOutputStream(socket.getOutputStream()), response)
        } catch (_: IOException) {
        }
    }

    private fun isValidRequest(request: List<String>, state: RoomMessage.Snapshot): Boolean =
        request.size in setOf(6, 8) &&
            (request.size == 6 || request[6] == CAPABILITY) &&
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
        private const val PEER_LIFETIME_NANOS = ConferenceHandoffTiming.PEER_LIFETIME_NANOS
        const val CAPABILITY = "LAN2"
        private const val INSTRUCTION_SEPARATOR = "\nLAN/2\n"
        private val endpointPattern = Regex("((?:[0-9]{1,3}\\.){3}[0-9]{1,3}):([0-9]{1,5})/([a-f0-9-]{36})")

        data class Reply(val snapshot: RoomMessage.Snapshot, val instruction: String,
            val endpoints: Map<String, LanSipEndpoint> = emptyMap())

        fun decodeReply(response: String): Reply? {
            if (response.length > MAX_REPLY_BYTES) return null
            val parts = response.split(INSTRUCTION_SEPARATOR, limit = 2)
            if (parts.size != 2) return null // Explicitly refuse the old protocol.
            val state = ConferenceRoomWire.decode(parts[0]) as? RoomMessage.Snapshot ?: return null
            val lines = parts[1].split('\n')
            if (lines.isEmpty() || lines[0].length > 512 || lines.size > MAX_CONFERENCE_PARTICIPANTS + 2) return null
            val endpoints = linkedMapOf<String, LanSipEndpoint>()
            for (line in lines.drop(1).filter { it.isNotBlank() }) {
                val entry = line.split('=')
                if (entry.size != 2 || entry[0] !in state.members || entry[0] == state.host || entry[0] in endpoints) return null
                endpoints[entry[0]] = LanSipEndpoint.parse(entry[1]) ?: return null
            }
            return Reply(state, lines[0], endpoints)
        }

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
            acknowledgement: String,
            sipEndpoint: LanSipEndpoint? = null
        ): Reply = withContext(Dispatchers.IO) {
            val (address, token) = checkNotNull(parseEndpoint(endpoint)) { "Invalid LAN conference endpoint" }
            Socket().use { socket ->
                socket.connect(address, ConferenceHandoffTiming.CONTROL_CONNECT_TIMEOUT_MILLIS)
                socket.soTimeout = ConferenceHandoffTiming.CONTROL_READ_TIMEOUT_MILLIS
                val request = listOf(
                    token, room, own, if (muted) "1" else "0", if (speaking) "1" else "0", acknowledgement, CAPABILITY, sipEndpoint?.toString().orEmpty()
                ).joinToString("\n")
                writeFrame(DataOutputStream(socket.getOutputStream()), request)
                val response = readFrame(DataInputStream(socket.getInputStream()), MAX_REPLY_BYTES)
                checkNotNull(decodeReply(response)) { "LAN2 update required" }
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
