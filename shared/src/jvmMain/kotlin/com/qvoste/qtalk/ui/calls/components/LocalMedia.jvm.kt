package com.qvoste.qtalk.ui.calls.components

import com.qvoste.qtalk.voip.RingtoneInfo
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_FILE_BYTES = 20_000_000L
private const val MAX_IMAGE_DIMENSION = 8192
private const val MAX_IMAGE_PIXELS = 32_000_000L
private const val AVATAR_SIZE = 512
private const val RINGTONE_SAMPLE_RATE = 16000
private const val MAX_RINGTONE_SECONDS = 60
private const val MAX_RINGTONE_PCM_BYTES = RINGTONE_SAMPLE_RATE * 2 * MAX_RINGTONE_SECONDS

internal fun localMediaDirectory(): Path = Path.of(System.getProperty("user.home"), ".qtalk", "media")

internal actual suspend fun importLocalMedia(avatar: Boolean): String? {
    val source = suspendCancellableCoroutine<Path?> { continuation ->
        EventQueue.invokeLater {
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
            val title = if (avatar) "Выберите аватар (PNG, JPEG)" else "Выберите рингтон (WAV, MP3)"
            val dialog = FileDialog(owner, title, FileDialog.LOAD)
            continuation.invokeOnCancellation { EventQueue.invokeLater { dialog.dispose() } }
            try {
                if (continuation.isActive) dialog.isVisible = true
                if (continuation.isActive) {
                    continuation.resume(dialog.file?.let { Path.of(dialog.directory, it) })
                }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                dialog.dispose()
            }
        }
    } ?: return null
    return importLocalMediaFile(source, avatar)
}

internal suspend fun importLocalMediaFile(
    source: Path,
    avatar: Boolean,
    storage: Path = localMediaDirectory()
): String = withContext(Dispatchers.IO) {
    require(Files.isRegularFile(source)) { "Выберите файл" }
    require(Files.size(source) in 1..MAX_FILE_BYTES) { "Размер файла должен быть не больше 20 МБ" }

    val directory = Files.createDirectories(storage.resolve(if (avatar) "avatars" else "ringtones"))
    val filename = UUID.randomUUID().toString() + if (avatar) ".png" else ".wav"
    val destination = directory.resolve(filename)
    val labelFile = destination.resolveSibling("$filename.name")
    try {
        if (avatar) {
            writeAvatar(source, destination)
            kotlin.coroutines.coroutineContext.ensureActive()
            destination.toUri().toString()
        } else {
            writeRingtone(source, destination)
            val label = source.fileName.toString().substringBeforeLast('.').take(100)
            Files.writeString(labelFile, label)
            kotlin.coroutines.coroutineContext.ensureActive()
            "custom-" + filename.removeSuffix(".wav")
        }
    } catch (error: Exception) {
        runCatching { Files.deleteIfExists(destination) }
        runCatching { Files.deleteIfExists(labelFile) }
        throw error
    }
}

private fun writeAvatar(source: Path, destination: Path) {
    ImageIO.createImageInputStream(source.toFile()).use { stream ->
        val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull()
            ?: error("Неподдерживаемый формат изображения")
        try {
            reader.input = stream
            val width = reader.getWidth(0)
            val height = reader.getHeight(0)
            require(width in 1..MAX_IMAGE_DIMENSION && height in 1..MAX_IMAGE_DIMENSION &&
                width.toLong() * height <= MAX_IMAGE_PIXELS) { "Слишком большое изображение" }

            val image = reader.read(0)
            val side = minOf(width, height)
            val left = (width - side) / 2
            val top = (height - side) / 2
            val result = BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB)
            val graphics = result.createGraphics()
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.drawImage(image, 0, 0, AVATAR_SIZE, AVATAR_SIZE, left, top, left + side, top + side, null)
            } finally {
                graphics.dispose()
            }
            check(ImageIO.write(result, "png", destination.toFile()))
        } finally {
            reader.dispose()
        }
    }
}

private fun writeRingtone(source: Path, destination: Path) {
    AudioSystem.getAudioInputStream(source.toFile()).use { audio ->
        val sourceFormat = audio.format
        val decodedFormat = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, sourceFormat.sampleRate, 16, sourceFormat.channels,
            sourceFormat.channels * 2, sourceFormat.sampleRate, false
        )
        AudioSystem.getAudioInputStream(decodedFormat, audio).use { decoded ->
            val format = AudioFormat(RINGTONE_SAMPLE_RATE.toFloat(), 16, 1, true, false)
            AudioSystem.getAudioInputStream(format, decoded).use { pcm ->
                // MP3 streams may have no frame count; enforce the limit on decoded PCM.
                val samples = pcm.readNBytes(MAX_RINGTONE_PCM_BYTES + format.frameSize)
                require(samples.isNotEmpty() && samples.size <= MAX_RINGTONE_PCM_BYTES) {
                    "Рингтон должен быть короче минуты"
                }
                AudioInputStream(samples.inputStream(), format, samples.size.toLong() / format.frameSize).use {
                    AudioSystem.write(it, AudioFileFormat.Type.WAVE, destination.toFile())
                }
            }
        }
    }
}

internal actual fun customRingtones(): List<RingtoneInfo> {
    val directory = localMediaDirectory().resolve("ringtones")
    if (!Files.isDirectory(directory)) return emptyList()
    return Files.list(directory).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".wav") }
            .sorted()
            .map { path ->
                val filename = path.fileName.toString()
                val label = runCatching {
                    Files.readString(path.resolveSibling("$filename.name")).take(100)
                }.getOrDefault("Свой рингтон")
                RingtoneInfo("custom-" + filename.removeSuffix(".wav"), label)
            }.toList()
    }
}
