package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import org.linphone.core.*
import java.nio.file.Files
import javax.sound.sampled.*
import kotlin.test.*

/** Synthetic file endpoints on loopback; never opens physical capture/playback devices. */
class NativeAudioGainTest {
    @Test fun nativePolicyRestoresUnityZeroManualMuteHoldAndStagedLeg() {
        val resources = LinphoneResourceInstaller.install()
        val factory = Factory.instance().apply {
            topResourcesDir = resources.share.toString()
            liblinphonePluginsDir = resources.liblinphonePlugins.toString()
            mspluginsDir = resources.mediastreamerPlugins.toString()
        }
        val temp = Files.createTempDirectory("qtalk-audio3-native-")
        val wave = temp.resolve("silence.wav")
        AudioInputStream(ByteArray(16000 * 2 * 3).inputStream(), AudioFormat(16000f, 16, 1, true, false), 48000).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, wave.toFile())
        }
        val cores = mutableListOf<Core>()
        fun endpoint(name: String): Core = isolatedNativeCore(factory).apply {
            cores += this
            useFiles = true; playFile = wave.toString(); recordFile = temp.resolve("$name.wav").toString()
            isIpv6Enabled = false; audioPort = -1
            setTransports(transports.apply { udpPort = -1; tcpPort = 0; tlsPort = 0 })
            assertEquals(0, start())
            primaryContact = "sip:$name@127.0.0.1:${transportsUsed.udpPort}"
            val account = createAccount(createAccountParams().apply {
                identityAddress = factory.createAddress(primaryContact)
                serverAddress = factory.createAddress("sip:127.0.0.1:${transportsUsed.udpPort}"); isRegisterEnabled = false
            })
            addAccount(account); defaultAccount = account
            addListener(object : CoreListenerStub() {
                override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                    if (state == Call.State.IncomingReceived) call.accept()
                }
            })
        }
        try {
            val a = endpoint("audio3a"); val b = endpoint("audio3b")
            val call = assertNotNull(a.inviteAddressWithParams(factory.createAddress("sip:audio3b@127.0.0.1:${b.transportsUsed.udpPort}"),
                assertNotNull(a.createCallParams(null)).apply { isVideoEnabled = false }))
            val deadline = System.nanoTime() + 5_000_000_000
            while (call.state != Call.State.StreamsRunning && System.nanoTime() < deadline) {
                cores.forEach(Core::iterate); Thread.sleep(10)
            }
            assertEquals(Call.State.StreamsRunning, call.state)
            fun apply(levels: GainLevels, mic: Boolean = false, speaker: Boolean = false, held: Boolean = false,
                staged: Set<Call> = emptySet(), conference: Conference? = null) =
                applyAudioGain(a, conference, levels, mic, speaker, held, staged)
            apply(GainLevels())
            assertEquals(0f, a.micGainDb); assertEquals(0f, a.playbackGainDb)
            apply(GainLevels(0f, 0f))
            assertFalse(a.isMicEnabled); assertTrue(call.microphoneMuted); assertTrue(call.speakerMuted)
            apply(GainLevels(), mic = true, speaker = true)
            assertTrue(call.microphoneMuted); assertTrue(call.speakerMuted)
            apply(GainLevels(), held = true)
            assertTrue(call.microphoneMuted); assertTrue(call.speakerMuted)
            apply(GainLevels(), staged = setOf(call))
            assertTrue(call.microphoneMuted); assertTrue(call.speakerMuted)
            apply(GainLevels())
            assertTrue(a.isMicEnabled); assertFalse(call.microphoneMuted); assertFalse(call.speakerMuted)
            apply(GainLevels(.5f, .5f, 6f))
            assertEquals(AudioGain.db(.5f) + 6f, a.micGainDb, .001f)
            assertEquals(AudioGain.db(.5f), a.playbackGainDb, .001f)
            val conference = assertNotNull(a.createConferenceWithParams(a.createConferenceParams(null).apply {
                isAudioEnabled = true; isVideoEnabled = false; isLocalParticipantEnabled = true
                isOneParticipantConferenceEnabled = true; account = a.defaultAccount; conferenceFactoryAddress = null
            }))
            assertEquals(0, conference.addParticipant(call)); assertEquals(0, conference.enter())
            apply(GainLevels(0f, 0f), conference = conference)
            assertTrue(conference.microphoneMuted); assertTrue(call.speakerMuted)
            apply(GainLevels(), mic = true, conference = conference)
            assertTrue(conference.microphoneMuted)
            apply(GainLevels(), conference = conference)
            assertFalse(conference.microphoneMuted)
        } finally { cores.forEach { it.stop() } }
    }
}
