package com.qvoste.qtalk.voip

import kotlinx.coroutines.flow.StateFlow

interface VoipEngine {
    val registrationState: StateFlow<RegistrationState>

    suspend fun start()
    suspend fun stop()

    suspend fun register(account: SipAccount)
    suspend fun unregister()
}