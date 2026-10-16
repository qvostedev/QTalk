package com.qvoste.qtalk.ui.calls.model

import java.util.prefs.Preferences

class PreferencesCallHistoryStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/callHistory")
) : CallHistoryStore {

    @Synchronized
    override fun load(): List<HistoryItem> {
        val count = preferences.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        return buildList(count) {
            repeat(count) { index ->
                val prefix = "$index."
                val id = preferences.get(prefix + "id", "").trim()
                val number = preferences.get(prefix + "number", "").trim()
                val startedAt = preferences.getLong(prefix + "startedAt", 0L)
                if (id.isBlank() || number.isBlank() || startedAt <= 0L) return@repeat
                add(
                    HistoryItem(
                        id = id,
                        name = preferences.get(prefix + "name", number),
                        number = number,
                        direction = preferences.get(prefix + "direction", "Исходящий"),
                        startedAtEpochMillis = startedAt,
                        telegram = preferences.get(prefix + "telegram", ""),
                        missed = preferences.getBoolean(prefix + "missed", false),
                        missedAcknowledged = preferences.getBoolean(prefix + "missedAcknowledged", false),
                        durationSeconds = preferences.getLong(prefix + "durationSeconds", -1L)
                            .takeIf { it >= 0L }
                    )
                )
            }
        }
    }

    @Synchronized
    override fun save(history: List<HistoryItem>) {
        val previousCount = preferences.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        history.forEachIndexed { index, item ->
            val prefix = "$index."
            preferences.put(prefix + "id", item.id)
            preferences.put(prefix + "name", item.name)
            preferences.put(prefix + "number", item.number)
            preferences.put(prefix + "direction", item.direction)
            preferences.putLong(prefix + "startedAt", item.startedAtEpochMillis)
            preferences.put(prefix + "telegram", item.telegram)
            preferences.putBoolean(prefix + "missed", item.missed)
            preferences.putBoolean(prefix + "missedAcknowledged", item.missedAcknowledged)
            preferences.putLong(prefix + "durationSeconds", item.durationSeconds ?: -1L)
        }
        for (index in history.size until previousCount) {
            val prefix = "$index."
            FIELDS.forEach { preferences.remove(prefix + it) }
        }
        preferences.putInt(KEY_COUNT, history.size)
        preferences.flush()
    }

    private companion object {
        const val KEY_COUNT = "count"
        val FIELDS = listOf(
            "id", "name", "number", "direction", "startedAt", "telegram", "missed",
            "missedAcknowledged", "durationSeconds"
        )
    }
}
