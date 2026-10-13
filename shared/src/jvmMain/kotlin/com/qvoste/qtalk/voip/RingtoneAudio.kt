package com.qvoste.qtalk.voip

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.roundToInt

/** Linphone no longer applies [sound]/ring_level, so attenuate the ringtone WAV itself. */
internal fun writeRingtoneAtVolume(source: Path, destination: Path, volume: Float) {
    AudioSystem.getAudioInputStream(source.toFile()).use { input ->
        val format = input.format
        require(format.encoding == AudioFormat.Encoding.PCM_SIGNED && format.sampleSizeInBits == 16) {
            "Ringtone must be a 16-bit PCM WAV"
        }
        val bytes = input.readBytes()
        scalePcm16(bytes, volume.coerceIn(0f, 1f), format.isBigEndian)
        Files.createDirectories(destination.parent)
        AudioInputStream(ByteArrayInputStream(bytes), format, bytes.size.toLong() / format.frameSize).use { output ->
            check(AudioSystem.write(output, AudioFileFormat.Type.WAVE, destination.toFile()) > 0)
        }
    }
}

internal fun isPcm16Wav(source: Path): Boolean = runCatching {
    if (!Files.isRegularFile(source)) return@runCatching false
    AudioSystem.getAudioInputStream(source.toFile()).use { input ->
        val format = input.format
        format.encoding == AudioFormat.Encoding.PCM_SIGNED && format.sampleSizeInBits == 16
    }
}.getOrDefault(false)

internal fun scalePcm16(bytes: ByteArray, volume: Float, bigEndian: Boolean = false) {
    for (index in 0 until bytes.size - 1 step 2) {
        val low = bytes[index + if (bigEndian) 1 else 0].toInt() and 0xff
        val high = bytes[index + if (bigEndian) 0 else 1].toInt()
        val sample = (high shl 8 or low).toShort().toInt()
        val scaled = (sample * volume).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        bytes[index + if (bigEndian) 1 else 0] = scaled.toByte()
        bytes[index + if (bigEndian) 0 else 1] = (scaled shr 8).toByte()
    }
}
