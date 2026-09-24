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
import org.linphone.core.Call
import org.linphone.core.AudioDevice
import org.linphone.core.Reason
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
    private var currentCall: Call? = null
    private val _callStatus = MutableStateFlow(CallStatus())
    override val callStatus = _callStatus.asStateFlow()

    private val _registrationState =
        MutableStateFlow(RegistrationState.DISCONNECTED)
    override val registrationState: StateFlow<RegistrationState> =
        _registrationState.asStateFlow()
    private val _registrationMessage = MutableStateFlow("")
    override val registrationMessage: StateFlow<String> =
        _registrationMessage.asStateFlow()

    private val coreListener = object : CoreListenerStub() {
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            if (state == Call.State.IncomingReceived) {
                if (currentCall != null) {
                    call.decline(Reason.Busy)
                    return
                }

                currentCall = call
                val caller = call.remoteAddress?.username.orEmpty()
                _callStatus.value = CallStatus(CallState.INCOMING, caller, message)
                return
            }

            if (state == Call.State.OutgoingInit && currentCall == null) currentCall = call
            if (call != currentCall) return
            println("QTALK: call = $state, $message")
            val next = when (state) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> CallState.DIALING
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> CallState.RINGING
                Call.State.Connected, Call.State.StreamsRunning -> CallState.ACTIVE
                Call.State.Error -> CallState.FAILED
                Call.State.End -> if (_callStatus.value.state == CallState.FAILED) CallState.FAILED else CallState.ENDED
                Call.State.Released -> {
                    currentCall = null
                    return
                }
                else -> return
            }
            _callStatus.value = _callStatus.value.copy(state = next, message = message)
        }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: LinphoneRegistrationState,
            message: String
        ) {
            println("QTALK: registration = $state")
            println("QTALK: registration message = $message")

            val errorPhrase = account.errorInfo?.phrase.orEmpty()
            _registrationMessage.value = if (errorPhrase.isNotBlank()) {
                "$message: $errorPhrase"
            } else {
                message
            }

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

        val initialTransports = core.transports
        initialTransports.udpPort = 0
        initialTransports.tcpPort = 0
        initialTransports.tlsPort = 0
        check(core.setTransports(initialTransports) == 0) { "Не удалось подготовить SIP-транспорт" }

        val startResult = core.start()
        if (startResult != 0) {
            core.removeListener(coreListener)
            _registrationState.value = RegistrationState.FAILED
            error("Unable to start Linphone Core: $startResult")
        }

        // На Windows автоопределение сети иногда не срабатывает.
        core.registerOnlyWhenNetworkIsUp = false
        core.isNetworkReachable = true
        core.setSipNetworkReachable(true)
        core.setMediaNetworkReachable(true)

        // ALSA default на Linux передаёт выбор микрофона и выхода PipeWire/PulseAudio.
        if (System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) {
            core.extendedAudioDevices.firstOrNull {
                it.driverName == "ALSA" && it.deviceName == "default" &&
                    it.hasCapability(AudioDevice.Capabilities.CapabilityRecord) &&
                    it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
            }?.let { device ->
                core.defaultInputAudioDevice = device
                core.defaultOutputAudioDevice = device
            }
        }
        println("QTALK: audio input = ${core.defaultInputAudioDevice?.id}")
        println("QTALK: audio output = ${core.defaultOutputAudioDevice?.id}")
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
            start()
            require(account.username.matches(Regex("[0-9]{1,20}"))) { "Введите номер учётной записи" }
            require(!account.password.isNullOrBlank() || !account.ha1.isNullOrBlank()) { "Введите пароль или HA1" }
            require(account.domain.isNotBlank()) { "Введите адрес SIP-сервера" }
            require(account.port in 1..65535) { "Порт должен быть от 1 до 65535" }
            require(account.registrationExpires >= 60) { "Срок регистрации должен быть не меньше 60 секунд" }
            println("QTALK: register() called")
            println("QTALK: username = ${account.username}")
            println("QTALK: domain = ${account.domain}")

            currentAccount?.let {
                println("QTALK: removing previous account")
                core.removeAccount(it)
                currentAccount = null
            }
            core.clearAllAuthInfo()

            _registrationState.value =
                RegistrationState.CONNECTING
            _registrationMessage.value = "Подключение к ${account.domain}"

            val transports = core.transports
            transports.udpPort = if (account.transport == SipTransport.UDP) -1 else 0
            transports.tcpPort = if (account.transport == SipTransport.TCP) -1 else 0
            transports.tlsPort = if (account.transport == SipTransport.TLS) -1 else 0
            check(core.setTransports(transports) == 0) { "Не удалось настроить SIP-транспорт" }

            val authInfo = factory.createAuthInfo(
                account.username,
                null,
                account.password,
                account.ha1,
                account.realm,
                account.domain,
                "MD5"
            )

            core.addAuthInfo(authInfo)
            println("QTALK: AuthInfo added")


            // SIP-адрес для пользователя 100: sip:100@127.0.0.1
            val identity = factory.createAddress(
                "sip:${account.username}@${account.domain}"
            ) ?: error("Unable to create SIP identity")


            val serverAddress = factory.createAddress(
                "sip:${account.domain}:${account.port};transport=${account.transport.uriValue}"
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
            params.expires = account.registrationExpires
            params.realm = account.realm

            val linphoneAccount = core.createAccount(params)
            currentAccount = linphoneAccount
            core.addAccount(linphoneAccount)
            core.defaultAccount = linphoneAccount

            println("QTALK: Linphone account added")
        }


    override suspend fun call(number: String) = withContext(linphoneDispatcher) {
        check(_registrationState.value == RegistrationState.REGISTERED) { "Сначала подключитесь к SIP-серверу" }
        check(currentCall == null) { "Предыдущий звонок ещё не завершён" }
        val extension = number.trim()

        require(extension.matches(Regex("[0-9]{1,20}"))) { "Введите номер" }
        val server = checkNotNull(currentAccount?.params?.serverAddress).clone()
        server.username = extension
        val params = checkNotNull(core.createCallParams(null))
        params.isVideoEnabled = false
        _callStatus.value = CallStatus(CallState.DIALING, extension)
        val call = core.inviteAddressWithParams(server, params)
        if (call == null) {
            _callStatus.value = CallStatus(CallState.FAILED, extension, "Не удалось начать звонок")
        } else {
            currentCall = call
        }
    }

    override suspend fun acceptCall() = withContext(linphoneDispatcher) {
        val call = checkNotNull(currentCall) { "Нет входящего звонка" }
        check(_callStatus.value.state == CallState.INCOMING) { "Звонок уже обработан" }

        val params = checkNotNull(core.createCallParams(call))
        params.isVideoEnabled = false
        if (call.acceptWithParams(params) != 0) {
            error("Не удалось принять звонок")
        }
    }

    override suspend fun declineCall() = withContext(linphoneDispatcher) {
        val call = checkNotNull(currentCall) { "Нет входящего звонка" }
        check(_callStatus.value.state == CallState.INCOMING) { "Звонок уже обработан" }

        if (call.decline(Reason.Declined) != 0) {
            error("Не удалось отклонить звонок")
        }
    }

    override suspend fun hangUp() = withContext(linphoneDispatcher) {
        val call = currentCall ?: return@withContext
        val previous = _callStatus.value
        _callStatus.value = previous.copy(state = CallState.ENDING)
        if (call.terminate() != 0) {
            _callStatus.value = previous
            error("Не удалось завершить звонок")
        }
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

            currentAccount?.let { account ->
                val params = account.params.clone()
                params.isRegisterEnabled = false
                account.params = params
                repeat(5) {
                    core.iterate()
                    delay(20.milliseconds)
                }
            }

            core.stop()
            core.removeListener(coreListener)
            currentCall = null
            currentAccount = null
            _callStatus.value = CallStatus()
            _registrationState.value = RegistrationState.DISCONNECTED
            _registrationMessage.value = ""

            println("QTALK: Linphone Core stopped")
        }
    }
}
