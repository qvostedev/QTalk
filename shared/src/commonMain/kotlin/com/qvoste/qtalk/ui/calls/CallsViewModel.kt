package com.qvoste.qtalk.ui.calls

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.voip.SipAccount
import com.qvoste.qtalk.voip.VoipEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class CallsViewModel(
    private val voipEngine: VoipEngine,
    private val connectSip: ConnectSipUseCase
) {
    val registrationState = voipEngine.registrationState
    val callStatus = voipEngine.callStatus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandMutex = Mutex()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private fun execute(action: suspend () -> Unit) {
        scope.launch {
            if (!commandMutex.tryLock()) return@launch
            try {
                _error.value = null
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось выполнить действие"
            } finally {
                commandMutex.unlock()
            }
        }
    }

    fun connect() = execute {
        connectSip(SipAccount(username = "100", password = "qtalk100", domain = "127.0.0.1"))
    }

    fun call(number: String) = execute { voipEngine.call(number) }
    fun hangUp() = execute { voipEngine.hangUp() }
    fun close() = scope.cancel()
}
