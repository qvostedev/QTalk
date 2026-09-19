package com.qvoste.qtalk.voip

import kotlinx.coroutines.flow.StateFlow

interface VoipEngine {
    val registrationState: StateFlow<RegistrationState>
    suspend fun register(account: SipAccount)
    suspend fun unregister()
}