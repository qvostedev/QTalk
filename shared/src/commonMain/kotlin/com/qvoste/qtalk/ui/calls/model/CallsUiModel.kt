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

internal data class Contact(
    val name: String,
    val number: String,
    val favorite: Boolean,
    val position: String = "",
    val department: String = "",
    val company: String = "",
    val telegram: String = ""
) {
    // Компания ПОРТ автоматически относит контакт к сотрудникам.
    val isEmployee: Boolean get() = company.trim().equals("ПОРТ", ignoreCase = true)
}

internal data class HistoryItem(
    val name: String,
    val number: String,
    val direction: String,
    val time: String,
    val missed: Boolean = false
)

// Цвет аватара стабильно вычисляется из имени.
internal fun colorFor(value: String): Color {
    val colors = listOf(
        Color(0xFF3659A2), Color(0xFF681466), Color(0xFF7A4A0D),
        Color(0xFF4C0084), Color(0xFF60472F), Color(0xFF8D1558)
    )
    return colors[(value.hashCode() and Int.MAX_VALUE) % colors.size]
}

internal fun initialsForName(name: String): String = name.trim().split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifBlank { "?" }

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
