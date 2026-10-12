package com.qvoste.qtalk.ui.calls.model

import androidx.compose.ui.graphics.Color

internal enum class AppSection(val title: String) {
    CALLS("Звонки"),
    CONTACTS("Контакты"),
    SETTINGS("Настройки"),
    NOTIFICATIONS("Уведомления")
}

internal enum class CallFilter(val title: String) {
    ALL("Все"), MISSED("Пропущенные"), OUTGOING("Исходящие"), INCOMING("Входящие")
}

internal enum class ContactFilter(val title: String) {
    ALL("Все"), CLIENTS("Клиенты"), EMPLOYEES("Сотрудники")
}

data class Contact(
    val name: String,
    val number: String,
    val favorite: Boolean,
    val position: String = "",
    val department: String = "",
    val company: String = "",
    val telegram: String = "",
    val office: String = "",
    val phone: String = "",
    val source: ContactSource = ContactSource.LEGACY
) {
    // Компания ПОРТ автоматически относит контакт к сотрудникам.
    val isEmployee: Boolean get() = company.trim().equals("ПОРТ", ignoreCase = true)
}

enum class ContactSource { LEGACY, DIRECTORY, LOCAL }

data class HistoryItem(
    val id: String,
    val name: String,
    val number: String,
    val direction: String,
    val startedAtEpochMillis: Long,
    val telegram: String = "",
    val missed: Boolean = false,
    val durationSeconds: Long? = null,
    private val legacyTime: String = ""
) {
    constructor(name: String, number: String, direction: String, time: String, missed: Boolean = false) :
        this("legacy:$name:$number:$time", name, number, direction, 0L, missed = missed, legacyTime = time)

    val time: String get() = legacyTime
}

// Пара цветов аватара стабильно вычисляется из имени или номера.
internal fun avatarGradientFor(value: String): List<Color> {
    val gradients = listOf(
        listOf(Color(0xFF17376B), Color(0xFF416FC9)),
        listOf(Color(0xFF370067), Color(0xFF6D00CD)),
        listOf(Color(0xFF123B52), Color(0xFF247EA0)),
        listOf(Color(0xFF4A082D), Color(0xFFA91665)),
        listOf(Color(0xFF0B403B), Color(0xFF168B7D)),
        listOf(Color(0xFF352044), Color(0xFF8050A0))
    )
    return gradients[(value.hashCode() and Int.MAX_VALUE) % gradients.size]
}

internal fun colorFor(value: String): Color = avatarGradientFor(value).first()

internal fun initialsForName(name: String): String {
    if (name.none(Char::isLetter)) return "?"
    return name.trim().split(Regex("\\s+"))
        .filter { part -> part.any(Char::isLetter) }
        .take(2)
        .joinToString("") { part -> part.first(Char::isLetter).uppercase() }
        .ifBlank { "?" }
}

// Храним Telegram в одном читаемом формате.
internal fun normalizeTelegramInput(value: String): String {
    val username = value.trim()
        .removePrefix("https://t.me/")
        .removePrefix("http://t.me/")
        .removePrefix("t.me/")
        .removePrefix("@")
        .filter { it.isLetterOrDigit() || it == '_' }
    return if (username.isBlank()) "" else "@$username"
}

internal fun formatDuration(totalSeconds: Int): String {
    val minutes = (totalSeconds / 60).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

internal val keypadRows = listOf(
    listOf("1" to "", "2" to "АБВГ", "3" to "ДЕЖЗ"),
    listOf("4" to "ИЙКЛ", "5" to "МНОП", "6" to "РСТУ"),
    listOf("7" to "ФХЦЧ", "8" to "ШЩЪЫ", "9" to "ЬЭЮЯ"),
    listOf("*" to "", "0" to "+", "#" to "")
)
