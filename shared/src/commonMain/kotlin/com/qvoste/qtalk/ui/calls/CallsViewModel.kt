package com.qvoste.qtalk.ui.calls

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.voip.RegistrationState
import com.qvoste.qtalk.voip.SipAccount
import com.qvoste.qtalk.voip.VoipEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallsViewModel (
    private val voipEngine: VoipEngine,
    private val connectSip: ConnectSipUseCase
) {
    val registrationState: StateFlow<RegistrationState> = voipEngine.registrationState
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun connect() {
        scope.launch {
            val account = SipAccount(
                username = "100",
                password = "qtalk100",
                domain = "127.0.0.1"
            )

            connectSip(account)
        }
    }
}