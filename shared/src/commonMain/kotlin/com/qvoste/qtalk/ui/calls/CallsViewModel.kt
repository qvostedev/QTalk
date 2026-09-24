package com.qvoste.qtalk.ui.calls

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.voip.SipAccount
import com.qvoste.qtalk.voip.SipAccountStore
import com.qvoste.qtalk.voip.VoipEngine
import com.qvoste.qtalk.voip.RegistrationState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class CallsViewModel(
    private val voipEngine: VoipEngine,
    private val connectSip: ConnectSipUseCase,
    private val accountStore: SipAccountStore
) {
    val registrationState = voipEngine.registrationState
    val registrationMessage = voipEngine.registrationMessage
    val callStatus = voipEngine.callStatus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandMutex = Mutex()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _accountNumber = MutableStateFlow("")
    val accountNumber = _accountNumber.asStateFlow()
    private val savedAccount = accountStore.load()
    private val _authenticationRequired = MutableStateFlow(savedAccount == null)
    val authenticationRequired = _authenticationRequired.asStateFlow()
    private val _accountDraft = MutableStateFlow(savedAccount)
    val accountDraft = _accountDraft.asStateFlow()
    private var pendingAccount: SipAccount? = null

    init {
        scope.launch {
            registrationState.collectLatest { state ->
                when (state) {
                    RegistrationState.REGISTERED -> pendingAccount?.let { account ->
                        accountStore.save(account)
                        _accountDraft.value = account.copy(password = null)
                        _authenticationRequired.value = false
                        pendingAccount = null
                    }
                    RegistrationState.FAILED -> _authenticationRequired.value = true
                    else -> Unit
                }
            }
        }

        savedAccount?.let(::connect)
    }

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

    fun connect(username: String, password: String, domain: String) = connect(
        SipAccount(
            username = username.trim(), password = password, domain = domain.trim()
        )
    )

    fun connect(account: SipAccount) = execute {
        _accountNumber.value = account.username.trim()
        _accountDraft.value = account
        pendingAccount = account
        connectSip(account)
    }

    fun logout() = execute {
        voipEngine.unregister()
        accountStore.clear()
        pendingAccount = null
        _accountDraft.value = null
        _accountNumber.value = ""
        _authenticationRequired.value = true
    }

    fun call(number: String) = execute { voipEngine.call(number) }
    fun acceptCall() = execute { voipEngine.acceptCall() }
    fun declineCall() = execute { voipEngine.declineCall() }
    fun hangUp() = execute { voipEngine.hangUp() }
    fun close() = scope.cancel()
}
