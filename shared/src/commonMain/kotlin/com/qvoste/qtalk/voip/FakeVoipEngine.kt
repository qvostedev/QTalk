package com.qvoste.qtalk.voip

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Оставлен в качестве заглушки
class FakeVoipEngine : VoipEngine {
    private val _registrationState = MutableStateFlow(RegistrationState.DISCONNECTED)
    override val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    override suspend fun start() {}
    override suspend fun stop() {}

    override suspend fun register(account: SipAccount) {
        _registrationState.value = RegistrationState.CONNECTING
        delay(1000)
        _registrationState.value = RegistrationState.REGISTERED
    }

    override suspend fun unregister() {
        _registrationState.value = RegistrationState.DISCONNECTED
    }
}