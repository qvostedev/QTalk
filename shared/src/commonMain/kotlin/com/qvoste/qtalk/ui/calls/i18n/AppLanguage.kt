package com.qvoste.qtalk.ui.calls.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val displayName: String) {
    RUSSIAN("ru", "Русский"), ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String): AppLanguage = entries.firstOrNull { it.code == code } ?: RUSSIAN
    }
}

data class LanguageSettings(
    val language: AppLanguage = AppLanguage.RUSSIAN,
    val onChange: (AppLanguage) -> Unit = {}
)

val LocalAppLanguage = compositionLocalOf { LanguageSettings() }

/** Use only for interface text, never for contact data, notes or protocol values. */
@Composable
internal fun uiText(source: String): String = translateUiText(source, LocalAppLanguage.current.language)

fun translateUiText(source: String, language: AppLanguage): String {
    if (language == AppLanguage.RUSSIAN) return source
    english[source]?.let { return it }
    for (label in countedLabels) {
        if (source.startsWith("$label ")) return english.getValue(label) + source.removePrefix(label)
    }
    for ((pattern, replacement) in englishPatterns) {
        if (pattern.matches(source)) return pattern.replace(source, replacement)
    }
    return source
}

private val english = mapOf(
    "Язык интерфейса" to "Interface language",
    "Русский или английский" to "Russian or English",
    "Выберите язык" to "Choose language",
    "ОСНОВНОЕ" to "MAIN", "СИСТЕМА" to "SYSTEM",
    "Звонки" to "Calls", "Контакты" to "Contacts", "Настройки" to "Settings",
    "Уведомления" to "Notifications", "Избранное" to "Favorites",
    "Все" to "All", "Все  ›" to "All  ›", "Клиенты" to "Customers", "Сотрудники" to "Employees",
    "Сотрудник" to "Employee", "Клиент" to "Customer",
    "Пропущенные" to "Missed", "Исходящие" to "Outgoing", "Входящие" to "Incoming",
    "Пропущенный" to "Missed", "Исходящий" to "Outgoing", "Входящий" to "Incoming",
    "Сегодня" to "Today", "Вчера" to "Yesterday", "Дата неизвестна" to "Unknown date",
    "Аккаунт" to "Account", "Профиль и безопасность" to "Profile and security",
    "Звук и устройства" to "Sound and devices", "Система" to "System",
    "Параметры приложения" to "Application preferences",
    "Микрофон" to "Microphone", "Наушники" to "Headphones",
    "Выбор устройства и настройка" to "Device selection and setup",
    "Громкость микрофона" to "Microphone volume", "Громкость динамика" to "Speaker volume",
    "Громкость рингтона" to "Ringtone volume", "Рингтон" to "Ringtone",
    "Мелодия входящего звонка" to "Incoming call ringtone",
    "Тема оформления" to "Appearance", "Цвет интерфейса" to "Interface colors",
    "Масштаб интерфейса" to "Interface scale", "Размер текста и элементов приложения" to "Text and interface size",
    "Светлая" to "Light", "Тёмная" to "Dark", "Автоматически" to "Automatic",
    "Автоматический масштаб подстраивается под размер окна." to "Automatic scaling adapts to the window size.",
    "База данных" to "Database", "О приложении" to "About",
    "Версия, лицензия, правовая информация" to "Version, license and legal information",
    "Выйти из аккаунта" to "Sign out", "Настройка аккаунта" to "Account settings",
    "SIP-аккаунт" to "SIP account", "SIP-логин" to "SIP username", "SIP-сервер" to "SIP server",
    "Пароль" to "Password", "Транспорт" to "Transport", "Порт" to "Port",
    "Срок регистрации, секунд" to "Registration expiry, seconds", "Сменить аккаунт" to "Switch account",
    "Вход в QTalk" to "Sign in to QTalk", "Введите данные вашей внутренней SIP-линии" to "Enter your internal SIP account details",
    "Использовать готовый HA1 вместо пароля" to "Use an existing HA1 instead of a password",
    "Войти" to "Sign in", "Подключение..." to "Connecting...", "Подключение" to "Connecting",
    "Не подключено" to "Disconnected", "В сети" to "Online", "Ошибка подключения" to "Connection error",
    "Доступен" to "Available", "Занят" to "Busy", "Не в сети" to "Offline",
    "Выберите микрофон" to "Choose microphone", "Выберите динамики" to "Choose speakers",
    "Выберите рингтон" to "Choose ringtone", "Доступные устройства не найдены" to "No devices available",
    "Системный микрофон" to "System microphone", "Системные динамики" to "System speakers",
    "Системный" to "System default", "Вариант - 1" to "Option - 1", "Вариант - 2" to "Option - 2",
    "Ссылка на Google Таблицу" to "Google Sheets link",
    "Подключите Google Таблицу с контактами. Таблица должна быть доступна всем, у кого есть ссылка." to
        "Connect a Google Sheets contact directory. Anyone with the link must have read access.",
    "Контакты обновляются при запуске и каждые 15 минут. E-mail из таблицы не импортируется." to
        "Contacts refresh at startup and every 15 minutes. Email addresses are not imported.",
    "Google Таблица не подключена" to "Google Sheets is not connected",
    "Ожидает синхронизации" to "Waiting to sync", "Загрузка контактов…" to "Loading contacts…",
    "Не удалось загрузить контакты" to "Could not load contacts",
    "Обновить сейчас" to "Refresh now", "Отключить" to "Disconnect",
    "Сохранить" to "Save", "Отмена" to "Cancel", "Закрыть" to "Close", "Готово" to "Done",
    "Добавить" to "Add", "Удалить" to "Delete", "Изменить" to "Edit",
    "Закрепить" to "Pin", "Открепить" to "Unpin", "Позвонить" to "Call", "Копировать номер" to "Copy number",
    "Поиск" to "Search", "Поиск контакта, номера или компании..." to "Search contacts, numbers or companies...",
    "Ничего не найдено" to "No results", "Контактов пока нет" to "No contacts yet",
    "Звонков пока нет" to "No calls yet", "История звонков пока пуста" to "No call history yet",
    "Контакт не выбран" to "No contact selected", "Здесь появится информация" to "Contact details will appear here",
    "Выберите контакт или наберите номер" to "Select a contact or dial a number",
    "Информация" to "Information", "История" to "History", "Заметки" to "Notes",
    "Должность" to "Position", "Отдел" to "Department", "Компания" to "Company",
    "Должность не указана" to "Position not specified", "Не указано" to "Not specified",
    "Не указана" to "Not specified", "Не указан" to "Not specified",
    "Внутренний" to "Extension",
    "ФИО контакта" to "Contact name", "номер" to "number", "Номер  •  " to "Number  •  ",
    "Введите текст заметки" to "Enter a note", "Заметок пока нет" to "No notes yet",
    "Входящий вызов" to "Incoming call", "Входящий звонок" to "Incoming call",
    "Неизвестный номер" to "Unknown number", "Личная линия" to "Personal line",
    "Принять" to "Accept", "Отклонить" to "Decline", "Завершить" to "End call",
    "Удержание" to "Hold", "Динамик" to "Speaker", "Клавиатура" to "Keypad", "Запись" to "Record",
    "Свернуть" to "Minimize", "Удалить символ" to "Delete character", "Удалить цифру" to "Delete digit",
    "Без ответа" to "No answer", "Готов к звонку" to "Ready to call",
    "Подключите SIP в настройках" to "Connect your SIP account in Settings",
    "Ожидание ответа" to "Waiting for answer", "Завершение звонка" to "Ending call",
    "Завершение звонка..." to "Ending call...", "Звонок завершён" to "Call ended",
    "Перевод доступен только во время разговора" to "Transfer is only available during a call",
    "Перевод доступен только на внутренний номер сотрудника" to "Only employee extensions can receive transfers",
    "Новых уведомлений нет" to "No new notifications", "Доступно обновление" to "Update available",
    "Что нового" to "What's new", "Перейти к обновлению  ›" to "Go to update  ›",
    "Подробнее  ⌄" to "Details  ⌄", "Свернуть  ⌃" to "Collapse  ⌃",
    "Описание этого релиза пока не добавлено." to "Release notes have not been added yet.",
    "Обновить" to "Update", "Проверка обновлений недоступна" to "Update checks are unavailable",
    "Открыть QTalk" to "Open QTalk", "Выход" to "Quit",
    "У вас установлена последняя версия" to "You have the latest version",
    "Проверка загруженного установщика…" to "Verifying downloaded installer…",
    "Обновление установлено. Перезапустите QTalk." to "Update installed. Restart QTalk.",
    "Автообновление для этой системы пока недоступно" to "Automatic updates are not available for this system yet",
    "Установщик релиза не содержит контрольную сумму SHA-256" to "The release installer has no SHA-256 checksum",
    "Некорректный размер установщика" to "Invalid installer size",
    "Некорректный адрес установщика" to "Invalid installer URL",
    "Не удалось выполнить действие" to "Could not complete the action",
    "Не удалось отправить DTMF" to "Could not send DTMF",
    "Звонок завершился до перевода" to "The call ended before it could be transferred",
    "Проверка обновлений…" to "Checking for updates…", "Проверка обновлений..." to "Checking for updates...",
    "Понедельник" to "Monday", "Вторник" to "Tuesday", "Среда" to "Wednesday", "Четверг" to "Thursday",
    "Пятница" to "Friday", "Суббота" to "Saturday", "Воскресенье" to "Sunday"
)

