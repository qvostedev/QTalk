package com.qvoste.qtalk.ui.calls.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant

interface CallHistoryStore {
    fun load(): List<HistoryItem>
    fun save(history: List<HistoryItem>)
}

class InMemoryCallHistoryStore(
    initialHistory: List<HistoryItem> = emptyList()
) : CallHistoryStore {
    private var history = initialHistory.toList()

    override fun load(): List<HistoryItem> = history

    override fun save(history: List<HistoryItem>) {
        this.history = history.toList()
    }
}

internal fun formatHistoryTimestamp(timestamp: Long, nowTimestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val zone = TimeZone.currentSystemDefault()
    val value = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    val now = Instant.fromEpochMilliseconds(nowTimestamp).toLocalDateTime(zone)
    val time = "${value.hour.twoDigits()}:${value.minute.twoDigits()}"
    return if (value.date == now.date) time
    else "${value.day.twoDigits()}.${(value.month.ordinal + 1).twoDigits()}.${value.year}  $time"
}

internal fun historyDateHeading(timestamp: Long, nowTimestamp: Long): String {
    if (timestamp <= 0L) return "Дата неизвестна"
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(nowTimestamp).toLocalDateTime(zone).date
    val daysAgo = today.toEpochDays() - date.toEpochDays()
    if (daysAgo == 0L) return "Сегодня"
    if (daysAgo == 1L) return "Вчера"
    val weekday = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Понедельник"
        DayOfWeek.TUESDAY -> "Вторник"
        DayOfWeek.WEDNESDAY -> "Среда"
        DayOfWeek.THURSDAY -> "Четверг"
        DayOfWeek.FRIDAY -> "Пятница"
        DayOfWeek.SATURDAY -> "Суббота"
        DayOfWeek.SUNDAY -> "Воскресенье"
    }
    val label = "$weekday ${date.day.twoDigits()}.${date.monthNumber.twoDigits()}"
    return if (date.year == today.year) label else "$label.${date.year}"
}

internal fun formatCallDuration(seconds: Long?): String = when {
    seconds == null -> ""
    seconds <= 0L -> "Без ответа"
    seconds < 60L -> "$seconds сек"
    seconds % 60L == 0L -> "${seconds / 60L} мин"
    else -> "${seconds / 60L} мин ${seconds % 60L} сек"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
