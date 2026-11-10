package com.qvoste.qtalk.support

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal fun safeScreenshotFilename(name: String): String = name.filter { !it.isISOControl() }.trim().take(180)
internal fun normalizeScreenshot(filename: String, bytes: ByteArray): SupportAttachment {
    try {
        if (bytes.size !in 8..4 * 1024 * 1024) throw IllegalArgumentException()
        val png = bytes.take(8) == listOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 13.toByte(), 10.toByte(), 26.toByte(), 10.toByte())
        val jpeg = bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        if (!png && !jpeg) throw IllegalArgumentException()
        javax.imageio.stream.MemoryCacheImageInputStream(bytes.inputStream()).use { stream ->
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: throw IllegalArgumentException()
            try {
                reader.input = stream
                val width = reader.getWidth(0); val height = reader.getHeight(0)
                if (width !in 1..16384 || height !in 1..16384 || width.toLong() * height > 16_000_000) throw IllegalArgumentException()
                val decoded = reader.read(0)
                val stripped = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                stripped.createGraphics().let { graphics -> try { graphics.drawImage(decoded, 0, 0, null) } finally { graphics.dispose() } }
                val output = ByteArrayOutputStream()
                ImageIO.write(stripped, "png", output)
                if (output.size() > 4 * 1024 * 1024) throw IllegalArgumentException()
                return SupportAttachment(safeScreenshotFilename(filename), output.toByteArray())
            } finally { reader.dispose() }
        }
    } catch (_: Exception) { throw SupportFailure(SupportError.INVALID_IMAGE) }
}

internal suspend fun chooseSupportFile(): Path? = suspendCancellableCoroutine { continuation ->
    EventQueue.invokeLater {
        if (!continuation.isActive) return@invokeLater
        val dialog = FileDialog(null as Frame?, "QTalk — PNG / JPEG", FileDialog.LOAD)
        continuation.invokeOnCancellation { EventQueue.invokeLater { dialog.dispose() } }
        try {
            dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") } }
            dialog.isVisible = true
            val selected = dialog.file?.let { Path.of(dialog.directory, it) }
            if (continuation.isActive) continuation.resume(selected)
        } finally { dialog.dispose() }
    }
}

