package com.qvoste.qtalk.ui.calls.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.qvoste.qtalk.ui.calls.model.normalizeTelegramInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@Composable
internal actual fun rememberTelegramAvatar(telegram: String, avatarUrl: String, pixelSize: Int): ImageBitmap? {
    val username = normalizeTelegramInput(telegram).removePrefix("@").lowercase()
    val source = avatarUrl.trim().ifBlank { username }
    val memoryKey = "$source#$pixelSize"
    val avatar = remember(memoryKey) { mutableStateOf(avatarMemoryCache[memoryKey]) }
    LaunchedEffect(memoryKey) {
        if (source.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            // Show the last good image immediately, including when it needs refreshing.
            val cached = avatarMemoryCache[memoryKey] ?: runCatching {
                avatarLoader(username, avatarUrl).readCached()?.let { decodeAvatar(it, pixelSize) }
            }.getOrNull()
            if (cached != null) {
                avatarMemoryCache[memoryKey] = cached
                avatar.value = cached
            }
            runCatching {
                val loader = avatarLoader(username, avatarUrl)
                // Reuse the decoded bitmap on navigation; only expired files need a refresh.
                if (cached != null && loader.isFresh()) cached
                else decodeAvatar(loader.load(), pixelSize)
            }
                .onSuccess {
                    avatarMemoryCache[memoryKey] = it
                    avatar.value = it
                }
        }
    }
    return avatar.value
}

private fun decodeAvatar(bytes: ByteArray, pixelSize: Int): ImageBitmap {
    val original = ImageIO.read(ByteArrayInputStream(bytes))
        ?: return Image.makeFromEncoded(bytes).toComposeImageBitmap()
    return downsampleAvatar(original, pixelSize).toComposeImageBitmap()
}

/** Progressive reduction avoids aliasing and loss of detail when drawing a 320px photo at 46px. */
internal fun downsampleAvatar(original: BufferedImage, pixelSize: Int): BufferedImage {
    val target = pixelSize.coerceIn(1, 4096)
    val ratio = minOf(1.0, target.toDouble() / minOf(original.width, original.height))
    val width = (original.width * ratio).toInt().coerceAtLeast(1)
    val height = (original.height * ratio).toInt().coerceAtLeast(1)
    var image = original
    while (image.width > width || image.height > height) {
        val next = BufferedImage(maxOf(width, image.width / 2), maxOf(height, image.height / 2),
            BufferedImage.TYPE_INT_ARGB)
        val graphics = next.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(image, 0, 0, next.width, next.height, null)
        } finally {
            graphics.dispose()
        }
        if (image !== original) image.flush()
        image = next
    }
    return image
}

private fun avatarLoader(username: String, avatarUrl: String): AvatarDiskCache {
    val directUrl = avatarUrl.trim()
    val source = directUrl.ifBlank { username }
    val cacheName = if (directUrl.isBlank()) "$username.img" else
        MessageDigest.getInstance("SHA-256").digest(directUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".img"
    return AvatarDiskCache(telegramAvatarCacheDirectory().resolve(cacheName), source, validate = ::validateAvatar) {
        val image = if (directUrl.startsWith("file:")) {
            val path = Path.of(URI(directUrl)).toAbsolutePath().normalize()
            require(path.startsWith(localMediaDirectory().resolve("avatars").toAbsolutePath().normalize()))
            require(Files.size(path) <= MAX_IMAGE_BYTES)
            Files.readAllBytes(path)
        } else if (directUrl.isNotBlank()) {
            download(directUrl, MAX_IMAGE_BYTES)
        } else {
            downloadTelegramAvatar(username)
        }
        image
    }
}

private fun validateAvatar(bytes: ByteArray) {
    Image.makeFromEncoded(bytes).use {
        check(it.width in 1..4096 && it.height in 1..4096) { "Размер аватара слишком большой" }
    }
}

private fun downloadTelegramAvatar(username: String): ByteArray {
    check(username.matches(Regex("[a-z0-9_]{1,32}"))) { "Некорректный Telegram username" }
    val profileHtml = download("https://t.me/$username", MAX_HTML_BYTES).toString(Charsets.UTF_8)
    val avatarUrl = findOpenGraphImage(profileHtml)
        ?: error("У профиля @$username нет публичного аватара")
    check(!avatarUrl.contains("/img/t_logo", ignoreCase = true)) {
        "У профиля @$username нет публичного аватара"
    }
    return download(avatarUrl, MAX_IMAGE_BYTES)
}

private fun download(url: String, maximumBytes: Int): ByteArray {
    var uri = URI(url)
    repeat(6) {
        check(uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null) { "Некорректная HTTPS-ссылка на аватар" }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 QTalk/1.0")
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                uri = uri.resolve(checkNotNull(connection.getHeaderField("Location")))
                return@repeat
            }
            check(status in 200..299) { "Сервер аватара вернул HTTP $status" }
            val bytes = connection.inputStream.use { it.readNBytes(maximumBytes + 1) }
            check(bytes.size <= maximumBytes) { "Файл аватара слишком большой" }
            return bytes
        } finally {
            connection.disconnect()
        }
    }
    error("Слишком много перенаправлений аватара")
}

private fun findOpenGraphImage(html: String): String? {
    val propertyFirst = Regex(
        """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    val contentFirst = Regex(
        """<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""",
        RegexOption.IGNORE_CASE
    )
    return (propertyFirst.find(html) ?: contentFirst.find(html))
        ?.groupValues?.get(1)
        ?.replace("&amp;", "&")
}

private fun telegramAvatarCacheDirectory(): Path {
    val localAppData = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)
    if (localAppData != null) return Path.of(localAppData, "QTalk", "avatar-cache")

    val xdgCache = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
    if (xdgCache != null) return Path.of(xdgCache, "QTalk", "avatar-cache")

    return Path.of(System.getProperty("user.home"), ".cache", "QTalk", "avatar-cache")
}

private val avatarMemoryCache = ConcurrentHashMap<String, ImageBitmap>()
private const val MAX_HTML_BYTES = 1_000_000
private const val MAX_IMAGE_BYTES = 5_000_000

/** The source URL is part of the key, so changing a contact photo never reuses another URL's image. */
internal class AvatarDiskCache(
    private val file: Path,
    private val source: String,
    private val now: () -> Instant = Instant::now,
    private val validate: (ByteArray) -> Unit = {},
    private val download: () -> ByteArray
) {
    fun isFresh(): Boolean = runCatching {
        Files.isRegularFile(file) &&
            Duration.between(Files.getLastModifiedTime(file).toInstant(), now()) < Duration.ofDays(7)
    }.getOrDefault(false)

    fun readCached(): ByteArray? = runCatching {
        if (Files.isRegularFile(file) && Files.size(file) in 1..MAX_IMAGE_BYTES.toLong()) {
            Files.readAllBytes(file).also(validate)
        } else null
    }.getOrNull()

    fun load(): ByteArray = synchronized(locks.computeIfAbsent(source) { Any() }) {
        val cached = readCached()
        val fresh = cached != null && isFresh()
        if (fresh) return@synchronized checkNotNull(cached)
        val bytes = try {
            download().also {
                check(it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES)
                validate(it)
            }
        } catch (error: Exception) {
            return@synchronized cached ?: throw error
        }
        // A read-only/full cache must not prevent displaying a successfully downloaded image.
        runCatching {
            Files.createDirectories(file.parent)
            val temporary = Files.createTempFile(file.parent, "avatar-", ".tmp")
            try {
                Files.write(temporary, bytes)
                try {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        bytes
    }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
    }
}
