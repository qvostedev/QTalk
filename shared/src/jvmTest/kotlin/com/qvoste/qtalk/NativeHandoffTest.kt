package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlinx.coroutines.runBlocking
import org.linphone.core.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.UUID
import kotlin.math.sin
import kotlin.test.*

/** Real native SIP/RTP with the production routing, admission, LAN channel, readiness and coordinator seams.
 * Synthetic cores only; no live registration, installed app, microphone, or default native databases.
 */
class NativeHandoffTest {
    @Test fun replacementMixerCanBePreparedBeforeOldLegsEndAndAbortRestoresOriginal(): Unit = runBlocking {
        val paths = LinphoneResourceInstaller.install()
        val factory = Factory.instance().apply {
            topResourcesDir = paths.share.toString(); liblinphonePluginsDir = paths.liblinphonePlugins.toString()
            mspluginsDir = paths.mediastreamerPlugins.toString()
        }
        val host = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.first { it is Inet4Address && it.isSiteLocalAddress }.hostAddress
        val tone = Files.createTempFile("qtalk-handoff-", ".wav")
        val samples = 16000 * 60
        val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { wav.putShort((12000 * sin(2 * Math.PI * 440 * it / 16000)).toInt().toShort()) }
        Files.write(tone, wav.array())
        val cores = mutableListOf<Core>()
        val events = mutableListOf<String>()
        val proxy = DatagramSocket(0, InetAddress.getLoopbackAddress()).apply { soTimeout = 1 }
        var proxyInvites = 0
        var proxyRegisters = 0
        var permit: ConferenceHandoffPermit? = null
        var incoming: Call? = null
        lateinit var guestOriginal: Call
        lateinit var receiverRouting: LanHandoffSip
        lateinit var receiverAccount: Account
        lateinit var successorEndpoint: LanSipEndpoint
        val roomId = UUID.randomUUID().toString()
        try {
            fun peer(number: String): Core = isolatedNativeCore(factory).also { c ->
                cores += c
                c.useFiles = true; c.playFile = tone.toString(); c.audioPort = -1; c.isIpv6Enabled = false
                assertEquals(0, c.setTransports(c.transports.apply { udpPort = -1; tcpPort = -1; tlsPort = 0 }))
                assertEquals(0, c.start()); c.maxCalls = 7
                val identity = "sip:$number@$host:${c.transportsUsed.udpPort}"
                c.primaryContact = identity
                val account = c.createAccount(c.createAccountParams().apply {
                    identityAddress = factory.createAddress(identity)
                    serverAddress = factory.createAddress("sip:$host:${c.transportsUsed.udpPort}")
                    isRegisterEnabled = false
                })
                c.addAccount(account); c.defaultAccount = account
                c.addListener(object : CoreListenerStub() {
                    override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                        events += "$number:$state:${call.remoteAddress?.username}"
                        if (state != Call.State.IncomingReceived) return
                        if (call.remoteAddress?.username == "101") {
                            assertEquals(0, call.acceptWithParams(assertNotNull(c.createCallParams(call)).apply { isVideoEnabled = false }))
                        } else if (number == "103") {
                            val allowed = permit?.let { p -> LanHandoffAdmission.accepts(p,
                                call.remoteAddress?.username.orEmpty(), call.remoteParams?.getCustomHeader("X-QTalk-Handoff"),
                                roomId, System.nanoTime(), incoming != null, guestOriginal in c.calls,
                                call.remoteAddress?.domain, call.remoteContactAddress?.let { LanSipEndpoint.parse("${it.domain}:${it.port}") },
                                successorEndpoint) } == true
                            if (allowed) {
                                incoming = call
                                call.setMicrophoneMuted(true); call.setSpeakerMuted(true)
                                assertEquals(0, call.acceptWithParams(receiverRouting.params(receiverAccount, call)))
                            } else call.decline(Reason.NotAcceptable)
                        } else call.decline(Reason.Busy)
                    }
                })
            }
            fun serviceProxy() {
                while (true) {
                    val packet = DatagramPacket(ByteArray(8192), 8192)
                    try { proxy.receive(packet) } catch (_: SocketTimeoutException) { break }
                    val message = String(packet.data, 0, packet.length)
                    if (message.startsWith("REGISTER ")) proxyRegisters++
                    if (message.startsWith("INVITE ")) proxyInvites++
                    if (!message.startsWith("REGISTER ") && !message.startsWith("INVITE ")) continue
                    val headers = message.split("\r\n").filter { line ->
                        listOf("Via:", "From:", "To:", "Call-ID:", "CSeq:", "Contact:").any { line.startsWith(it) }
                    }
                    val status = if (message.startsWith("REGISTER ")) "200 OK" else "486 Busy Here"
                    val response = (listOf("SIP/2.0 $status") + headers + listOf("Expires: 120", "Content-Length: 0", "", "")).joinToString("\r\n").toByteArray()
                    proxy.send(DatagramPacket(response, response.size, packet.socketAddress))
                }
            }
            fun pump() { cores.forEach(Core::iterate); serviceProxy(); Thread.sleep(10) }
            fun await(message: String, condition: () -> Boolean) {
                val until = System.nanoTime() + 8_000_000_000L
                while (!condition() && System.nanoTime() < until) pump()
                assertTrue(condition(), "$message; events=${events.takeLast(20)}")
            }
            fun dial(from: Core, to: Core): Call {
                val direct = LanHandoffSip(from, factory)
                val account = direct.account(assertNotNull(from.primaryContactParsed.username), direct.endpoint(host),
                    assertNotNull(LanSipEndpoint.parse("$host:${to.transportsUsed.udpPort}")))
                return assertNotNull(from.inviteAddressWithParams(factory.createAddress(to.primaryContact)!!, direct.params(account)))
            }
            val old = peer("101"); val successor = peer("102"); val guest = peer("103")
            val oldRouting = LanHandoffSip(old, factory)
            val a = dial(old, successor)
            await("First original") { a.state == Call.State.StreamsRunning }
            val oldMixer = oldRouting.mixer(assertNotNull(old.defaultAccount))
            assertEquals(0, oldMixer.addParticipant(a)); assertEquals(0, oldMixer.enter())
            val b = dial(old, guest)
            await("Second original") { b.state == Call.State.StreamsRunning }
            assertEquals(0, oldMixer.addParticipant(b))
            await("Original bridge") { a.state == Call.State.StreamsRunning && b.state == Call.State.StreamsRunning }
            val successorOriginal = successor.calls.single(); guestOriginal = guest.calls.single()
            val ordinary = successor.createAccount(successor.createAccountParams().apply {
                identityAddress = factory.createAddress("sip:102@127.0.0.1")
                serverAddress = factory.createAddress("sip:127.0.0.1:${proxy.localPort}")
                isRegisterEnabled = true; isOutboundProxyEnabled = true
            })
            successor.addAccount(ordinary); successor.defaultAccount = ordinary
            await("Synthetic proxy registration") { ordinary.state == org.linphone.core.RegistrationState.Ok }
            assertTrue(proxyRegisters > 0)
            val routing = LanHandoffSip(successor, factory)
            successorEndpoint = routing.endpoint(host)
            val lan = routing.account("102", successorEndpoint)
            receiverRouting = LanHandoffSip(guest, factory)
            val guestEndpoint = receiverRouting.endpoint(host)
            receiverAccount = receiverRouting.account("103", guestEndpoint)
            val initialTransports = successor.transportsUsed
            assertTrue(initialTransports.tcpPort > 0, "LAN setup preserves TCP alongside UDP")
            LanConferenceChannel(InetAddress.getByName(host)).use { channel ->
                channel.snapshot = RoomMessage.Snapshot(roomId, 1, "101", listOf("101", "102", "103"),
                    emptyList(), emptySet(), emptySet(), channel.endpoint)
                val negotiated = mapOf("102" to successorEndpoint, "103" to guestEndpoint)
                suspend fun exchange(number: String, ack: String) = LanConferenceChannel.exchange(channel.endpoint,
                    roomId, number, false, false, ack, negotiated.getValue(number))
                exchange("102", ""); val roster = exchange("103", "").endpoints
                assertEquals(negotiated, roster); channel.handoffEndpoints = roster

                suspend fun transfer(unavailable: Boolean, abortAfterReady: Boolean = false): Pair<Conference, Call> {
                    val id = UUID.randomUUID().toString()
                    val plan = ConferenceHandoff(id, "102", listOf("102", "103"))
                    var staged: Call? = null
                    var mixer: Conference? = null
                    var committing = false
                    var aborting = false
                    val blackhole = DatagramSocket(0, InetAddress.getByName(host))
                    try {
                        plan.transfer(object : ConferenceHandoff.Transport {
                            override fun instruct(instruction: String) {
                                channel.instruction = "LAN2|$instruction"
                                when (instruction.substringBefore('|')) {
                                    "prepare" -> {
                                        incoming = null
                                        permit = ConferenceHandoffPermit(id, "102", roomId, System.nanoTime() + 35_000_000_000L)
                                    }
                                    "connect" -> {
                                        mixer = routing.mixer(lan)
                                        val target = if (unavailable) assertNotNull(LanSipEndpoint.parse("$host:${blackhole.localPort}")) else roster.getValue("103")
                                        staged = routing.invite(assertNotNull(mixer), lan, "103", target, id)
                                        staged!!.setMicrophoneMuted(true); staged!!.setSpeakerMuted(true)
                                        assertEquals(target.port, staged!!.params.account?.params?.serverAddress?.port)
                                        assertEquals(lan, mixer!!.currentParams.account)
                                        assertEquals(ordinary, successor.defaultAccount)
                                    }
                                    "abort" -> {
                                        aborting = true; permit = null
                                        staged?.terminate(); mixer?.terminate()
                                    }
                                    "commit" -> {
                                        committing = true
                                        mixer!!.microphoneMuted = false
                                        staged!!.setMicrophoneMuted(false); staged!!.setSpeakerMuted(false)
                                        incoming!!.setMicrophoneMuted(false); incoming!!.setSpeakerMuted(false)
                                    }
                                }
                            }
                            override suspend fun awaitAcknowledgements(ack: String, timeoutMillis: Long): Boolean {
                                val until = System.nanoTime() + (if (unavailable && ack.startsWith("ready:")) 600 else 5000) * 1_000_000L
                                while (System.nanoTime() < until) {
                                    pump()
                                    if (staged?.state == Call.State.StreamsRunning && !aborting && mixer?.isIn != true) {
                                        if (staged!!.conference != mixer) assertEquals(0, mixer!!.addParticipant(staged!!))
                                        assertEquals(0, mixer!!.enter()); mixer!!.microphoneMuted = true
                                    }
                                    if (aborting && successor.calls.size == 1 && guest.calls.size == 1) {
                                        if (successorOriginal.state == Call.State.Paused) successorOriginal.resume()
                                        if (guestOriginal.state == Call.State.Paused) guestOriginal.resume()
                                    }
                                    val restored = a.state == Call.State.StreamsRunning && b.state == Call.State.StreamsRunning &&
                                        successor.calls.size == 1 && guest.calls.size == 1
                                    val senderReady = LanHandoffSip.ready(listOfNotNull(staged), 1, mixer, true)
                                    val receiverReady = LanHandoffSip.ready(listOfNotNull(incoming), 1, null, false)
                                    val phase = ack.substringBefore(':')
                                    val canAckSender = when (phase) { "prepared" -> permit != null; "ready" -> senderReady
                                        "committed" -> committing && senderReady; "aborted" -> aborting && restored; else -> false }
                                    val canAckReceiver = when (phase) { "prepared" -> permit != null; "ready" -> receiverReady
                                        "committed" -> committing && receiverReady; "aborted" -> aborting && restored; else -> false }
                                    val first = exchange("102", if (canAckSender) ack else "waiting:$id")
                                    val second = exchange("103", if (canAckReceiver) ack else "waiting:$id")
                                    assertEquals(channel.instruction, first.instruction); assertEquals(roster, second.endpoints)
                                    assertTrue(old.calls.contains(a) && old.calls.contains(b), "Original legs retained at every barrier")
                                    assertEquals(0, proxyInvites, "Replacement must never reach the registered proxy")
                                    if (channel.peers.values.count { it.acknowledgement == ack } == 2) {
                                        if (abortAfterReady && ack.startsWith("ready:")) return false
                                        return true
                                    }
                                }
                                return false
                            }
                        })
                    } catch (e: IllegalStateException) {
                        if (!unavailable && !abortAfterReady) throw e
                        assertEquals(ConferenceHandoff.Phase.ABORTED, plan.phase)
                        assertTrue(plan.rollbackAcknowledged)
                        assertEquals(Call.State.StreamsRunning, a.state); assertEquals(Call.State.StreamsRunning, b.state)
                    } finally { blackhole.close() }
                    return assertNotNull(mixer) to assertNotNull(staged)
                }
                transfer(unavailable = true)
                await("Original audio after unavailable-peer rollback") { a.playVolume > -60f && b.playVolume > -60f }
                transfer(unavailable = false, abortAfterReady = true)
                await("Original audio after accepted-leg rollback") { a.playVolume > -60f && b.playVolume > -60f }
                val (replacement, newLeg) = transfer(unavailable = false)
                assertEquals(0, proxyInvites)
                assertEquals(0, a.terminate()); assertEquals(0, b.terminate())
                await("Only old legs end after all commit acknowledgements") { old.calls.isEmpty() && successor.calls.size == 1 && guest.calls.size == 1 }
                val receiving = guest.calls.single()
                val sent = newLeg.audioStats!!.rtpPacketSent; val received = newLeg.audioStats!!.rtpPacketRecv
                val peerSent = receiving.audioStats!!.rtpPacketSent; val peerReceived = receiving.audioStats!!.rtpPacketRecv
                await("Real two-way RTP after old host removal") {
                    newLeg.audioStats!!.rtpPacketSent > sent + 5 && newLeg.audioStats!!.rtpPacketRecv > received + 5 &&
                        receiving.audioStats!!.rtpPacketSent > peerSent + 5 && receiving.audioStats!!.rtpPacketRecv > peerReceived + 5 &&
                        newLeg.playVolume > -60f && receiving.playVolume > -60f
                }
                assertEquals(ordinary, successor.defaultAccount)
                assertEquals(org.linphone.core.RegistrationState.Ok, ordinary.state)
                assertEquals(initialTransports.tcpPort, successor.transportsUsed.tcpPort)
                assertEquals(0, newLeg.terminate()); replacement.terminate()
                await("Replacement releases") { successor.calls.isEmpty() && guest.calls.isEmpty() }
                routing.releaseUnused(); receiverRouting.releaseUnused()
                val normal = assertNotNull(successor.inviteAddressWithParams(factory.createAddress("sip:104@127.0.0.1"),
                    assertNotNull(successor.createCallParams(null)).apply { account = ordinary; isVideoEnabled = false }))
                await("Ordinary call still reaches its registered proxy") { proxyInvites > 0 }
                assertEquals(ordinary, normal.params.account)
            }
        } finally {
            cores.reversed().forEach { it.stop() }; proxy.close(); Files.deleteIfExists(tone)
        }
    }
}
