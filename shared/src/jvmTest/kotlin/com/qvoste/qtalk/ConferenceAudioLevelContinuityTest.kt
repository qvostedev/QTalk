package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import org.linphone.core.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.sin
import kotlin.test.*

/** Host's own capture/playback controls must not silence the other conference participants. */
class ConferenceAudioLevelContinuityTest {
    @Test fun hostMuteAndZeroLevelsPreserveRemoteToRemoteAudio() {
        val resources = LinphoneResourceInstaller.install()
        val factory = Factory.instance().apply {
            topResourcesDir = resources.share.toString()
            liblinphonePluginsDir = resources.liblinphonePlugins.toString()
            mspluginsDir = resources.mediastreamerPlugins.toString()
        }
        val host = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.first { it is Inet4Address && it.isSiteLocalAddress }.hostAddress
        val temp = Files.createTempDirectory("qtalk-conference-levels-")
        val cores = mutableListOf<Core>()
        fun wave(name: String, frequency: Double): String {
            val samples = 16000 * 30
            val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            bytes.putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            bytes.put("data".toByteArray()).putInt(samples * 2)
            repeat(samples) { bytes.putShort((12000 * sin(2 * Math.PI * frequency * it / 16000)).toInt().toShort()) }
            return temp.resolve("$name.wav").also { Files.write(it, bytes.array()) }.toString()
        }
        fun peer(number: String, frequency: Double): Core = isolatedNativeCore(factory).apply {
            cores += this
            useFiles = true; playFile = wave(number, frequency); audioPort = -1; isIpv6Enabled = false
            setTransports(transports.apply { udpPort = -1; tcpPort = 0; tlsPort = 0 })
            assertEquals(0, start()); maxCalls = 7
            primaryContact = "sip:$number@$host:${transportsUsed.udpPort}"
            val account = createAccount(createAccountParams().apply {
                identityAddress = factory.createAddress(primaryContact)
                serverAddress = factory.createAddress("sip:$host:${transportsUsed.udpPort}")
                isRegisterEnabled = false
            })
            addAccount(account); defaultAccount = account
            addListener(object : CoreListenerStub() {
                override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
                    if (state == Call.State.IncomingReceived) call.acceptWithParams(core.createCallParams(call)!!.apply { isVideoEnabled = false })
                }
            })
        }
        fun pump(milliseconds: Long) {
            val until = System.nanoTime() + milliseconds * 1_000_000
            while (System.nanoTime() < until) { cores.forEach(Core::iterate); Thread.sleep(10) }
        }
        fun await(label: String, condition: () -> Boolean) {
            val until = System.nanoTime() + 8_000_000_000L
            while (!condition() && System.nanoTime() < until) pump(20)
            assertTrue(condition(), label)
        }
        try {
            val a = peer("801", 0.0); val b = peer("802", 440.0); val c = peer("803", 660.0)
            val routing = LanHandoffSip(a, factory)
            val local = routing.endpoint(host)
            val mixerAccount = routing.account("801", local)
            val toB = LanSipEndpoint.parse("$host:${b.transportsUsed.udpPort}")!!
            val firstAccount = routing.account("801", local, toB)
            val first = a.inviteAddressWithParams(factory.createAddress(b.primaryContact), routing.params(firstAccount))!!
            await("first call ready") { first.state == Call.State.StreamsRunning }
            val mixer = routing.mixer(mixerAccount)
            assertEquals(0, mixer.addParticipant(first)); assertEquals(0, mixer.enter())
            val second = routing.invite(mixer, mixerAccount, "803", LanSipEndpoint.parse("$host:${c.transportsUsed.udpPort}")!!,
                "11111111-1111-1111-1111-111111111111")
            await("second call ready") { second.state == Call.State.StreamsRunning }
            val heardB = b.calls.single(); val heardC = c.calls.single()
            await("remote participants initially hear each other") { heardB.playVolume > -60f && heardC.playVolume > -60f }
            fun assertRelay(label: String, levels: GainLevels, manualMic: Boolean = false) {
                applyAudioGain(a, mixer, levels, manualMic, false, false, emptySet())
                pump(900)
                assertTrue(heardB.playVolume > -60f, "$label: B lost C (${heardB.playVolume})")
                assertTrue(heardC.playVolume > -60f, "$label: C lost B (${heardC.playVolume})")
            }
            assertRelay("host input zero", GainLevels(0f, 1f))
            assertRelay("host manual mic mute", GainLevels(), manualMic = true)
            assertRelay("host playback zero", GainLevels(1f, 0f))
        } finally { cores.reversed().forEach { it.stop() } }
    }
}
