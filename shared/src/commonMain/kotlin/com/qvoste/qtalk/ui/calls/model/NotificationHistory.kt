package com.qvoste.qtalk.ui.calls.model

internal const val MAX_NOTIFICATION_HISTORY = 200
internal const val MAX_NOTIFICATION_DESCRIPTION_LENGTH = 16_000

enum class NotificationCategory { UPDATE, SYSTEM }

data class SavedNotification(
    val id: String,
    val timestamp: Long,
    val category: NotificationCategory,
    val title: String,
    val description: String,
    val version: String = "",
    val read: Boolean = false,
    val deleted: Boolean = false
)

interface NotificationHistoryStore {
    fun load(): List<SavedNotification>
    fun save(items: List<SavedNotification>)
}

internal expect fun notificationHistoryStore(): NotificationHistoryStore

internal fun List<SavedNotification>.withUpdate(
    version: String,
    description: String,
    now: Long
): List<SavedNotification> {
    val id = "update:$version"
    if (any { it.id == id }) {
        return map { notification ->
            if (notification.id == id && !notification.deleted && description.isNotBlank()) {
                notification.copy(description = description)
            } else {
                notification
            }
        }
    }
    val notification = SavedNotification(id, now, NotificationCategory.UPDATE, "Доступно обновление", description, version)
    return (listOf(notification) + this).take(MAX_NOTIFICATION_HISTORY)
}