private val englishPatterns = listOf(
    Regex("Загружено контактов: (\\d+)") to "Contacts loaded: $1",
    Regex("(\\d+) контактов") to "$1 contacts",
    Regex("Внутренний (.+)") to "Extension $1",
    Regex("  •  Внутренний (.+)") to "  •  Extension $1",
    Regex("Личная линия сотрудника:  (.+)") to "Employee extension:  $1",
    Regex("Номер  •  (.+)") to "Number  •  $1",
    Regex("QTalk (.+) готов к установке") to "QTalk $1 is ready to install",
    Regex("Версия ([^\\n]+)\\nКорпоративный SIP-клиент для звонков и работы с контактами\\.") to
        "Version $1\nCorporate SIP client for calls and contacts.",
    Regex("(.+)  •  Личная линия") to "$1  •  Personal line",
    Regex("Вызов  •  (.+)") to "Call  •  $1",
    Regex("Набор (.+)\\.\\.\\.") to "Dialing $1...",
    Regex("Ожидание ответа (.+)\\.\\.\\.") to "Waiting for $1...",
    Regex("Разговор с (.+)") to "In call with $1",
    Regex("Загрузка версии (.+)…") to "Downloading version $1…",
    Regex("Загрузка версии (.+): ([\\d.,]+) из ([\\d.,]+) МБ") to "Downloading version $1: $2 of $3 MB",
    Regex("Загрузка версии (.+): ([\\d.,]+) МБ") to "Downloading version $1: $2 MB",
    Regex("Установка версии (.+)… QTalk перезапустится автоматически") to "Installing version $1… QTalk will restart automatically",
    Regex("Для версии (.+) не опубликован установщик для вашей системы") to "No installer for your system has been published for version $1",
    Regex("Ошибка обновления: (.+)") to "Update error: $1",
    Regex("(\\d+) мин (\\d+) сек") to "$1 min $2 sec",
    Regex("(\\d+) мин") to "$1 min", Regex("(\\d+) сек") to "$1 sec"
)

private val countedLabels = listOf("Все", "Клиенты", "Сотрудники", "Пропущенные", "Входящие", "Исходящие", "Сегодня", "Вчера",
    "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

fun releaseNotesForLanguage(body: String, language: AppLanguage): String {
    if (body.isBlank()) return ""
    val headings = Regex("(?m)^#{1,2}\\s+(English|Русский|Russian)\\s*\\r?$").findAll(body).toList()
    if (headings.isNotEmpty()) {
        val index = headings.indexOfFirst {
            if (language == AppLanguage.ENGLISH) it.groupValues[1] == "English"
            else it.groupValues[1] in setOf("Русский", "Russian")
        }
        if (index >= 0) {
            return body.substring(headings[index].range.last + 1,
                headings.getOrNull(index + 1)?.range?.first ?: body.length).trim()
        }
    } else {
        val hasRussian = body.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }
        if (hasRussian == (language == AppLanguage.RUSSIAN)) return body.trim()
    }
    return if (language == AppLanguage.RUSSIAN) "Описание этого релиза на русском языке пока не опубликовано."
        else "Release notes in English have not been published yet."
}
