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
    "Не все участники подтвердили общий экран. Проверьте подключение к сервису конференций и версию QTalk у участников." to "Some participants have not confirmed the shared view. Check the conference service connection and participants' QTalk versions.",
    "Выберите участников для группового звонка" to "Select participants for the group call",
    "Создать конференцию" to "Create group call", "Добавить участников" to "Add participants",
    "Пригласить" to "Invite", "Групповой звонок" to "Group call", "Приглашаем участников…" to "Inviting participants…",
    "Выход организатора завершит звонок" to "The call ends when the host leaves",
    "Организатор передаст звонок при выходе" to "The host will hand over the call when leaving",
    "Отменить приглашение" to "Cancel invitation", "Выйти" to "Leave",
    "При выходе организатора групповой звонок завершится." to "The group call ends when the host leaves.",
    "Приглашение в групповой звонок" to "Group call invitation",
    "Достигнут лимит участников" to "Participant limit reached",
    "Приглашать участников может только организатор" to "Only the host can invite participants",
    "Дождитесь завершения приглашения" to "Wait for the current invitations to finish",
    "Организатор" to "Host", "Звоним…" to "Calling…",
    "Готов к подключению" to "Ready to join", "Возвращаемся к звонку…" to "Returning to the call…",
    "На удержании" to "On hold", "Подключаем…" to "Connecting…", "В звонке" to "In call",
    "Участник ответил. Добавьте его в конференцию или вернитесь к первому звонку." to "The participant answered. Add them to the conference or return to the first call.",
    "Первый собеседник на удержании. Приглашение можно отменить." to "The first participant is on hold. You can cancel the invitation.",
    "Добавить в конференцию" to "Add to conference", "Завершить для всех" to "End for everyone",
    "Звонок работает на вашем компьютере. При его завершении отключатся все участники." to "Your computer hosts this call. Ending it will disconnect everyone.",
    "Отключить участника?" to "Disconnect participant?",
    "Остальные участники продолжат разговор." to "The other participants will stay in the call.",
    "Отключить от конференции" to "Remove from conference",
    "Микрофон выключен" to "Microphone off", "Говорит" to "Speaking",
    "Конференция ещё не началась" to "The conference has not started yet",
    "Конференция" to "Conference", "Вы" to "You",
    "Локальная аудиоконференция" to "Local audio conference",
    "Добавить участника" to "Add participant", "Имя или номер" to "Name or number",
    "Первый собеседник будет на удержании до объединения звонков." to "The first participant will be on hold until the calls are merged.",
    "Звоним участнику… Первый собеседник на удержании." to "Calling the participant… The first participant is on hold.",
    "Участник ответил. Можно объединить звонки." to "The participant answered. You can now merge the calls.",
    "Объединяем звонки…" to "Merging calls…",
    "Возвращаемся к первому звонку…" to "Returning to the first call…",
    "Объединить" to "Merge calls", "Вернуться к первому звонку" to "Return to first call",
    "Конференция уже создаётся" to "A conference is already being created",
    "Выберите другого участника" to "Choose another participant",
    "Сначала дождитесь ответа собеседника" to "Wait for the participant to answer first",
    "Первый собеседник завершил звонок" to "The first participant ended the call",
    "Участник отключился или не ответил" to "The participant disconnected or did not answer",
    "Не удалось добавить участника" to "Could not add the participant",
    "Дождитесь ответа участника" to "Wait for the participant to answer",
    "Не удалось объединить звонки" to "Could not merge the calls",
    "Конференция уже началась" to "The conference has already started",
    "Не удалось вернуться к первому звонку" to "Could not return to the first call",
    "Не удалось поставить первый звонок на удержание" to "Could not put the first call on hold",
    "Не удалось создать локальную конференцию" to "Could not create a local conference",
    "Не удалось подключить звук конференции" to "Could not connect conference audio",
    "Не удалось завершить звонок участника" to "Could not end the participant's call",
    "Нет связи с сервером телефонии" to "No connection to the telephony server",
    "Нельзя добавить свой номер" to "You cannot add your own number",
    "Конференции недоступны" to "Conferences are not available",
    "Нет сетевого подключения. Проверьте подключение к сети." to "No network connection. Check your network connection.",
    "Сервер отклонил авторизацию. Проверьте логин и пароль." to "Authentication rejected. Check your username and password.",
    "Сервер отклонил регистрацию. Проверьте настройки SIP." to "Registration rejected. Check your SIP settings.",
    "Подключение к серверу телефонии…" to "Connecting to the telephony server…",
    "Нет связи с сервером телефонии. Переподключаемся…" to "Telephony server unavailable. Reconnecting…",
    "Настроить аккаунт" to "Account settings", "Повторить" to "Retry",
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
    "Загрузить аватар" to "Upload avatar", "Загрузить рингтон" to "Upload ringtone",
    "Закрыть окно" to "Close window",
    "Загрузить свою аватарку" to "Upload custom avatar",
    "Вернуть исходную аватарку" to "Restore original avatar",
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
