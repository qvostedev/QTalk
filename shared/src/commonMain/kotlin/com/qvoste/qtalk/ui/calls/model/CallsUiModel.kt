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
    val source: ContactSource = ContactSource.LEGACY,
    val status: EmployeeStatus = EmployeeStatus.OFFLINE
) {
    // Компания ПОРТ автоматически относит контакт к сотрудникам.
    val isEmployee: Boolean get() = company.trim().equals("ПОРТ", ignoreCase = true)
}

enum class ContactSource { LEGACY, DIRECTORY, LOCAL }

enum class EmployeeStatus(val sheetValue: String) {
    AVAILABLE("Доступен"),
    BUSY("Занят"),
    OFFLINE("Не в сети");

    companion object {
        fun fromSheet(value: String): EmployeeStatus = entries.firstOrNull {
            it.sheetValue.equals(value.trim(), ignoreCase = true)
        } ?: OFFLINE
    }
}

data class HistoryItem(
    val id: String,
    val name: String,
    val number: String,
    val direction: String,
    val startedAtEpochMillis: Long,
    val telegram: String = "",
    val missed: Boolean = false,
    val missedAcknowledged: Boolean = false,
    val durationSeconds: Long? = null
)

internal fun telegramMatchesQuery(telegram: String, query: String): Boolean {
    val handle = query.trim().removePrefix("@")
    return handle.isNotEmpty() && telegram.trim().removePrefix("@")
        .contains(handle, ignoreCase = true)
}

internal fun Contact.matchesSearch(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        number.contains(query) ||
        company.contains(query, ignoreCase = true) ||
        department.contains(query, ignoreCase = true) ||
        position.contains(query, ignoreCase = true) ||
        office.contains(query, ignoreCase = true) ||
        phone.contains(query, ignoreCase = true) ||
        telegramMatchesQuery(telegram, query)

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
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("*", "0", "#")
)
