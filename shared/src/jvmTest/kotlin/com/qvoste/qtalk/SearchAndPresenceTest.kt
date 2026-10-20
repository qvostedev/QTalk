package com.qvoste.qtalk

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.voip.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class SearchAndPresenceTest {
    @Test fun searchIgnoresWhitespaceInBothQueryAndContactOrHistory() {
        val contact = Contact("Иван Иванов", "2 01", true, phone = "+7 900 123 45 67",
            company = "Тест компания", telegram = "@test_user")
        for (query in listOf(" ИванИванов ", "Иван\tИва нов", "201", "+79001234567", "Тесткомпания", "@test_ user", "\u00a0")) {
            assertTrue(contact.matchesSearch(query), query)
        }
        assertFalse(contact.matchesSearch("Петров"))
        val history = HistoryItem("id", contact.name, contact.phone, "Входящий", 1L)
        assertTrue(history.matchesSearch(" ИванИванов "))
        assertTrue(history.matchesSearch("+79001234567"))
    }

    @Test fun selectedContactResolvesLatestStatusWithoutChangingSelection() {
        val selected = Contact("Иван Иванов", "201", true, source = ContactSource.DIRECTORY)
        val latest = selected.copy(name = "Иванов Иван", status = EmployeeStatus.BUSY)
        assertEquals(latest, selected.latestFrom(listOf(latest)))
        assertNull(selected.latestFrom(listOf(latest.copy(source = ContactSource.LOCAL))))
        assertNull(selected.latestFrom(emptyList()))
    }

    @Test fun localStatusUpdatesBeforeSlowPublicationAndReadsRunIndependently() = runBlocking {
        val states = MutableStateFlow(CallStatus())
        val engine = object : VoipEngine by FakeVoipEngine() {
            override val registrationState = MutableStateFlow(RegistrationState.REGISTERED)
            override val callStatus = states
            override suspend fun register(account: SipAccount) = Unit
        }
        val publishStarted = CompletableDeferred<Unit>()
        val releasePublication = CompletableDeferred<Unit>()
        val remoteRead = CompletableDeferred<Unit>()
        val presence = object : EmployeePresenceService {
            override val enabled = true
            override suspend fun publish(extension: String, status: EmployeeStatus) {
                publishStarted.complete(Unit)
                releasePublication.await()
            }
            override suspend fun load(): Map<String, EmployeeStatus> {
                remoteRead.complete(Unit)
                return mapOf("202" to EmployeeStatus.BUSY, "201" to EmployeeStatus.OFFLINE)
            }
        }
        val store = InMemorySipAccountStore().apply {
            save(SipAccount(username = "201", password = "test", domain = "example.invalid"))
        }
        val vm = CallsViewModel(engine, ConnectSipUseCase(engine), store,
            InMemoryContactStore(listOf(Contact("Self", "201", true), Contact("Peer", "202", true))),
            DisabledContactDatabaseService, InMemoryContactNoteStore(), InMemoryCallHistoryStore(), presence)
        try {
            withTimeout(3_000) {
                publishStarted.await()
                remoteRead.await()
                while (vm.contacts.value.first { it.number == "202" }.status != EmployeeStatus.BUSY) delay(10)
                states.value = CallStatus(CallState.ACTIVE, "202")
                while (vm.contacts.value.first { it.number == "201" }.status != EmployeeStatus.BUSY) delay(10)
            }
            assertFalse(releasePublication.isCompleted)
        } finally {
            releasePublication.complete(Unit)
            vm.close()
        }
    }

    @Test fun editingContactStillWorksWhenStatusChangedSinceEditorOpened() {
        val before = Contact("Peer", "202", true)
        val engine = FakeVoipEngine()
        val vm = CallsViewModel(engine, ConnectSipUseCase(engine), InMemorySipAccountStore(),
            InMemoryContactStore(listOf(before.copy(status = EmployeeStatus.BUSY))),
            DisabledContactDatabaseService, InMemoryContactNoteStore(), InMemoryCallHistoryStore())
        try {
            vm.updateContact(before, before.copy(position = "Engineer"))
            assertEquals("Engineer", vm.contacts.value.single().position)
            assertEquals(EmployeeStatus.BUSY, vm.contacts.value.single().status)
        } finally { vm.close() }
    }
}
