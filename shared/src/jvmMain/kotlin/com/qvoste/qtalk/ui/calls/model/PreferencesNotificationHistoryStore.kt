package com.qvoste.qtalk.ui.calls.model

import java.util.prefs.Preferences

internal actual fun notificationHistoryStore(): NotificationHistoryStore = PreferencesNotificationHistoryStore()

class PreferencesNotificationHistoryStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/notification-history")
) : NotificationHistoryStore {
    @Synchronized
    override fun load(): List<SavedNotification> {
        val count = preferences.getInt("count", 0).coerceIn(0, MAX_NOTIFICATION_HISTORY)
        return (0 until count).mapNotNull { index ->
            val prefix = "$index."
            val category = runCatching {
                NotificationCategory.valueOf(preferences.get(prefix + "category", "UPDATE"))
            }.getOrNull() ?: return@mapNotNull null
            val id = preferences.get(prefix + "id", "")
            if (id.isBlank()) return@mapNotNull null
            SavedNotification(
                id = id,
                timestamp = preferences.getLong(prefix + "time", 0),
                category = category,
                title = preferences.get(prefix + "title", ""),
                description = (0 until DESCRIPTION_CHUNKS).joinToString("") {
                    preferences.get(prefix + "description.$it", "")
                },
                version = preferences.get(prefix + "version", ""),
                read = preferences.getBoolean(prefix + "read", false),
                deleted = preferences.getBoolean(prefix + "deleted", false)
            )
        }
    }

    @Synchronized
    override fun save(items: List<SavedNotification>) {
        val previousCount = preferences.getInt("count", 0).coerceIn(0, MAX_NOTIFICATION_HISTORY)
        val history = items.take(MAX_NOTIFICATION_HISTORY)
        history.forEachIndexed { index, notification ->
            val prefix = "$index."
            preferences.put(prefix + "id", notification.id.take(200))
            preferences.putLong(prefix + "time", notification.timestamp)
            preferences.put(prefix + "category", notification.category.name)
            preferences.put(prefix + "title", notification.title.take(500))
            // Preferences values have a byte limit; split release notes into small chunks.
            val chunks = notification.description.take(MAX_NOTIFICATION_DESCRIPTION_LENGTH).chunked(DESCRIPTION_CHUNK_SIZE)
            repeat(DESCRIPTION_CHUNKS) { part ->
                preferences.put(prefix + "description.$part", chunks.getOrElse(part) { "" })
            }
            preferences.put(prefix + "version", notification.version.take(200))
            preferences.putBoolean(prefix + "read", notification.read)
            preferences.putBoolean(prefix + "deleted", notification.deleted)
        }
        for (index in history.size until previousCount) {
            val prefix = "$index."
            FIELDS.forEach { preferences.remove(prefix + it) }
            repeat(DESCRIPTION_CHUNKS) { preferences.remove(prefix + "description.$it") }
        }
        preferences.putInt("count", history.size)
        preferences.flush()
    }

    private companion object {
        const val DESCRIPTION_CHUNK_SIZE = 1000
        const val DESCRIPTION_CHUNKS = MAX_NOTIFICATION_DESCRIPTION_LENGTH / DESCRIPTION_CHUNK_SIZE
        val FIELDS = listOf("id", "time", "category", "title", "version", "read", "deleted")
    }
}
