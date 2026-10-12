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
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Composable
internal actual fun rememberTelegramAvatar(telegram: String): ImageBitmap? {
    val username = normalizeTelegramInput(telegram).removePrefix("@").lowercase()
    val avatar = remember(username) { mutableStateOf(avatarMemoryCache[username]) }
    LaunchedEffect(username) {
        if (username.isBlank() || avatar.value != null) return@LaunchedEffect
        avatar.value = withContext(Dispatchers.IO) {
            runCatching {
                Image.makeFromEncoded(loadTelegramAvatar(username)).toComposeImageBitmap().also {
                    avatarMemoryCache[username] = it
                }
            }.getOrNull()
        }
    }
    return avatar.value
}

private fun loadTelegramAvatar(username: String): ByteArray {
    val lock = avatarLocks.computeIfAbsent(username) { Any() }
    return synchronized(lock) { loadTelegramAvatarLocked(username) }
}

private fun loadTelegramAvatarLocked(username: String): ByteArray {
    val cacheDirectory = telegramAvatarCacheDirectory()
    Files.createDirectories(cacheDirectory)
    val cacheFile = cacheDirectory.resolve("$username.img")
    if (Files.isRegularFile(cacheFile)) {
        val age = Duration.between(Files.getLastModifiedTime(cacheFile).toInstant(), Instant.now())
        if (age < CACHE_LIFETIME) return Files.readAllBytes(cacheFile)
    }

    val profileHtml = download("https://t.me/$username", MAX_HTML_BYTES).toString(Charsets.UTF_8)
    val avatarUrl = findOpenGraphImage(profileHtml)
        ?: error("У профиля @$username нет публичного аватара")
    check(!avatarUrl.contains("/img/t_logo", ignoreCase = true)) {
        "У профиля @$username нет публичного аватара"
    }
    val imageUrl = URI(avatarUrl)
    check(imageUrl.scheme.equals("https", ignoreCase = true)) { "Некорректная ссылка на аватар" }

    val image = download(imageUrl.toString(), MAX_IMAGE_BYTES)
    val temporaryFile = cacheDirectory.resolve("$username.tmp")
    Files.write(temporaryFile, image)
    runCatching {
        Files.move(
            temporaryFile,
            cacheFile,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }.getOrElse {
        Files.move(temporaryFile, cacheFile, StandardCopyOption.REPLACE_EXISTING)
    }
    return image
}

private fun download(url: String, maximumBytes: Int): ByteArray {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 QTalk/1.0")
        val status = connection.responseCode
        check(status in 200..299) { "Telegram вернул ошибку HTTP $status" }
        val bytes = connection.inputStream.use { it.readNBytes(maximumBytes + 1) }
        check(bytes.size <= maximumBytes) { "Файл аватара слишком большой" }
        return bytes
    } finally {
        connection.disconnect()
    }
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

private val CACHE_LIFETIME: Duration = Duration.ofHours(24)
private val avatarLocks = ConcurrentHashMap<String, Any>()
private val avatarMemoryCache = ConcurrentHashMap<String, ImageBitmap>()
private const val MAX_HTML_BYTES = 1_000_000
private const val MAX_IMAGE_BYTES = 5_000_000
