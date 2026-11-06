package com.qvoste.qtalk

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.voip.*
import kotlin.test.*

class ContactCreationTest {
    @Test fun duplicateNormalizedNumberDoesNotCreateOrOverwriteDirectoryContact() {
        val directory = Contact("Directory name", "+7 900 111 22 33", false, source = ContactSource.DIRECTORY)
        val store = InMemoryContactStore(listOf(directory))
        val engine = FakeVoipEngine()
        val vm = CallsViewModel(engine, ConnectSipUseCase(engine), InMemorySipAccountStore(),
            store, DisabledContactDatabaseService, InMemoryContactNoteStore(), InMemoryCallHistoryStore())
        try {
            vm.addContact(Contact("Draft name", "79001112233", false))
            assertEquals(listOf(directory), store.load())
            vm.addContact(Contact("New local", "555123", false, source = ContactSource.DIRECTORY))
            assertEquals(2, store.load().size)
            assertEquals(ContactSource.LOCAL, store.load().last().source)
        } finally { vm.close() }
    }
}
