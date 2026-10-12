package com.qvoste.qtalk.ui.calls.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.prefs.Preferences

class GoogleSheetsContactDatabaseService(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/contactDatabase")
) : ContactDatabaseService {

    override fun loadSourceUrl(): String = preferences.get(KEY_SOURCE_URL, DEFAULT_SOURCE_URL)

    override fun saveSourceUrl(url: String) {
        preferences.put(KEY_SOURCE_URL, url.trim())
        preferences.flush()
    }

    override suspend fun downloadContacts(url: String): List<Contact> = withContext(Dispatchers.IO) {
        val exportUrl = buildExportUrl(url)
        val connection = URI(exportUrl).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "QTalk/1.0")
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                error("Таблица недоступна. Включите доступ «Все, у кого есть ссылка»")
            }
            check(status in 200..299) { "Google Таблица вернула ошибку HTTP $status" }
            val csv = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseContacts(csv)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildExportUrl(sourceUrl: String): String {
        val trimmed = sourceUrl.trim()
        val spreadsheetId = Regex("/spreadsheets/d/([A-Za-z0-9_-]+)")
            .find(trimmed)?.groupValues?.get(1)
            ?: error("Вставьте ссылку на Google Таблицу")
        val gid = Regex("(?:[?&#]gid=)(\\d+)").find(trimmed)?.groupValues?.get(1)
        return buildString {
            append("https://docs.google.com/spreadsheets/d/")
            append(spreadsheetId)
            append("/export?format=csv")
            if (gid != null) append("&gid=").append(gid)
        }
    }

    internal fun parseContacts(csv: String): List<Contact> {
        val rows = parseCsv(csv.removePrefix("\uFEFF"))
        val headerRowIndex = rows.indexOfFirst { row ->
            val headers = row.map(::normalizeHeader)
            headers.any { it == "фио" || it == "ф и о" || it == "имя" || it == "name" } &&
                headers.any { it.contains("внутрен") || it.contains("добавоч") || it == "number" }
        }
        check(headerRowIndex >= 0) {
            "Не найдены столбцы «Ф.И.О.» и «Внутренний»"
        }

        val headers = rows[headerRowIndex].map(::normalizeHeader)
        fun column(vararg variants: String): Int = headers.indexOfFirst { header ->
            variants.any { variant -> header == variant || header.contains(variant) }
        }
        val nameColumn = column("фио", "ф и о", "имя", "name")
        val numberColumn = column("внутрен", "добавоч", "number")
        val positionColumn = column("должност", "position")
        val departmentColumn = column("отдел", "department")
        val officeColumn = column("офис", "адрес", "office")
        val cityPhoneColumn = column("городск", "телефон", "phone")
        val companyColumn = column("компан", "company")
        val telegramColumn = column("telegram", "телеграм")

        fun List<String>.valueAt(index: Int): String =
            if (index in indices) this[index].trim().replace(Regex("\\s+"), " ") else ""

        val contacts = rows.drop(headerRowIndex + 1).mapNotNull { row ->
            val name = row.valueAt(nameColumn)
            if (name.isBlank()) return@mapNotNull null
            val number = row.valueAt(numberColumn).filter(Char::isDigit)
            val cityPhone = row.valueAt(cityPhoneColumn)
            Contact(
                name = name,
                number = number,
                favorite = false,
                position = row.valueAt(positionColumn),
                department = row.valueAt(departmentColumn),
                company = row.valueAt(companyColumn).ifBlank { "ПОРТ" },
                telegram = normalizeTelegramInput(row.valueAt(telegramColumn)),
                office = row.valueAt(officeColumn),
                phone = when {
                    cityPhone.isBlank() -> number
                    number.isBlank() -> cityPhone
                    else -> "$cityPhone доб.$number"
                }
            )
        }
        check(contacts.isNotEmpty()) { "В таблице не найдено ни одного контакта" }
        return contacts
    }

    private fun normalizeHeader(value: String): String = value
        .trim()
        .lowercase()
        .replace('.', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            when {
                char == '"' && quoted && index + 1 < csv.length && csv[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    row += field.toString()
                    field.clear()
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') index++
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }

    private companion object {
        const val KEY_SOURCE_URL = "googleSheetsUrl"
        const val DEFAULT_SOURCE_URL = ""
    }
}
