package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.ui.calls.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.test.*

class TelephonyConnectionTest {
    @Test fun normalRefreshDoesNotLookLikeAnOutage() {
        assertEquals(RegistrationState.REGISTERED, registrationDuringRefresh(RegistrationState.REGISTERED))
        assertEquals(RegistrationState.CONNECTING, registrationDuringRefresh(RegistrationState.FAILED))
        assertFalse(TelephonyConnection.CONNECTED.showWarning)
        assertTrue(TelephonyConnection.SERVER_UNREACHABLE.showWarning)
    }

    @Test fun failuresAreClassifiedAndRetriesAreBounded() {
        assertEquals(TelephonyConnection.AUTHENTICATION_FAILED, classifyRegistrationFailure(403, false))
        assertEquals(TelephonyConnection.AUTHENTICATION_FAILED, classifyRegistrationFailure(0, true))
        assertEquals(TelephonyConnection.SERVER_UNREACHABLE, classifyRegistrationFailure(503, false))
        assertEquals(TelephonyConnection.REGISTRATION_REJECTED, classifyRegistrationFailure(404, false))
        assertFalse(TelephonyConnection.AUTHENTICATION_FAILED.retryable)
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L),
            (0..5).map(::reconnectDelayMillis))
    }

    @Test fun transientFailureKeepsSavedAccountAndMainScreen() = runBlocking {
        val registration = MutableStateFlow(RegistrationState.REGISTERED)
        val engine = object : VoipEngine by FakeVoipEngine() {
            override val registrationState = registration
            override suspend fun register(account: SipAccount) = Unit
        }
        val store = InMemorySipAccountStore()
        store.save(SipAccount(username = "101", password = "test", domain = "example.invalid"))
        val vm = CallsViewModel(engine, ConnectSipUseCase(engine), store, InMemoryContactStore(),
            DisabledContactDatabaseService, InMemoryContactNoteStore(), InMemoryCallHistoryStore())
        try {
            delay(100)
            registration.value = RegistrationState.FAILED
            delay(100)
            assertFalse(vm.authenticationRequired.value)
            assertEquals("101", store.load()?.username)
        } finally { vm.close() }
    }
}
