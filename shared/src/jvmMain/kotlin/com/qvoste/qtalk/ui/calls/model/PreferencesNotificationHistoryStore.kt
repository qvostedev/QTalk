package com.qvoste.qtalk.ui.calls.model

import java.util.prefs.Preferences

internal actual fun notificationHistoryStore(): NotificationHistoryStore = PreferencesNotificationHistoryStore()

class PreferencesNotificationHistoryStore(private val preferences: Preferences =
    Preferences.userRoot().node("com/qvoste/qtalk/notification-history")) : NotificationHistoryStore {
    @Synchronized override fun load(): List<SavedNotification> = (0 until preferences.getInt("count", 0).coerceIn(0, 200)).mapNotNull { index ->
        runCatching {
            val p = "$index."
            SavedNotification(preferences.get(p + "id", ""), preferences.getLong(p + "time", 0),
                NotificationCategory.valueOf(preferences.get(p + "category", "UPDATE")),
                preferences.get(p + "title", ""), (0 until 16).joinToString("") { preferences.get(p + "description.$it", "") },
                preferences.get(p + "version", ""), preferences.getBoolean(p + "read", false), preferences.getBoolean(p + "deleted", false))
        }.getOrNull()?.takeIf { it.id.isNotBlank() }
    }
    @Synchronized override fun save(items: List<SavedNotification>) {
        val bounded = items.take(200)
        bounded.forEachIndexed { index, item ->
            val p = "$index."
            preferences.put(p + "id", item.id.take(200)); preferences.putLong(p + "time", item.timestamp)
            preferences.put(p + "category", item.category.name); preferences.put(p + "title", item.title.take(500))
            val chunks = item.description.take(16000).chunked(1000)
            repeat(16) { preferences.put(p + "description.$it", chunks.getOrElse(it) { "" }) }
            preferences.put(p + "version", item.version.take(200)); preferences.putBoolean(p + "read", item.read)
            preferences.putBoolean(p + "deleted", item.deleted)
        }
        preferences.putInt("count", bounded.size); preferences.flush()
    }
}
