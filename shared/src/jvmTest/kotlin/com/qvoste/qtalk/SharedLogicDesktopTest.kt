package com.qvoste.qtalk

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.model.ContactSource
import com.qvoste.qtalk.ui.calls.model.GoogleSheetsContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.PreferencesContactStore
import com.qvoste.qtalk.ui.calls.model.ContactNote
import com.qvoste.qtalk.ui.calls.model.PreferencesContactNoteStore
import com.qvoste.qtalk.ui.calls.model.HistoryItem
import com.qvoste.qtalk.ui.calls.model.PreferencesCallHistoryStore
import com.qvoste.qtalk.ui.calls.model.formatHistoryTimestamp
import com.qvoste.qtalk.ui.calls.model.historyDateHeading
import com.qvoste.qtalk.ui.calls.model.formatCallDuration
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import com.qvoste.qtalk.ui.calls.model.dialablePhoneNumber
import com.qvoste.qtalk.ui.calls.model.mergeDownloadedContacts
import com.qvoste.qtalk.ui.calls.model.transferCodeFor
import com.qvoste.qtalk.ui.calls.model.matchesSearch
import com.qvoste.qtalk.ui.calls.model.matchesCallNumber
import com.qvoste.qtalk.ui.calls.model.telegramMatchesQuery
import com.qvoste.qtalk.ui.calls.model.DisabledContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.InMemoryCallHistoryStore
import com.qvoste.qtalk.ui.calls.model.InMemoryContactNoteStore
import com.qvoste.qtalk.ui.calls.model.InMemoryContactStore
import com.qvoste.qtalk.ui.calls.theme.PreferencesThemeStore
import com.qvoste.qtalk.ui.calls.theme.ThemeMode
import com.qvoste.qtalk.voip.normalizeOutgoingNumber
import com.qvoste.qtalk.voip.CallState
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.FakeVoipEngine
import com.qvoste.qtalk.voip.InMemorySipAccountStore
import com.qvoste.qtalk.voip.VoipEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLogicDesktopTest {

    @Test
    fun callMatchesManuallyCreatedContactAcrossNumberFormats() {
        val contact = Contact("Тестовый контакт", "+8 000 000 00 00", true, phone = "")
        assertTrue(contact.matchesCallNumber("80000000000"))
        assertTrue(contact.matchesCallNumber("+7 000 000 00 00"))
        assertFalse(contact.matchesCallNumber("+7 000 000 00 01"))
        assertFalse(contact.matchesCallNumber(""))
    }

    @Test
    fun contactSearchMatchesTelegramWithOrWithoutAtSign() {
        val contact = Contact("Тестовый контакт", "123", false, telegram = "@sample_contact")
        assertTrue(contact.matchesSearch("@sample_contact"))
        assertTrue(contact.matchesSearch("sample_contact"))
        assertTrue(contact.matchesSearch("123"))
        assertFalse(contact.matchesSearch("@unknown"))
        assertFalse(telegramMatchesQuery(contact.telegram, "@"))
    }

    @Test
    fun employeeRightClickSendsTransferCodeDuringExistingCall() = runBlocking {
        val engine = object : VoipEngine by FakeVoipEngine() {
            override val callStatus: StateFlow<CallStatus> = MutableStateFlow(
                CallStatus(CallState.ACTIVE, "89951751328", connectedAtEpochMillis = 42L)
            )
            val sent = java.util.Collections.synchronizedList(mutableListOf<Char>())
            var newCalls = 0
            override suspend fun sendDtmf(digit: Char) { sent.add(digit) }
            override suspend fun call(number: String) { newCalls++ }
        }
        val viewModel = CallsViewModel(
            engine, ConnectSipUseCase(engine), InMemorySipAccountStore().apply {
                save(com.qvoste.qtalk.voip.SipAccount("216", domain = "example.invalid"))
            },
            InMemoryContactStore(listOf(
                Contact("User", "216", false, company = "Test organization"),
                Contact("Кайгородов Антон", "213", false, company = "Test organization")
            )), DisabledContactDatabaseService,
            InMemoryContactNoteStore(), InMemoryCallHistoryStore()
        )
        try {
            withTimeout(3_000) { while (engine.registrationState.value != com.qvoste.qtalk.voip.RegistrationState.REGISTERED) delay(10) }
            viewModel.transferToEmployee(Contact("Кайгородов Антон", "213", false, company = "Test organization"))
            withTimeout(3_000) { while (engine.sent.size < 5) delay(10) }
            assertEquals("**213", synchronized(engine.sent) { engine.sent.joinToString("") })
            assertEquals(0, engine.newCalls)
        } finally { viewModel.close() }
    }

    @Test
    fun transferCodeUsesOnlyEmployeeExtensions() {
        val employee = Contact("Кайгородов Антон", "213", false, isEmployee = true)
        assertEquals("**213", transferCodeFor(employee))
        assertEquals(null, transferCodeFor(employee.copy(number = "+7 900 000 00 00")))
        assertEquals(null, transferCodeFor(employee.copy(isEmployee = false)))
        assertEquals(null, transferCodeFor(employee.copy(number = "")))
    }

    @Test
    fun selectedThemeSurvivesRestart() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val store = PreferencesThemeStore(node)
            assertEquals(ThemeMode.DARK, store.load())
            store.save(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, PreferencesThemeStore(node).load())
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun phoneNumbersAreReadableButExtensionsStayUntouched() {
        assertEquals("209", formatPhoneNumber("209"))
        assertEquals("+8 995 175 13 28", formatPhoneNumber("89951751328"))
        assertEquals("+7 905 976 72 41", formatPhoneNumber("9059767241"))
        assertEquals("+89951751328", dialablePhoneNumber("+8 995 175 13 28"))
    }

    @Test
    fun outgoingNumberKeepsInternationalPlus() {
        assertEquals("+79059767241", normalizeOutgoingNumber(" +7905 976 72 41 "))
        assertEquals("216", normalizeOutgoingNumber("216"))
        assertFailsWith<IllegalArgumentException> { normalizeOutgoingNumber("++7905") }
    }

    @Test
    fun newContactStoreStartsEmptyAndPersistsContacts() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val store = PreferencesContactStore(node)
            assertTrue(store.load().isEmpty())
            assertFalse(node.getBoolean("initialized", false))

            val updated = listOf(
                Contact(
                    name = "Тестовый контакт",
                    number = "999",
                    favorite = true,
                    position = "Тест",
                    department = "Тестирование",
                    company = "ПОРТ",
                    office = "Молокова, 68",
                    phone = "2-347-348 доб.999"
                )
            )
            store.save(updated)

            assertEquals(updated, PreferencesContactStore(node).load())
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun contactNotesPersistTextAndPinnedState() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val notes = listOf(
                ContactNote(
                    id = "1",
                    contactName = "Кайгородов Антон",
                    contactNumber = "213",
                    text = "Перезвонить после обеда",
                    pinned = true
                ),
                ContactNote(
                    id = "2",
                    contactName = "Кайгородов Антон",
                    contactNumber = "213",
                    text = "Уточнить номер заявки"
                )
            )
            PreferencesContactNoteStore(node).save(notes)

            assertEquals(notes, PreferencesContactNoteStore(node).load())
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun callHistoryPersistsAcrossApplicationUpdates() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val history = listOf(
                HistoryItem(
                    id = "incoming:213:1791417600000",
                    name = "Кайгородов Антон",
                    number = "213",
                    direction = "Входящий",
                    startedAtEpochMillis = 1_791_417_600_000L,
                    telegram = "@anton",
                    missed = true,
                    missedAcknowledged = true,
                    durationSeconds = 0L
                )
            )
            PreferencesCallHistoryStore(node).save(history)

            assertEquals(history, PreferencesCallHistoryStore(node).load())
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun viewingMissedCallsAcknowledgesOnlyUnreadMissedEntries() {
        val unread = HistoryItem(
            id = "incoming:201:1",
            name = "Пропущенный",
            number = "201",
            direction = "Входящий",
            startedAtEpochMillis = 1L,
            missed = true
        )
        val acknowledged = unread.copy(id = "incoming:202:2", number = "202", missedAcknowledged = true)
        val outgoing = unread.copy(
            id = "outgoing:203:3",
            number = "203",
            direction = "Исходящий",
            missed = false
        )
        val historyStore = InMemoryCallHistoryStore(listOf(unread, acknowledged, outgoing))
        val engine = FakeVoipEngine()
        val viewModel = CallsViewModel(
            engine, ConnectSipUseCase(engine), InMemorySipAccountStore(),
            InMemoryContactStore(emptyList()), DisabledContactDatabaseService,
            InMemoryContactNoteStore(), historyStore
        )

        try {
            viewModel.markMissedCallsSeen()

            val history = viewModel.callHistory.value
            assertTrue(history[0].missedAcknowledged)
            assertTrue(history[1].missedAcknowledged)
            assertFalse(history[2].missedAcknowledged)
            assertEquals(history, historyStore.load())
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun callHistoryShowsTimeTodayAndDateForOlderCalls() {
        val now = 1_791_460_800_000L
        val today = formatHistoryTimestamp(now, now)
        val older = formatHistoryTimestamp(now - 48 * 60 * 60 * 1_000L, now)

        assertEquals(5, today.length)
        assertFalse('.' in today)
        assertTrue(Regex("\\d{2}\\.\\d{2}\\.\\d{4}  \\d{2}:\\d{2}").matches(older))
    }

    @Test
    fun callHistoryHasDateGroupsAndReadableDuration() {
        val now = 1_791_460_800_000L
        assertEquals("Сегодня", historyDateHeading(now, now))
        assertEquals("Вчера", historyDateHeading(now - 24 * 60 * 60 * 1_000L, now))
        assertTrue(Regex("[А-Яа-я]+ \\d{2}\\.\\d{2}(\\.\\d{4})?")
            .matches(historyDateHeading(now - 48 * 60 * 60 * 1_000L, now)))
        assertEquals("Без ответа", formatCallDuration(0L))
        assertEquals("1 мин 23 сек", formatCallDuration(83L))
        assertEquals("", formatCallDuration(null))
    }

    @Test
    fun contactStoreMigratesAutomationDepartments() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            node.putBoolean("initialized", true)
            node.putInt("count", 1)
            node.putInt("schemaVersion", 1)
            node.put("0.name", "Тестовый системный администратор")
            node.put("0.department", "Системные администраторы")

            val migrated = PreferencesContactStore(node).load().single()

            assertEquals("Отдел автоматизации", migrated.department)
            assertEquals(3, node.getInt("schemaVersion", 0))
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun googleSheetsCsvSupportsQuotedCommasAndIgnoresEmail() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val csv = """
                Справочник сотрудников,,,,,,,
                Ф.И.О.,Отдел,Должность,Офис,Городской номер,Внутренний,E-mail,Telegram
                Иванов Иван,Отдел автоматизации,Инженер,"Молокова, 68",2-347-348,216,ivan@example.com,@ivan
            """.trimIndent()

            val contact = GoogleSheetsContactDatabaseService(node).parseContacts(csv).single()

            assertEquals("Иванов Иван", contact.name)
            assertEquals("216", contact.number)
            assertEquals("", contact.company)
            assertEquals("Молокова, 68", contact.office)
            assertEquals("@ivan", contact.telegram)
            assertEquals("2-347-348 доб.216", contact.phone)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun contactSyncKeepsFavoriteWhileRefreshingContactDetails() {
        val local = Contact(
            name = "Иванов Иван",
            number = "216",
            favorite = true,
            position = "Старая должность",
            department = "Старый отдел",
            company = "ПОРТ"
        )
        val downloaded = local.copy(
            favorite = false,
            position = "Новая должность",
            department = "Новый отдел"
        )

        val merged = mergeDownloadedContacts(listOf(local), listOf(downloaded)).single()

        assertEquals(true, merged.favorite)
        assertEquals("Новая должность", merged.position)
        assertEquals("Новый отдел", merged.department)
    }

    @Test
    fun contactSyncDoesNotShareFavoriteBetweenContactsWithTheSameNumber() {
        val localContacts = listOf(
            Contact("Первый контакт", "100", favorite = true),
            Contact("Второй контакт", "100", favorite = false)
        )
        val downloadedContacts = listOf(
            Contact("Первый контакт", "100", favorite = false),
            Contact("Второй контакт", "100", favorite = false)
        )

        val merged = mergeDownloadedContacts(localContacts, downloadedContacts)

        assertEquals(listOf(true, false), merged.map(Contact::favorite))
    }

    @Test
    fun contactSyncKeepsFavoriteAfterUniqueContactRename() {
        val local = Contact("Иванов Иван", "216", favorite = true)
        val downloaded = Contact("Иванов Иван Иванович", "216", favorite = false)

        assertEquals(
            true,
            mergeDownloadedContacts(listOf(local), listOf(downloaded))
                .single { it.name == downloaded.name }.favorite
        )
    }

    @Test
    fun manuallyAddedContactsSurviveRepeatedDirectorySyncAndReload() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val store = PreferencesContactStore(node)
            val manual = Contact("Грецкий Олег", "89951751328", favorite = true, source = ContactSource.LOCAL)
            val directory = Contact("Иванов Иван", "216", favorite = false)
            store.save(listOf(directory, manual))

            val firstSync = mergeDownloadedContacts(store.load(), listOf(directory))
            store.save(firstSync)
            val secondSync = mergeDownloadedContacts(store.load(), listOf(directory.copy(position = "Инженер")))

            assertEquals(2, secondSync.size)
            assertEquals(manual, secondSync.single { it.name == manual.name })
            assertEquals("Инженер", secondSync.single { it.name == directory.name }.position)
            assertEquals(ContactSource.DIRECTORY, secondSync.single { it.name == directory.name }.source)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun legacyUnmatchedContactIsKeptDuringFirstSync() {
        val manual = Contact("Грецкий Олег", "89951751328", favorite = true)
        val directory = Contact("Иванов Иван", "216", favorite = false)

        val merged = mergeDownloadedContacts(listOf(directory, manual), listOf(directory))

        assertEquals(ContactSource.LOCAL, merged.single { it.name == manual.name }.source)
        assertEquals(2, merged.size)
    }

    @Test
    fun localContactWithSameNumberAsDirectoryContactIsNotDiscarded() {
        val directory = Contact("Иванов Иван", "216", favorite = false, source = ContactSource.DIRECTORY)
        val manual = Contact("Домашний телефон", "216", favorite = true, source = ContactSource.LOCAL)

        val merged = mergeDownloadedContacts(listOf(directory, manual), listOf(directory))

        assertEquals(2, merged.size)
        assertEquals(manual, merged.single { it.name == manual.name })
    }

    @Test
    fun legacyContactWithSameNumberAsDirectoryContactIsNotDiscarded() {
        val directory = Contact("Иванов Иван", "216", favorite = false)
        val manual = Contact("Домашний телефон", "216", favorite = true)

        val merged = mergeDownloadedContacts(listOf(directory, manual), listOf(directory))

        assertEquals(2, merged.size)
        assertEquals(ContactSource.LOCAL, merged.single { it.name == manual.name }.source)
    }
}
