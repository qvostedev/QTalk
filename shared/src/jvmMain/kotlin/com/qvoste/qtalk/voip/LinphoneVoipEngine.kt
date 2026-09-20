package com.qvoste.qtalk.voip

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState as LinphoneRegistrationState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

class LinphoneVoipEngine : VoipEngine {
    private val factory = Factory.instance()

    private val core by lazy {
        Files.createDirectories(Path.of(factory.getDataDir(null)))
        factory.createCore(null, null, null)
    }

    // Один отдельный поток и все операции с Core выполняются только через него.
    private val linphoneDispatcher: CoroutineDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "QTalk-Linphone")
            }
            .asCoroutineDispatcher()

    private val scope = CoroutineScope(linphoneDispatcher)
    private var iterateJob: Job? = null
    private var currentAccount: Account? = null

    private val _registrationState =
        MutableStateFlow(RegistrationState.DISCONNECTED)
    override val registrationState: StateFlow<RegistrationState> =
        _registrationState.asStateFlow()

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: LinphoneRegistrationState,
            message: String
        ) {
            println("QTALK: registration = $state")
            println("QTALK: registration message = $message")

            _registrationState.value =
                when (state) {
                    LinphoneRegistrationState.Progress ->
                        RegistrationState.CONNECTING

                    LinphoneRegistrationState.Refreshing ->
                        RegistrationState.CONNECTING

                    LinphoneRegistrationState.Ok ->
                        RegistrationState.REGISTERED

                    LinphoneRegistrationState.Failed ->
                        RegistrationState.FAILED

                    LinphoneRegistrationState.Cleared ->
                        RegistrationState.DISCONNECTED

                    LinphoneRegistrationState.None ->
                        RegistrationState.DISCONNECTED
                }
        }
    }


    override suspend fun start() = withContext(linphoneDispatcher) {
        if (iterateJob?.isActive == true) {
            return@withContext
        }

        println("QTALK: starting Linphone Core")

        core.addListener(coreListener)

        // Локальный 5060 занят Asterisk; -1 выбирает свободный порт.
        val transports = core.transports
        transports.udpPort = -1
        core.setTransports(transports)

        val startResult = core.start()
        if (startResult != 0) {
            core.removeListener(coreListener)
            _registrationState.value = RegistrationState.FAILED
            error("Unable to start Linphone Core: $startResult")
        }

        println("QTALK: Linphone Core started")

        iterateJob = scope.launch {
            println("QTALK: iterate loop started")

            while (isActive) {
                core.iterate()
                delay(20.milliseconds)
            }
        }
    }


    override suspend fun register(account: SipAccount) =
        withContext(linphoneDispatcher) {
            println("QTALK: register() called")
            println("QTALK: username = ${account.username}")
            println("QTALK: domain = ${account.domain}")

            currentAccount?.let {
                println("QTALK: removing previous account")
                core.removeAccount(it)
                currentAccount = null
            }

            _registrationState.value =
                RegistrationState.CONNECTING

            val authInfo = factory.createAuthInfo(
                account.username,
                null,
                account.password,
                null,
                null,
                account.domain
            )

            core.addAuthInfo(authInfo)
            println("QTALK: AuthInfo added")


            // SIP-адрес для пользователя 100: sip:100@127.0.0.1
            val identity = factory.createAddress(
                "sip:${account.username}@${account.domain}"
            ) ?: error("Unable to create SIP identity")


            // Asterisk UDP, порт 5060
            val serverAddress = factory.createAddress(
                "sip:${account.domain}:5060;transport=udp"
            ) ?: error("Unable to create SIP server address")

            println(
                "QTALK: identity = ${identity.asStringUriOnly()}"
            )

            println(
                "QTALK: server = ${serverAddress.asStringUriOnly()}"
            )

            val params = core.createAccountParams()
            params.identityAddress = identity
            params.serverAddress = serverAddress
            params.isRegisterEnabled = true

            val linphoneAccount = core.createAccount(params)
            currentAccount = linphoneAccount
            core.addAccount(linphoneAccount)
            core.defaultAccount = linphoneAccount

            println("QTALK: Linphone account added")
        }


    override suspend fun unregister() =
        withContext(linphoneDispatcher) {
            println("QTALK: unregister()")

            val account = currentAccount ?: return@withContext
            val params = account.params.clone()
            params.isRegisterEnabled = false
            account.params = params
            currentAccount = null
        }


    override suspend fun stop() {
        iterateJob?.cancelAndJoin()
        iterateJob = null

        withContext(linphoneDispatcher) {
            println("QTALK: stopping Linphone Core")

            core.removeListener(coreListener)
            core.stop()
            _registrationState.value = RegistrationState.DISCONNECTED

            println("QTALK: Linphone Core stopped")
        }
    }
}
