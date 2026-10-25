package com.qvoste.qtalk.ui.calls.model

private object SessionNotificationHistory : NotificationHistoryStore {
    private var items = emptyList<SavedNotification>()
    override fun load() = items
    override fun save(items: List<SavedNotification>) { this.items = items.take(200) }
}
internal actual fun notificationHistoryStore(): NotificationHistoryStore = SessionNotificationHistory
