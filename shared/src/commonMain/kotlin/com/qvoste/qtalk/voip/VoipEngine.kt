package com.qvoste.qtalk.voip

import kotlinx.coroutines.flow.StateFlow

interface VoipEngine {
    val registrationState: StateFlow<RegistrationState>

    val callStatus: StateFlow<CallStatus>

    suspend fun call(number: String)
    suspend fun hangUp()

    suspend fun start()
    suspend fun stop()

    suspend fun register(account: SipAccount)
    suspend fun unregister()
}