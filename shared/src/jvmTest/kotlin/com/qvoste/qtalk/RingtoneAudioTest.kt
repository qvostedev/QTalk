package com.qvoste.qtalk

import com.qvoste.qtalk.voip.scalePcm16
import com.qvoste.qtalk.voip.isPcm16Wav
import com.qvoste.qtalk.voip.writeRingtoneAtVolume
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingtoneAudioTest {
    @Test
    fun bundledFallbackIsA16BitWave() {
        val resource = "assets/org.linphone.core/share/sounds/linphone/rings/oldphone-mono.wav"
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream(resource))
        AudioSystem.getAudioInputStream(stream.buffered()).use {
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, it.format.encoding)
            assertEquals(16, it.format.sampleSizeInBits)
        }
    }

    @Test
    fun attenuatesSignedSamplesWithoutChangingTheirSign() {
        val samples = byteArrayOf(0x10, 0x27, 0xf0.toByte(), 0xd8.toByte())
        scalePcm16(samples, 0.5f)
        assertContentEquals(byteArrayOf(0x88.toByte(), 0x13, 0x78, 0xec.toByte()), samples)
        scalePcm16(samples, 0f)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), samples)
    }

    @Test
    fun writesPlayableWaveAtSelectedVolume() {
        val source = Files.createTempFile("qtalk-ring-source", ".wav")
        val output = Files.createTempFile("qtalk-ring-output", ".wav")
        try {
            val format = AudioFormat(8000f, 16, 1, true, false)
            val samples = byteArrayOf(0x10, 0x27, 0xf0.toByte(), 0xd8.toByte())
            AudioInputStream(ByteArrayInputStream(samples), format, 2).use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, source.toFile())
            }
            assertTrue(isPcm16Wav(source))
            writeRingtoneAtVolume(source, output, 0.5f)
            AudioSystem.getAudioInputStream(output.toFile()).use {
                assertContentEquals(byteArrayOf(0x88.toByte(), 0x13, 0x78, 0xec.toByte()), it.readBytes())
            }
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(source)
        }
    }

    @Test
    fun rejectsUnsupportedRingtoneWithoutInterruptingStartup() {
        val source = Files.createTempFile("qtalk-ring-unsupported", ".mkv")
        try {
            Files.write(source, byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()))
            assertFalse(isPcm16Wav(source))
        } finally {
            Files.deleteIfExists(source)
        }
    }
}
