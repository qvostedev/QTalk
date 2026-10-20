package com.qvoste.qtalk.voip

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import java.io.BufferedInputStream
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.prefs.Preferences
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class LinphoneVoipEngine : VoipEngine {
    private val linphoneResources = LinphoneResourceInstaller.install()
    private val factory = Factory.instance().apply {
        topResourcesDir = linphoneResources.share.toString()
        liblinphonePluginsDir = linphoneResources.liblinphonePlugins.toString()
        mspluginsDir = linphoneResources.mediastreamerPlugins.toString()
    }

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
    private var coreStarted = false
    private var currentAccount: Account? = null
    private var currentCall: Call? = null
    private val _connectionStatus = MutableStateFlow(TelephonyConnection.DISCONNECTED)
    override val connectionStatus = _connectionStatus.asStateFlow()
    private var registrationStartedAt = 0L
    private var retryAt = 0L
    private var retryAttempt = 0
    private var lastNetworkCheckAt = 0L
    private var networkAvailable = true
    private var lastRegistrationOkAt = 0L
    private val _callStatus = MutableStateFlow(CallStatus())
    override val callStatus = _callStatus.asStateFlow()
    private val audioPreferences = Preferences.userRoot().node("com/qvoste/qtalk/audio")
    private val ringtonePreviewLock = Any()
    private var ringtonePreview: Clip? = null
    private val _audioSettings = MutableStateFlow(
        AudioSettings(
            microphoneSensitivity = audioPreferences.getFloat("microphoneSensitivity", 0.5f),
            speakerVolume = audioPreferences.getFloat("speakerVolume", 0.5f),
            ringtoneVolume = audioPreferences.getFloat("ringtoneVolume", 0.5f),
            selectedRingtoneId = storedRingtoneId()
        )
    )
    override val audioSettings = _audioSettings.asStateFlow()

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
                _callStatus.value = CallStatus(
                    state = CallState.INCOMING,
                    number = caller,
                    message = message,
                    startedAtEpochMillis = System.currentTimeMillis()
                )
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
            val previous = _callStatus.value
            _callStatus.value = previous.copy(
                state = next,
                message = message,
                connectedAtEpochMillis = if (next == CallState.ACTIVE && previous.connectedAtEpochMillis == 0L) {
                    System.currentTimeMillis()
                } else {
                    previous.connectedAtEpochMillis
                }
            )
        }

        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: LinphoneRegistrationState,
            message: String
        ) {
            if (account != currentAccount) return
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
                        registrationDuringRefresh(_registrationState.value)

                    LinphoneRegistrationState.Ok ->
                        RegistrationState.REGISTERED

                    LinphoneRegistrationState.Failed ->
                        RegistrationState.FAILED

                    LinphoneRegistrationState.Cleared ->
                        RegistrationState.DISCONNECTED

                    LinphoneRegistrationState.None ->
                        RegistrationState.DISCONNECTED
                }
            when (state) {
                LinphoneRegistrationState.Ok -> {
                    _connectionStatus.value = TelephonyConnection.CONNECTED
                    lastRegistrationOkAt = System.nanoTime() / 1_000_000
                    registrationStartedAt = 0L
                    retryAttempt = 0
                    retryAt = 0L
                }
                LinphoneRegistrationState.Progress -> {
                    registrationStartedAt = System.nanoTime() / 1_000_000
                    if (!_connectionStatus.value.retryable) _connectionStatus.value = TelephonyConnection.CONNECTING
                }
                LinphoneRegistrationState.Refreshing -> {
                    if (registrationStartedAt == 0L) registrationStartedAt = System.nanoTime() / 1_000_000
                }
                LinphoneRegistrationState.Failed -> {
                    registrationStartedAt = 0L
                    _connectionStatus.value = if (!networkAvailable) TelephonyConnection.NETWORK_UNAVAILABLE
                    else classifyRegistrationFailure(account.errorInfo?.protocolCode ?: 0,
                        account.error in setOf(Reason.Forbidden, Reason.Unauthorized))
                    retryAt = System.nanoTime() / 1_000_000 + reconnectDelayMillis(retryAttempt++)
                }
                LinphoneRegistrationState.Cleared, LinphoneRegistrationState.None -> {
                    _connectionStatus.value = TelephonyConnection.DISCONNECTED
                    registrationStartedAt = 0L
                }
                else -> Unit
            }
        }
    }


    override suspend fun start() = withContext(linphoneDispatcher) {
        if (iterateJob?.isActive == true) {
            return@withContext
        }

        println("QTALK: starting Linphone Core")

        if (!coreStarted) {
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
            coreStarted = true
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
        refreshAudioDevicesInternal()
        println("QTALK: audio input = ${core.defaultInputAudioDevice?.id}")
        println("QTALK: audio output = ${core.defaultOutputAudioDevice?.id}")
        println("QTALK: Linphone Core started")

        iterateJob = scope.launch {
            println("QTALK: iterate loop started")

            while (isActive) {
                core.iterate()
                checkTelephonyConnection()
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
            _connectionStatus.value = TelephonyConnection.CONNECTING
            registrationStartedAt = System.nanoTime() / 1_000_000
            retryAttempt = 0
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


    override suspend fun retryRegistration() = withContext(linphoneDispatcher) {
        checkNotNull(currentAccount) { "Сначала настройте SIP-аккаунт" }
        if (!hasActiveNetwork()) {
            _connectionStatus.value = TelephonyConnection.NETWORK_UNAVAILABLE
            return@withContext
        }
        refreshRegistrationNow()
    }

    private fun refreshRegistrationNow() {
        registrationStartedAt = System.nanoTime() / 1_000_000
        if (_registrationState.value != RegistrationState.REGISTERED) {
            _registrationState.value = RegistrationState.CONNECTING
        }
        currentAccount?.refreshRegister()
    }

    private fun hasActiveNetwork(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().any {
            it.isUp && !it.isLoopback && it.inetAddresses.toList().any { address -> !address.isLoopbackAddress }
        }
    }.getOrDefault(true)

    private fun checkTelephonyConnection() {
        if (currentAccount == null) return
        val now = System.nanoTime() / 1_000_000
        if (now - lastNetworkCheckAt >= 5_000L) {
            lastNetworkCheckAt = now
            val available = hasActiveNetwork()
            val restored = available && !networkAvailable
            networkAvailable = available
            if (!available && _connectionStatus.value !in setOf(
                    TelephonyConnection.AUTHENTICATION_FAILED, TelephonyConnection.REGISTRATION_REJECTED)) {
                _connectionStatus.value = TelephonyConnection.NETWORK_UNAVAILABLE
                _registrationState.value = RegistrationState.FAILED
                registrationStartedAt = 0L
            } else if (restored && _connectionStatus.value.retryable) {
                refreshRegistrationNow()
            }
        }
        if (!networkAvailable) return
        if (registrationStartedAt != 0L && now - registrationStartedAt >= 30_000L) {
            registrationStartedAt = 0L
            _registrationState.value = RegistrationState.FAILED
            _connectionStatus.value = TelephonyConnection.SERVER_UNREACHABLE
            retryAt = now + reconnectDelayMillis(retryAttempt++)
        }
        if (registrationStartedAt == 0L && _connectionStatus.value.retryable && now >= retryAt) {
            refreshRegistrationNow()
        } else if (registrationStartedAt == 0L && _connectionStatus.value == TelephonyConnection.CONNECTED &&
            now - lastRegistrationOkAt >= 60_000L) {
            refreshRegistrationNow()
        }
    }

    override suspend fun call(number: String) = withContext(linphoneDispatcher) {
        check(_registrationState.value == RegistrationState.REGISTERED) { "Сначала подключитесь к SIP-серверу" }
        check(currentCall == null) { "Предыдущий звонок ещё не завершён" }
        val extension = normalizeOutgoingNumber(number)
        val server = checkNotNull(currentAccount?.params?.serverAddress).clone()
        server.username = extension
        val params = checkNotNull(core.createCallParams(null))
        params.isVideoEnabled = false
        _callStatus.value = CallStatus(
            state = CallState.DIALING,
            number = extension,
            startedAtEpochMillis = System.currentTimeMillis()
        )
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

    override suspend fun sendDtmf(digit: Char) = withContext(linphoneDispatcher) {
        require(digit in "0123456789*#") { "Недопустимый DTMF-символ" }
        val call = checkNotNull(currentCall) { "Нет активного звонка" }
        check(_callStatus.value.state == CallState.ACTIVE) { "Звонок ещё не установлен" }
        if (call.sendDtmf(digit) != 0) error("Не удалось отправить DTMF")
    }

    override suspend fun setMicrophoneMuted(muted: Boolean) = withContext(linphoneDispatcher) {
        val call = checkNotNull(currentCall) { "Нет активного звонка" }
        call.setMicrophoneMuted(muted)
    }

    override suspend fun setSpeakerMuted(muted: Boolean) = withContext(linphoneDispatcher) {
        val call = checkNotNull(currentCall) { "Нет активного звонка" }
        call.setSpeakerMuted(muted)
    }

    override suspend fun unregister() =
        withContext(linphoneDispatcher) {
            println("QTALK: unregister()")

            val account = currentAccount ?: return@withContext
            val params = account.params.clone()
            params.isRegisterEnabled = false
            account.params = params
            currentAccount = null
            _registrationState.value = RegistrationState.DISCONNECTED
            _connectionStatus.value = TelephonyConnection.DISCONNECTED
            registrationStartedAt = 0L
        }


    override suspend fun stop() {
        stopRingtonePreview()
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

            if (coreStarted) {
                core.stop()
                core.removeListener(coreListener)
                coreStarted = false
            }
            currentCall = null
            currentAccount = null
            _callStatus.value = CallStatus()
            _registrationState.value = RegistrationState.DISCONNECTED
            _connectionStatus.value = TelephonyConnection.DISCONNECTED
            registrationStartedAt = 0L
            _registrationMessage.value = ""

            println("QTALK: Linphone Core stopped")
        }
    }

    override suspend fun refreshAudioDevices() = withContext(linphoneDispatcher) {
        refreshAudioDevicesInternal()
    }

    override suspend fun selectInputAudioDevice(deviceId: String) = withContext(linphoneDispatcher) {
        val device = core.extendedAudioDevices.firstOrNull {
            it.id == deviceId && it.hasCapability(AudioDevice.Capabilities.CapabilityRecord)
        } ?: return@withContext

        core.defaultInputAudioDevice = device
        core.inputAudioDevice = device
        audioPreferences.put("inputDeviceId", device.id)
        audioPreferences.flush()
        _audioSettings.value = _audioSettings.value.copy(selectedInputDeviceId = device.id)
    }

    override suspend fun selectOutputAudioDevice(deviceId: String) = withContext(linphoneDispatcher) {
        val device = core.extendedAudioDevices.firstOrNull {
            it.id == deviceId && it.hasCapability(AudioDevice.Capabilities.CapabilityPlay)
        } ?: return@withContext

        core.defaultOutputAudioDevice = device
        core.outputAudioDevice = device
        core.setRingerDevice(device.id)
        audioPreferences.put("outputDeviceId", device.id)
        audioPreferences.flush()
        _audioSettings.value = _audioSettings.value.copy(selectedOutputDeviceId = device.id)
    }

    override suspend fun setMicrophoneSensitivity(value: Float) = withContext(linphoneDispatcher) {
        val normalized = value.coerceIn(0f, 1f)
        core.micGainDb = normalized * 60f - 30f
        audioPreferences.putFloat("microphoneSensitivity", normalized)
        audioPreferences.flush()
        _audioSettings.value = _audioSettings.value.copy(microphoneSensitivity = normalized)
    }

    override suspend fun setSpeakerVolume(value: Float) = withContext(linphoneDispatcher) {
        val normalized = value.coerceIn(0f, 1f)
        core.playbackGainDb = normalized * 60f - 30f
        audioPreferences.putFloat("speakerVolume", normalized)
        audioPreferences.flush()
        _audioSettings.value = _audioSettings.value.copy(speakerVolume = normalized)
    }

    override suspend fun setRingtoneVolume(value: Float) = withContext(linphoneDispatcher) {
        val normalized = value.coerceIn(0f, 1f)
        applyRingtoneVolume(normalized)
        audioPreferences.putFloat("ringtoneVolume", normalized)
        audioPreferences.flush()
        _audioSettings.value = _audioSettings.value.copy(ringtoneVolume = normalized)
    }

    override suspend fun previewRingtone(ringtoneId: String) = withContext(Dispatchers.IO) {
        val ringtone = QTalkRingtones.firstOrNull { it.id == ringtoneId } ?: return@withContext
        val resource = javaClass.classLoader.getResourceAsStream("ringtones/${ringtone.id}.wav")
            ?: return@withContext
        val clip = AudioSystem.getClip()
        resource.use { input ->
            BufferedInputStream(input).use { buffered ->
                AudioSystem.getAudioInputStream(buffered).use(clip::open)
            }
        }

        val volume = _audioSettings.value.ringtoneVolume.coerceIn(0f, 1f)
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val decibels = if (volume <= 0f) gain.minimum else (20f * log10(volume)).coerceIn(gain.minimum, gain.maximum)
            gain.value = decibels
        }

        synchronized(ringtonePreviewLock) {
            ringtonePreview?.stop()
            ringtonePreview?.close()
            ringtonePreview = clip
            clip.start()
        }

        Thread({
            Thread.sleep(minOf(2_000L, clip.microsecondLength / 1_000L))
            synchronized(ringtonePreviewLock) {
                if (ringtonePreview === clip) {
                    clip.stop()
                    clip.close()
                    ringtonePreview = null
                }
            }
        }, "QTalk-RingtonePreview").apply {
            isDaemon = true
            start()
        }
    }

    override suspend fun selectRingtone(ringtoneId: String) = withContext(linphoneDispatcher) {
        val ringtone = QTalkRingtones.firstOrNull { it.id == ringtoneId } ?: return@withContext
        val volume = _audioSettings.value.ringtoneVolume.coerceIn(0f, 1f)
        audioPreferences.put("ringtoneId", ringtone.id)
        audioPreferences.flush()
        applyRingtoneVolume(volume, ringtone.id)
        _audioSettings.value = _audioSettings.value.copy(selectedRingtoneId = ringtone.id)
    }

    private fun stopRingtonePreview() {
        synchronized(ringtonePreviewLock) {
            ringtonePreview?.stop()
            ringtonePreview?.close()
            ringtonePreview = null
        }
    }

    private fun refreshAudioDevicesInternal() {
        val devices = core.extendedAudioDevices.toList()
        val inputDevices = devices
            .filter { it.hasCapability(AudioDevice.Capabilities.CapabilityRecord) }
            .distinctBy(AudioDevice::getId)
        val outputDevices = devices
            .filter { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) }
            .distinctBy(AudioDevice::getId)

        val preferredInputId = audioPreferences.get("inputDeviceId", "")
        val preferredOutputId = audioPreferences.get("outputDeviceId", "")
        val selectedInput = inputDevices.firstOrNull { it.id == preferredInputId }
            ?: core.defaultInputAudioDevice
            ?: inputDevices.firstOrNull()
        val selectedOutput = outputDevices.firstOrNull { it.id == preferredOutputId }
            ?: core.defaultOutputAudioDevice
            ?: outputDevices.firstOrNull()

        selectedInput?.let { core.defaultInputAudioDevice = it }
        selectedOutput?.let {
            core.defaultOutputAudioDevice = it
            core.setRingerDevice(it.id)
        }

        val microphoneSensitivity = audioPreferences
            .getFloat("microphoneSensitivity", 0.5f)
            .coerceIn(0f, 1f)
        val speakerVolume = audioPreferences
            .getFloat("speakerVolume", 0.5f)
            .coerceIn(0f, 1f)
        val ringtoneVolume = audioPreferences
            .getFloat("ringtoneVolume", 0.5f)
            .coerceIn(0f, 1f)
        val ringtoneId = storedRingtoneId()
        core.micGainDb = microphoneSensitivity * 60f - 30f
        core.playbackGainDb = speakerVolume * 60f - 30f
        applyRingtoneVolume(ringtoneVolume, ringtoneId)

        _audioSettings.value = AudioSettings(
            inputDevices = inputDevices.map {
                AudioDeviceInfo(it.id, readableAudioDeviceName(it.deviceName, it.id, "Микрофон"))
            },
            outputDevices = outputDevices.map {
                AudioDeviceInfo(it.id, readableAudioDeviceName(it.deviceName, it.id, "Динамики"))
            },
            selectedInputDeviceId = selectedInput?.id,
            selectedOutputDeviceId = selectedOutput?.id,
            microphoneSensitivity = microphoneSensitivity,
            speakerVolume = speakerVolume,
            ringtoneVolume = ringtoneVolume,
            selectedRingtoneId = ringtoneId
        )
    }

    private fun applyRingtoneVolume(value: Float, ringtoneId: String = storedRingtoneId()) {
        val source = bundledRingtone(ringtoneId)
        if (value >= 1f) {
            core.ring = source.toString()
            return
        }
        val level = (value * 100f).roundToInt()
        val destination = Path.of(factory.getDataDir(null), "qtalk-ringtones", "$ringtoneId-$level.wav")
        writeRingtoneAtVolume(source, destination, value)
        core.ring = destination.toString()
    }

    private fun storedRingtoneId(): String = audioPreferences
        .get("ringtoneId", QTalkRingtones.first().id)
        .takeIf { stored -> QTalkRingtones.any { it.id == stored } }
        ?: QTalkRingtones.first().id

    private fun bundledRingtone(ringtoneId: String): Path {
        val selected = QTalkRingtones.firstOrNull { it.id == ringtoneId } ?: QTalkRingtones.first()
        val destination = Path.of(factory.getDataDir(null), "qtalk-ringtones", "${selected.id}-original.wav")
        Files.createDirectories(destination.parent)
        val input = javaClass.classLoader.getResourceAsStream("ringtones/${selected.id}.wav")
            ?: error("Bundled QTalk ringtone is unavailable: ${selected.id}")
        input.use {
            Files.copy(it, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return destination
    }

}

internal fun readableAudioDeviceName(rawName: String?, id: String, fallback: String): String {
    val original = rawName.orEmpty().trim()
    if (original.isBlank()) return id.ifBlank { fallback }

    val isDefault = original.equals("Default Capture", ignoreCase = true) ||
        original.equals("Default Playback", ignoreCase = true)
    if (isDefault) {
        return if (fallback == "Микрофон") "Системный микрофон" else "Системные динамики"
    }

    val brokenSequence = Regex("(?:\\uFFFD|\\?){2,}")
    if (brokenSequence.containsMatchIn(original)) {
        val readablePart = original.replace(brokenSequence, "")
            .trim().trimStart('-', '–', '—').trim()
        return if (readablePart.isBlank()) fallback else "$fallback $readablePart"
    }

    return original
}

internal fun normalizeOutgoingNumber(number: String): String {
    val normalized = number.filterNot(Char::isWhitespace)
    require(normalized.matches(Regex("\\+?[0-9]{1,20}"))) { "Введите номер" }
    return normalized
}
