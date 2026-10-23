package com.qvoste.qtalk

import com.qvoste.qtalk.voip.LinphoneResourceInstaller
import org.linphone.core.Factory
import org.linphone.core.Core
import org.linphone.core.Call
import org.linphone.core.CoreListenerStub
import org.linphone.core.InfoMessage
import com.qvoste.qtalk.voip.ConferenceRoomWire
import com.qvoste.qtalk.voip.RoomMessage
import com.qvoste.qtalk.voip.isSpeechLevel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinphoneConferenceSmokeTest {
    @Test fun nativeSdkMergesTwoEstablishedCallsWithoutServer() {
        val originalTemp = System.getProperty("java.io.tmpdir")
        val testTemp = Files.createDirectories(Path.of(originalTemp, "qtalk-conference-sdk-test"))
        System.setProperty("java.io.tmpdir", testTemp.toString())
        try {
            checkLocalConference()
        } finally {
            System.setProperty("java.io.tmpdir", originalTemp)
        }
    }

    private fun checkLocalConference() {
        val paths = LinphoneResourceInstaller.install()
        val factory = Factory.instance().apply {
            topResourcesDir = paths.share.toString()
            liblinphonePluginsDir = paths.liblinphonePlugins.toString()
            mspluginsDir = paths.mediastreamerPlugins.toString()
        }
        val cores = mutableListOf<Core>()
        val receivedRooms = mutableMapOf<Core, RoomMessage>()
        val autoAnswer = mutableSetOf<Core>()
        val tone = Files.createTempFile("qtalk-conference-tone-", ".wav")
        val sampleRate = 16000
        val sampleCount = sampleRate * 30
        val wav = ByteBuffer.allocate(44 + sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + sampleCount * 2).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(sampleCount * 2)
        repeat(sampleCount) { wav.putShort((12000 * sin(2 * Math.PI * 440 * it / sampleRate)).toInt().toShort()) }
        Files.write(tone, wav.array())
        try {
            fun endpoint(name: String): Core {
                val core = factory.createCoreWithConfig(factory.createConfigFromString(""), null)
                cores += core
                core.useFiles = true
                core.playFile = tone.toString()
                core.audioPort = -1
                val transports = core.transports.apply { udpPort = -1; tcpPort = 0; tlsPort = 0 }
                assertEquals(0, core.setTransports(transports))
                assertEquals(0, core.start())
                core.maxCalls = 3
                val identity = "sip:$name@127.0.0.1:${core.transportsUsed.udpPort}"
                core.primaryContact = identity
                val accountParams = core.createAccountParams().apply {
                    identityAddress = factory.createAddress(identity)
                    serverAddress = factory.createAddress("sip:127.0.0.1")
                    isRegisterEnabled = false
                }
                val account = core.createAccount(accountParams)
                assertEquals(0, core.addAccount(account))
                core.defaultAccount = account
                core.addListener(object : CoreListenerStub() {
                    override fun onInfoReceived(core: Core, call: Call, message: InfoMessage) {
                        message.content?.utf8Text?.let(ConferenceRoomWire::decode)?.let { receivedRooms[core] = it }
                    }
                    override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                        if (state == Call.State.IncomingReceived && core in autoAnswer) assertEquals(0, call.accept())
                    }
                })
                return core
            }
            val core = endpoint("101")
            val firstPeer = endpoint("102")
            val secondPeer = endpoint("103")
            val thirdPeer = endpoint("104")
            autoAnswer += firstPeer
            fun await(description: String, condition: () -> Boolean) {
                val deadline = System.nanoTime() + 10_000_000_000L
                while (!condition() && System.nanoTime() < deadline) {
                    cores.forEach(Core::iterate)
                    Thread.sleep(10)
                }
                assertTrue(condition(), description)
            }
            fun dial(peer: Core): Call {
                val callParams = assertNotNull(core.createCallParams(null)).apply { isVideoEnabled = false }
                val call = assertNotNull(core.inviteAddressWithParams(factory.createAddress(peer.primaryContact)!!, callParams))
                await("Call must establish: ${call.state}") { call.state == Call.State.StreamsRunning }
                return call
            }
            val first = dial(firstPeer)
            val params = core.createConferenceParams(null).apply {
                isAudioEnabled = true
                isVideoEnabled = false
                isLocalParticipantEnabled = true
                isOneParticipantConferenceEnabled = true
                account = core.defaultAccount
                conferenceFactoryAddress = null
            }
            assertNull(params.conferenceFactoryAddress)
            assertTrue(params.isLocalParticipantEnabled)
            val conference = assertNotNull(core.createConferenceWithParams(params))
            assertEquals(0, conference.addParticipant(first))
            assertEquals(0, conference.enter())
            await("First participant must already have an active mixer") {
                first.state == Call.State.StreamsRunning && conference.isIn
            }
            await("First participant must receive host audio before another answers") {
                firstPeer.calls.single().playVolume > -60f
            }
            await("Host must receive first participant's audio before another answers") { first.playVolume > -60f }
            fun inviteWhileTalking(peer: Core, existing: List<Call>): Call {
                val before = core.calls.toSet()
                conference.inviteParticipants(arrayOf(factory.createAddress(peer.primaryContact)!!),
                    assertNotNull(core.createCallParams(null)).apply { isVideoEnabled = false })
                val invited = assertNotNull(core.calls.firstOrNull { it !in before })
                await("Next participant must ring without holding existing calls") { invited.state == Call.State.OutgoingRinging }
                repeat(100) {
                    cores.forEach(Core::iterate)
                    assertTrue(conference.isIn, "Host must remain connected to the mixer while inviting")
                    assertTrue(existing.all { it.state == Call.State.StreamsRunning }, "Established calls must not be held")
                    Thread.sleep(10)
                }
                assertTrue(firstPeer.calls.single().playVolume > -60f, "Host audio must continue while the next participant rings")
                assertTrue(first.playVolume > -60f, "Guest audio must continue while the next participant rings")
                assertEquals(0, peer.calls.single().accept())
                await("Invited call must join the same mixer after answering") {
                    invited.state == Call.State.StreamsRunning && invited.conference == conference
                }
                return invited
            }
            val second = inviteWhileTalking(secondPeer, listOf(first))
            await("Both calls must be running in the local conference") {
                first.state == Call.State.StreamsRunning && second.state == Call.State.StreamsRunning &&
                    first.conference == conference && second.conference == conference
            }
            assertEquals(2, conference.participantCount)
            val third = inviteWhileTalking(thirdPeer, listOf(first, second))
            await("Existing participants must stay connected when another joins") {
                listOf(first, second, third).all { it.state == Call.State.StreamsRunning }
            }
            assertEquals(3, conference.participantCount)
            firstPeer.isMicEnabled = false
            repeat(120) { cores.forEach(Core::iterate); Thread.sleep(10) }
            assertTrue(!isSpeechLevel(first.playVolume), "Muted participant/comfort noise must not be shown as speech: ${first.playVolume}")
            assertTrue(isSpeechLevel(second.playVolume), "Second participant must remain active")
            assertTrue(isSpeechLevel(third.playVolume), "Third participant must remain active")
            firstPeer.isMicEnabled = true
            await("Speech must resume after unmuting") { isSpeechLevel(first.playVolume) }
            assertEquals(0, conference.leave())
            repeat(100) {
                cores.forEach(Core::iterate)
                assertTrue(listOf(first, second, third).all { it.state == Call.State.StreamsRunning },
                    "Holding the host must not hold other participants")
                Thread.sleep(10)
            }
            assertTrue(firstPeer.calls.single().playVolume > -60f, "Guests must keep hearing each other while the host is held")
            assertEquals(0, conference.enter())
            val snapshot = RoomMessage.Snapshot("12345678-1234-1234-1234-123456789abc", 1, "101",
                listOf("101", "102", "103", "104"), emptyList(), setOf("102"), emptySet())
            for (call in listOf(first, second, third)) {
                val content = factory.createContent().apply {
                    type = "application"; subtype = ConferenceRoomWire.subtype; utf8Text = ConferenceRoomWire.encode(snapshot)
                }
                assertEquals(0, call.sendInfoMessage(core.createInfoMessage().apply { this.content = content }))
            }
            await("Both remote clients must receive the same room roster over SIP INFO") {
                receivedRooms[firstPeer] == snapshot && receivedRooms[secondPeer] == snapshot && receivedRooms[thirdPeer] == snapshot
            }
            val microphone = RoomMessage.Microphone(snapshot.roomId, true)
            val microphoneContent = factory.createContent().apply {
                type = "application"; subtype = ConferenceRoomWire.subtype; utf8Text = ConferenceRoomWire.encode(microphone)
            }
            assertEquals(0, firstPeer.calls.single().sendInfoMessage(firstPeer.createInfoMessage().apply {
                content = microphoneContent
            }))
            await("The host must receive a guest microphone update") { receivedRooms[core] == microphone }
            conference.participantList.forEach { participant -> participant.devices.forEach { device ->
                device.getIsSpeaking()
                device.getIsMuted()
            } }
            assertEquals(0, first.terminate())
            await("Removing one participant must leave the other call connected") {
                conference.participantCount == 2 && second.state == Call.State.StreamsRunning
            }
            assertEquals(0, third.terminate())
            await("The mixer must retain its final participant") { conference.participantCount == 1 }
            assertEquals(0, conference.enter())
            await("The last pair must retain two-way audio when the UI returns to ordinary mode") {
                second.state == Call.State.StreamsRunning && second.playVolume > -60f && secondPeer.calls.single().playVolume > -60f
            }
            assertEquals(0, conference.terminate())
            await("Old conference legs must be released before the successor redials") {
                secondPeer.calls.isEmpty() && thirdPeer.calls.isEmpty()
            }
            autoAnswer += thirdPeer
            val successorParams = assertNotNull(secondPeer.createCallParams(null)).apply { isVideoEnabled = false }
            val successorCall = assertNotNull(secondPeer.inviteAddressWithParams(factory.createAddress(thirdPeer.primaryContact)!!, successorParams))
            await("The two remaining participants must establish an ordinary call on the successor") {
                successorCall.state == Call.State.StreamsRunning && thirdPeer.calls.singleOrNull()?.state == Call.State.StreamsRunning
            }
            assertNull(successorCall.conference)
            await("Audio must work in both directions after successor redial") {
                successorCall.playVolume > -60f && thirdPeer.calls.single().playVolume > -60f
            }
            assertEquals(0, successorCall.terminate())
        } finally {
            cores.reversed().forEach { it.stop() }
            Files.deleteIfExists(tone)
        }
    }
}
