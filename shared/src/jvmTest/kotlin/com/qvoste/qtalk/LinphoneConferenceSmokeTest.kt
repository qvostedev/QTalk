package com.qvoste.qtalk

import com.qvoste.qtalk.voip.LinphoneResourceInstaller
import org.linphone.core.Factory
import org.linphone.core.Core
import org.linphone.core.Call
import org.linphone.core.CoreListenerStub
import java.nio.file.Files
import java.nio.file.Path
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
        try {
            fun endpoint(name: String): Core {
                val core = factory.createCoreWithConfig(factory.createConfigFromString(""), null)
                cores += core
                core.useFiles = true
                core.audioPort = -1
                val transports = core.transports.apply { udpPort = -1; tcpPort = 0; tlsPort = 0 }
                assertEquals(0, core.setTransports(transports))
                assertEquals(0, core.start())
                core.maxCalls = 2
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
                    override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                        if (state == Call.State.IncomingReceived) assertEquals(0, call.accept())
                    }
                })
                return core
            }
            val core = endpoint("conference-host")
            val firstPeer = endpoint("first-peer")
            val secondPeer = endpoint("second-peer")
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
            assertEquals(0, first.pause())
            await("First call must be paused") { first.state == Call.State.Paused }
            val second = dial(secondPeer)
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
            assertEquals(0, conference.addParticipant(second))
            assertEquals(0, conference.enter())
            await("Both calls must be running in the local conference") {
                first.state == Call.State.StreamsRunning && second.state == Call.State.StreamsRunning &&
                    first.conference == conference && second.conference == conference
            }
            assertEquals(2, conference.participantCount)
            conference.participantList.forEach { participant -> participant.devices.forEach { device ->
                device.getIsSpeaking()
                device.getIsMuted()
            } }
            assertEquals(0, first.terminate())
            await("Removing one participant must leave the other call connected") {
                conference.participantCount == 1 && second.state == Call.State.StreamsRunning
            }
            assertEquals(0, conference.terminate())
        } finally {
            cores.reversed().forEach { it.stop() }
        }
    }
}
