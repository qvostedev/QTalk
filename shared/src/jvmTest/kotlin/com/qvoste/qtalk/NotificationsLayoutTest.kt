package com.qvoste.qtalk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.renderComposeScene
import com.qvoste.qtalk.ui.calls.NotificationsSection
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.theme.*
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Clock

@OptIn(ExperimentalComposeUiApi::class)
class NotificationsLayoutTest {
    @Test fun renderPersistedNotificationsInBothThemes() {
        val now = Clock.System.now().toEpochMilliseconds()
        val notifications = listOf(SavedNotification("update:2", now, NotificationCategory.UPDATE,
            "Доступно обновление", "Changes", "2.0.0"), SavedNotification("network", now - 86_400_000,
            NotificationCategory.SYSTEM, "Нет связи с телефонией", "Reconnect"))
        ThemeMode.entries.forEach { mode ->
            val image = renderComposeScene(1000, 860) {
                CallsTheme(mode) {
                    Box(Modifier.fillMaxSize().background(AppBackground)) {
                    NotificationsSection(notifications, {}, Modifier, false, emptyList(), {}, {}, {}, false, {}, {},
                        "", {}, FocusRequester(), "2.0.0", {})
                    }
                }
            }
            val output = Path.of("build", "notification-previews", "$mode.png")
            Files.createDirectories(output.parent)
            Files.write(output, checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            image.close()
        }
    }
}
