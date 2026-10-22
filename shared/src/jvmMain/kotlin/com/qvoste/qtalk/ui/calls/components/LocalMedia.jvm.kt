package com.qvoste.qtalk.ui.calls.components

import com.qvoste.qtalk.voip.RingtoneInfo
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioFileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun localMediaDirectory(): Path = Path.of(System.getProperty("user.home"), ".qtalk", "media")

internal actual suspend fun importLocalMedia(avatar: Boolean): String? {
    val source = suspendCancellableCoroutine<Path?> { continuation ->
        java.awt.EventQueue.invokeLater {
            val owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
            val dialog = FileDialog(owner, if (avatar) "Выберите аватар (PNG, JPEG)" else "Выберите рингтон (WAV, MP3)", FileDialog.LOAD)
            continuation.invokeOnCancellation { java.awt.EventQueue.invokeLater { dialog.dispose() } }
            try {
                if (continuation.isActive) dialog.isVisible = true
                if (continuation.isActive) continuation.resume(dialog.file?.let { Path.of(dialog.directory, it) })
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally { dialog.dispose() }
        }
    } ?: return null
    return importLocalMediaFile(source, avatar)
}

internal suspend fun importLocalMediaFile(source: Path, avatar: Boolean, storage: Path = localMediaDirectory()): String =
    withContext(Dispatchers.IO) {
        require(Files.size(source) in 1..20_000_000) { "Размер файла должен быть не больше 20 МБ" }
        val directory = Files.createDirectories(storage.resolve(if (avatar) "avatars" else "ringtones"))
        val destination = directory.resolve(UUID.randomUUID().toString() + if (avatar) ".png" else ".wav")
        try {
            if (avatar) {
                ImageIO.createImageInputStream(source.toFile()).use { stream ->
                    val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: error("Неподдерживаемый формат изображения")
                    try {
                        reader.input = stream
                        val width = reader.getWidth(0); val height = reader.getHeight(0)
                        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 32_000_000) { "Слишком большое изображение" }
                        val image = reader.read(0)
                        val side = minOf(width, height)
                        val result = java.awt.image.BufferedImage(512, 512, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                        result.createGraphics().let { graphics ->
                            try {
                                graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                                graphics.drawImage(image, 0, 0, 512, 512, (width-side)/2, (height-side)/2, (width+side)/2, (height+side)/2, null)
                            } finally { graphics.dispose() }
                        }
                        check(ImageIO.write(result, "png", destination.toFile()))
                    } finally { reader.dispose() }
                }
                destination.toUri().toString()
            } else {
                AudioSystem.getAudioInputStream(source.toFile()).use { audio ->
                    val decodedFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, audio.format.sampleRate,
                        16, audio.format.channels, audio.format.channels * 2, audio.format.sampleRate, false)
                    AudioSystem.getAudioInputStream(decodedFormat, audio).use { decoded ->
                        val format = AudioFormat(16000f, 16, 1, true, false)
                        AudioSystem.getAudioInputStream(format, decoded).use { pcm ->
                            // Compressed streams may not expose a duration until decoded.
                            val samples = pcm.readNBytes(16000 * 2 * 60 + 2)
                            require(samples.isNotEmpty() && samples.size <= 16000 * 2 * 60) { "Рингтон должен быть короче минуты" }
                            javax.sound.sampled.AudioInputStream(samples.inputStream(), format, samples.size.toLong() / 2).use {
                                AudioSystem.write(it, AudioFileFormat.Type.WAVE, destination.toFile())
                            }
                        }
                    }
                }
                Files.writeString(destination.resolveSibling(destination.fileName.toString() + ".name"), source.fileName.toString().substringBeforeLast('.').take(100))
                "custom-" + destination.fileName.toString().removeSuffix(".wav")
            }
        } catch (error: Exception) {
            Files.deleteIfExists(destination)
            throw error
        }
    }
internal actual fun customRingtones(): List<RingtoneInfo> {
    val directory = localMediaDirectory().resolve("ringtones")
    if (!Files.isDirectory(directory)) return emptyList()
    return Files.list(directory).use { paths -> paths.filter { it.fileName.toString().endsWith(".wav") }.sorted().map {
        val id = "custom-" + it.fileName.toString().removeSuffix(".wav")
        RingtoneInfo(id, runCatching { Files.readString(it.resolveSibling(it.fileName.toString() + ".name")).take(100) }.getOrDefault("Свой рингтон"))
    }.toList() }
}
