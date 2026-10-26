package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.components.importLocalMediaFile
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.net.URI
import javax.imageio.ImageIO
import kotlin.test.*

class LocalMediaTest {
    @Test fun rejectsOverlongRingtoneWithoutKeepingPartialFiles(): Unit = runBlocking {
        val directory = Files.createTempDirectory("qtalk-long-ringtone-")
        val source = directory.resolve("long.wav")
        val storage = directory.resolve("stored")
        val format = javax.sound.sampled.AudioFormat(16000f, 16, 1, true, false)
        val samples = ByteArray(16000 * 2 * 61)
        javax.sound.sampled.AudioInputStream(samples.inputStream(), format, samples.size.toLong() / 2).use {
            javax.sound.sampled.AudioSystem.write(it, javax.sound.sampled.AudioFileFormat.Type.WAVE, source.toFile())
        }
        assertFails { importLocalMediaFile(source, false, storage) }
        assertEquals(0L, Files.list(storage.resolve("ringtones")).use { it.count() })
    }

    @Test fun decodesMp3FramesToManagedWav(): Unit = runBlocking {
        val directory = Files.createTempDirectory("qtalk-mp3-test-")
        val source = directory.resolve("silence.mp3")
        // MPEG-1 Layer III, 128 kbit/s, 44.1 kHz; empty side information
        // represents silent granules. No external music or encoder is required.
        val frame = ByteArray(417).apply {
            this[0] = 0xff.toByte(); this[1] = 0xfb.toByte(); this[2] = 0x90.toByte()
        }
        Files.newOutputStream(source).use { output -> repeat(40) { output.write(frame) } }
        val id = importLocalMediaFile(source, false, directory.resolve("stored"))
        val wav = directory.resolve("stored/ringtones/${id.removePrefix("custom-")}.wav")
        javax.sound.sampled.AudioSystem.getAudioInputStream(wav.toFile()).use {
            assertEquals(16000f, it.format.sampleRate)
            assertEquals(1, it.format.channels)
            assertEquals(16, it.format.sampleSizeInBits)
            assertTrue(it.frameLength > 0)
        }
    }

    @Test fun importsIndependentSquareAvatarAndRejectsInvalidImage(): Unit = runBlocking {
        val directory = Files.createTempDirectory("qtalk-media-test-")
        val source = directory.resolve("source.png")
        ImageIO.write(BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", source.toFile())
        val imported = importLocalMediaFile(source, true, directory.resolve("stored"))
        Files.delete(source)
        val destination = java.nio.file.Path.of(URI(imported))
        val image = ImageIO.read(destination.toFile())
        assertEquals(512, image.width); assertEquals(512, image.height)
        val invalid = directory.resolve("invalid.png")
        Files.writeString(invalid, "not an image")
        assertFails { importLocalMediaFile(invalid, true, directory.resolve("stored")) }
        assertEquals(1L, Files.list(destination.parent).use { it.count() })
    }
}
