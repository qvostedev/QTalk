package com.qvoste.qtalk.voip

import java.nio.file.Path
import javax.sound.sampled.*

/** Scale the source PCM exactly once, including zero, independently of MASTER_GAIN support. */
internal fun openRingtonePreview(source: Path, volume: Float, clipFactory: () -> Clip = AudioSystem::getClip): PreviewPlayback {
    val clip = clipFactory()
    try {
        AudioSystem.getAudioInputStream(source.toFile()).use { input ->
            val format = input.format
            require(format.encoding == AudioFormat.Encoding.PCM_SIGNED && format.sampleSizeInBits == 16)
            val bytes = input.readBytes()
            scalePcm16(bytes, volume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f, format.isBigEndian)
            clip.open(format, bytes, 0, bytes.size)
        }
        return object : PreviewPlayback {
            override fun start(onEnd: () -> Unit) {
                clip.addLineListener { if (it.type == LineEvent.Type.STOP) onEnd() }
                clip.start()
            }
            override fun close() { clip.stop(); clip.close() }
        }
    } catch (error: Exception) { clip.close(); throw error }
}
