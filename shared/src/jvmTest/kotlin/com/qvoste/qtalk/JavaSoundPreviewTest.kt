package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import java.lang.reflect.Proxy
import java.nio.file.Files
import javax.sound.sampled.*
import kotlin.test.*

class JavaSoundPreviewTest {
    @Test fun previewScalesOriginalPcmOnceWithoutHardwareGainIncludingZeroAndClosesAtEof() {
        val source = Files.createTempFile("qtalk-preview-pcm-", ".wav")
        try {
            val format = AudioFormat(16000f, 16, 1, true, false)
            val samples = byteArrayOf(0x10, 0x27, 0xf0.toByte(), 0xd8.toByte())
            AudioInputStream(samples.inputStream(), format, 2).use { AudioSystem.write(it, AudioFileFormat.Type.WAVE, source.toFile()) }
            for ((volume, expected) in listOf(0f to byteArrayOf(0, 0, 0, 0),
                .5f to byteArrayOf(0x88.toByte(), 0x13, 0x78, 0xec.toByte()), 1f to samples)) {
                var pcm: ByteArray? = null
                var listener: LineListener? = null
                var closes = 0
                var starts = 0
                val clip = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Clip::class.java)) { _, method, args ->
                    when (method.name) {
                        "open" -> { pcm = (args[1] as ByteArray).copyOf(); null }
                        "addLineListener" -> { listener = args[0] as LineListener; null }
                        "start" -> { starts++; null }
                        "stop" -> null
                        "close" -> { closes++; null }
                        else -> error("Preview must not rely on hardware controls: ${method.name}")
                    }
                } as Clip
                val playback = openRingtonePreview(source, volume) { clip }
                assertContentEquals(expected, pcm)
                playback.start { playback.close() }
                assertEquals(1, starts); assertEquals(0, closes)
                assertNotNull(listener).update(LineEvent(clip, LineEvent.Type.STOP, 2))
                assertEquals(1, closes)
            }
        } finally { Files.deleteIfExists(source) }
    }

    @Test fun bundledSourcesRetainFullFourSecondFiles() {
        for (name in listOf("warm", "round")) {
            javaClass.classLoader.getResourceAsStream("ringtones/$name.wav")!!.buffered().use { stream ->
                AudioSystem.getAudioInputStream(stream).use { input ->
                    assertTrue(input.frameLength / input.format.frameRate > 4f)
                }
            }
        }
    }
}
