package com.qvoste.qtalk.ui.calls.model

enum class NotificationCategory { UPDATE, SYSTEM }
data class SavedNotification(val id: String, val timestamp: Long, val category: NotificationCategory,
    val title: String, val description: String, val version: String = "", val read: Boolean = false,
    val deleted: Boolean = false)

interface NotificationHistoryStore {
    fun load(): List<SavedNotification>
    fun save(items: List<SavedNotification>)
}
internal expect fun notificationHistoryStore(): NotificationHistoryStore

internal fun List<SavedNotification>.withUpdate(version: String, description: String, now: Long): List<SavedNotification> {
    val id = "update:$version"
    return if (any { it.id == id }) map { if (it.id == id && !it.deleted && description.isNotBlank()) it.copy(description = description) else it }
    else (listOf(SavedNotification(id, now, NotificationCategory.UPDATE, "Доступно обновление", description, version)) + this).take(200)
}
