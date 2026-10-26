package com.qvoste.qtalk

import com.qvoste.qtalk.voip.LinphoneResourceInstaller
import com.qvoste.qtalk.voip.SpeechActivityDetector
import org.linphone.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*

/** Real RTP and native mixer, without SIP accounts, microphones or external calls. */
class NativeSpeechActivityTest {
    @Test fun independentInputsDetectBurstsAndReturnToSilence() {
        val paths = LinphoneResourceInstaller.install()
        val factory = Factory.instance().apply {
            topResourcesDir = paths.share.toString()
            liblinphonePluginsDir = paths.liblinphonePlugins.toString()
            mspluginsDir = paths.mediastreamerPlugins.toString()
        }
        val temp = Files.createTempDirectory("qtalk-speech-rtp-")
        val cores = mutableListOf<Core>()
        fun wave(name: String, start: Double): String {
            val rate = 16000; val count = rate * 9; val random = Random(name.hashCode())
            val data = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
            data.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
            data.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            data.put("data".toByteArray()).putInt(count * 2)
            repeat(count) { i ->
                val t = i.toDouble() / rate
                val burst = t >= start && t < start + 1.0
                val envelope = if (burst) 0.55 + 0.45 * sin(2 * Math.PI * 6 * t) else 0.0
                val value = random.nextInt(-1200, 1201) + 10000 * envelope * sin(2 * Math.PI * 220 * t)
                data.putShort(value.toInt().toShort())
            }
            val file = temp.resolve(name); Files.write(file, data.array()); return file.toString()
        }
        fun endpoint(name: String, source: String): Core {
            val core = factory.createCoreWithConfig(factory.createConfigFromString(""), null)
            cores += core
            core.useFiles = true; core.playFile = source; core.audioPort = -1
            core.isIpv6Enabled = false
            core.setTransports(core.transports.apply { udpPort = -1; tcpPort = 0; tlsPort = 0 })
            assertEquals(0, core.start()); core.maxCalls = 4
            val identity = "sip:$name@127.0.0.1:${core.transportsUsed.udpPort}"
            core.primaryContact = identity
            val params = core.createAccountParams().apply {
                identityAddress = factory.createAddress(identity)
                serverAddress = factory.createAddress("sip:127.0.0.1"); isRegisterEnabled = false
            }
            val account = core.createAccount(params); core.addAccount(account); core.defaultAccount = account
            core.addListener(object : CoreListenerStub() {
                override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                    if (state == Call.State.IncomingReceived) call.accept()
                }
            })
            return core
        }
        fun pump(ms: Long) {
            val end = System.nanoTime() + ms * 1_000_000
            while (System.nanoTime() < end) { cores.forEach(Core::iterate); Thread.sleep(10) }
        }
        try {
            val host = endpoint("301", wave("host.wav", 99.0))
            val a = endpoint("302", wave("first.wav", 2.0))
            val b = endpoint("303", wave("second.wav", 3.5))
            fun waitConnected(call: Call) {
                val deadline = System.nanoTime() + 5_000_000_000L
                while (call.state != Call.State.StreamsRunning && System.nanoTime() < deadline) pump(10)
                assertEquals(Call.State.StreamsRunning, call.state)
            }
            val first = assertNotNull(host.inviteAddressWithParams(factory.createAddress(a.primaryContact)!!,
                assertNotNull(host.createCallParams(null)).apply { isVideoEnabled = false }))
            waitConnected(first)
            val conference = assertNotNull(host.createConferenceWithParams(host.createConferenceParams(null).apply {
                isAudioEnabled = true; isVideoEnabled = false; isLocalParticipantEnabled = true
                isOneParticipantConferenceEnabled = true; account = host.defaultAccount; conferenceFactoryAddress = null
            }))
            assertEquals(0, conference.addParticipant(first)); assertEquals(0, conference.enter())
            val before = host.calls.toSet()
            conference.inviteParticipants(arrayOf(factory.createAddress(b.primaryContact)!!),
                assertNotNull(host.createCallParams(null)).apply { isVideoEnabled = false })
            val second = assertNotNull(host.calls.firstOrNull { it !in before })
            waitConnected(second)
            val calls = listOf(first, second)
            val detectors = List(2) { SpeechActivityDetector() }
            val seen = BooleanArray(2); val quiet = BooleanArray(2) { true }
            var noisySamplesAboveOldThreshold = 0
            var nextLog = 0.0
            val started = System.nanoTime()
            while (System.nanoTime() - started < 6_500_000_000L) {
                pump(50)
                val now = System.nanoTime(); val elapsed = (now - started) / 1e9
                if (elapsed >= nextLog) { println("levels t=$elapsed: ${calls.map { it.playVolume }}"); nextLog += 0.25 }
                calls.forEachIndexed { index, call ->
                    val level = call.playVolume
                    val active = detectors[index].update(level, now)
                    if (elapsed in 0.6..1.0 && level > -35f) noisySamplesAboveOldThreshold++
                    if (elapsed < 1.0 || elapsed > 5.5) quiet[index] = quiet[index] && !active
                    if (active) seen[index] = true
                    if (index == 1 && elapsed < 2.5) assertFalse(active, "Second input must not light up for first speaker")
                }
            }
            assertTrue(noisySamplesAboveOldThreshold > 0, "Exercise background that triggered the old fixed threshold")
            assertTrue(seen.all { it }, "Both distinct audio bursts must be detected: ${seen.toList()}")
            assertTrue(quiet.all { it }, "Noise before/after the bursts must stay quiet")
        } finally {
            cores.reversed().forEach { it.stop() }
            Files.walk(temp).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
