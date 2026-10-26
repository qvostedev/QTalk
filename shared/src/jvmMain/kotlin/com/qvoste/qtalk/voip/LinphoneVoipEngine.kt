package com.qvoste.qtalk.voip
import com.qvoste.qtalk.ui.calls.components.customRingtones
import com.qvoste.qtalk.ui.calls.components.localMediaDirectory

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
import org.linphone.core.Conference
import org.linphone.core.InfoMessage
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
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

private fun conferenceLegState(state: Call.State): ConferenceLegState? = when (state) {
    Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.Connected -> ConferenceLegState.DIALING
    Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> ConferenceLegState.RINGING
    Call.State.StreamsRunning -> ConferenceLegState.ACTIVE
    Call.State.Pausing -> ConferenceLegState.PAUSING
    Call.State.Paused -> ConferenceLegState.PAUSED
    Call.State.End -> ConferenceLegState.ENDED
    Call.State.Error -> ConferenceLegState.FAILED
    Call.State.Released -> ConferenceLegState.RELEASED
    else -> null
}

class LinphoneVoipEngine : VoipEngine {
    private val logger = java.util.logging.Logger.getLogger(LinphoneVoipEngine::class.java.name)
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
    private var localConference: Conference? = null
    private var conferenceInviteJob: Job? = null
    private var microphoneMuted = false
    private var speakerMuted = false
    private var conferenceHeld = false
    private var localSpeechUntil = 0L
    private val remoteSpeechUntil = mutableMapOf<String, Long>()
    private var lastConferenceAudioAt = 0L
    private var roomId = ""
    private var roomRevision = 0L
    private var guestRoom: RoomMessage.Snapshot? = null
    private var roomLastSentAt = 0L
    private var lastRoomSnapshot: RoomMessage.Snapshot? = null
    private var roomInviteQueue = emptyList<String>()
    private var roomInviteJob: Job? = null
    private val reportedMutes = mutableMapOf<Call, Boolean>()
    private val roomSentStates = mutableMapOf<Call, Call.State>()
    private val roomPeerSince = mutableMapOf<Call, Long>()
    private var roomService = GoogleSheetsConferenceService("")
    private var roomServiceJob: Job? = null
    private var roomOwnerToken = UUID.randomUUID().toString()
    private var cloudSnapshot: RoomMessage.Snapshot? = null
    private var guestLastSeenAt = 0L
    private var cloudLastSuccessAt = 0L
    private var lanRoom: LanConferenceChannel? = null
    private var lanRoomJob: Job? = null
    private var lastLanReceivedAt = 0L
    private var lanAcknowledgement = ""
    private var roomMaintenanceJob: Job? = null
    private var roomTransitionJob: Job? = null
    private var ignoredGuestRoom = ""
    private data class Handoff(val id: String, val host: String, val members: List<String>, val expiresAt: Long,
        val microphoneMuted: Boolean, val speakerMuted: Boolean, val committed: Boolean = false)
    private var handoff: Handoff? = null
    private val _conferenceStatus = MutableStateFlow(ConferenceStatus())
    private val conferenceSession = LocalConferenceSession(object : LocalConferenceBackend<Call> {
        override fun number(call: Call): String = call.remoteAddress?.username.orEmpty()
        override fun state(call: Call): ConferenceLegState = conferenceLegState(call.state) ?: ConferenceLegState.PAUSING
        override fun pause(call: Call) { check(call.pause() == 0) { "Не удалось поставить первый звонок на удержание" } }
        override fun prepareConference(call: Call): Boolean {
            val conference = ensureLocalConference()
            if (call.conference != conference) check(conference.addParticipant(call) == 0) { "Не удалось подключить звук конференции" }
            check(conference.enter() == 0) { "Не удалось подключить звук конференции" }
            conference.microphoneMuted = microphoneMuted
            return true
        }
        override fun resume(call: Call) {
            val conference = localConference
            val result = if (conference != null && call.conference == conference) conference.enter()
                else if (call.state == Call.State.Paused) call.resume() else 0
            check(result == 0) { "Не удалось вернуться к первому звонку" }
        }
        override fun invite(number: String): Call {
            val address = checkNotNull(currentAccount?.params?.serverAddress).clone()
            address.username = number
            val params = checkNotNull(core.createCallParams(null))
            params.isVideoEnabled = false
            val conference = checkNotNull(localConference)
            val previousCalls = core.calls.toSet()
            conference.inviteParticipants(arrayOf(address), params)
            val invited = core.calls.firstOrNull { it !in previousCalls && it.remoteAddress?.username == number }
            return checkNotNull(invited) { "Не удалось добавить участника" }.also {
                it.setMicrophoneMuted(microphoneMuted)
                it.setSpeakerMuted(speakerMuted)
            }
        }
        override fun merge(first: Call, second: Call) {
            val params = core.createConferenceParams(null).apply {
                isAudioEnabled = true
                isVideoEnabled = false
                isLocalParticipantEnabled = true
                isOneParticipantConferenceEnabled = true
                account = currentAccount
                conferenceFactoryAddress = null
            }
            val previousConference = localConference?.takeIf { it.state in setOf(Conference.State.Instantiated,
                Conference.State.CreationPending, Conference.State.Created) }
            val conference = previousConference ?: checkNotNull(core.createConferenceWithParams(params)) { "Не удалось создать локальную конференцию" }
            localConference = conference
            var addedFirst = false
            var addedSecond = false
            try {
                // The SDK's batch wrapper returns a C++ boolean, unlike addParticipant's status code.
                if (first.conference != conference) {
                    check(conference.addParticipant(first) == 0) { "Не удалось объединить звонки" }
                    addedFirst = true
                }
                if (second.conference != conference) {
                    check(conference.addParticipant(second) == 0) { "Не удалось объединить звонки" }
                    addedSecond = true
                }
                check(conference.enter() == 0) { "Не удалось подключить звук конференции" }
                conference.microphoneMuted = microphoneMuted
            } catch (error: Exception) {
                if (addedFirst) runCatching { conference.removeParticipant(first) }
                if (addedSecond) runCatching { conference.removeParticipant(second) }
                runCatching { if (previousConference != null) conference.enter() else conference.leave() }
                localConference = previousConference
                throw error
            }
        }
        override fun end(call: Call) {
            if (call.state !in setOf(Call.State.End, Call.State.Error, Call.State.Released)) {
                check(call.terminate() == 0) { "Не удалось завершить звонок участника" }
            }
        }
    }, onPrimaryChanged = {
        currentCall = it
        _callStatus.value = _callStatus.value.copy(number = it.remoteAddress?.username.orEmpty())
    }, onFinished = {
        currentCall = null
        localConference = null
        conferenceInviteJob?.cancel()
        _callStatus.value = _callStatus.value.copy(state = CallState.ENDED)
    })
    override val conferenceStatus = _conferenceStatus.asStateFlow()
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
        override fun onInfoReceived(core: Core, call: Call, message: InfoMessage) {
            if (roomService.enabled) return
            val content = message.content ?: return
            if (content.type != "application" || content.subtype != ConferenceRoomWire.subtype) return
            when (val room = ConferenceRoomWire.decode(content.utf8Text.orEmpty())) {
                is RoomMessage.Microphone -> if (room.roomId == roomId && (call in conferenceSession.calls || call == currentCall)) {
                    reportedMutes[call] = room.muted
                }
                is RoomMessage.Snapshot -> {
                    val own = currentAccount?.params?.identityAddress?.username.orEmpty()
                    val remote = call.remoteAddress?.username.orEmpty()
                    if (call != currentCall || conferenceSession.hasSession || roomId.isNotBlank() ||
                        room.host.filter(Char::isDigit) != remote.filter(Char::isDigit) ||
                        (room.members + room.pending).none { it.filter(Char::isDigit) == own.filter(Char::isDigit) }) return
                    val previous = guestRoom
                    if (previous != null && (previous.roomId != room.roomId || room.revision < previous.revision)) return
                    guestRoom = room
                    sendRoomMessage(call, RoomMessage.Microphone(room.roomId, microphoneMuted))
                }
                null -> Unit
            }
        }
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            val conferenceState = conferenceLegState(state)
            if (conferenceState != null && conferenceSession.onCallState(call, conferenceState)) return
            if (state == Call.State.IncomingReceived) {
                val transition = handoff?.takeIf { it.committed && it.expiresAt > System.nanoTime() && it.host == call.remoteAddress?.username }
                if (transition != null) {
                    currentCall?.takeIf { it != call }?.terminate()
                    currentCall = call
                    clearRoom()
                    conferenceSession.reset()
                    microphoneMuted = transition.microphoneMuted
                    speakerMuted = transition.speakerMuted
                    handoff = null
                    _callStatus.value = CallStatus(CallState.ACTIVE, transition.host,
                        startedAtEpochMillis = System.currentTimeMillis(), connectedAtEpochMillis = System.currentTimeMillis())
                    val params = checkNotNull(core.createCallParams(call)).apply { isVideoEnabled = false }
                    if (call.acceptWithParams(params) == 0) {
                        call.setMicrophoneMuted(microphoneMuted); call.setSpeakerMuted(speakerMuted)
                    }
                    return
                }
                if (currentCall != null) {
                    call.decline(Reason.Busy)
                    return
                }

                currentCall = call
                clearRoom()
                conferenceSession.reset()
                microphoneMuted = false
                speakerMuted = false
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
            logger.fine("Call state: $state")
            val next = when (state) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> CallState.DIALING
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> CallState.RINGING
                Call.State.Connected, Call.State.StreamsRunning -> CallState.ACTIVE
                Call.State.Error -> CallState.FAILED
                Call.State.End -> if (_callStatus.value.state == CallState.FAILED) CallState.FAILED else CallState.ENDED
                Call.State.Released -> {
                    currentCall = null
                    localConference = null
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
            logger.fine("Registration state: $state")

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
        core.maxCalls = MAX_CONFERENCE_PARTICIPANTS - 1
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

        iterateJob = scope.launch {

            while (isActive) {
                core.iterate()
                checkTelephonyConnection()
                refreshConferenceAudio()
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

            currentAccount?.let {
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


            val identity = factory.createAddress(
                "sip:${account.username}@${account.domain}"
            ) ?: error("Unable to create SIP identity")


            val serverAddress = factory.createAddress(
                "sip:${account.domain}:${account.port};transport=${account.transport.uriValue}"
            ) ?: error("Unable to create SIP server address")



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
        conferenceSession.reset()
        clearRoom()
        microphoneMuted = false
        speakerMuted = false
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
        handoff = null
        roomTransitionJob?.cancel(); roomTransitionJob = null
        if (conferenceSession.hasSession && conferenceSession.calls.size >= 2) transferConferenceHost()
        if (conferenceSession.hasSession) {
            val previous = _callStatus.value
            _callStatus.value = _callStatus.value.copy(state = CallState.ENDING)
            conferenceInviteJob?.cancel()
            try { conferenceSession.hangUpAll() } catch (error: Exception) {
                if (conferenceSession.hasSession) _callStatus.value = previous
                throw error
            }
            return@withContext
        }
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
        val call = checkNotNull(conferenceSession.foregroundCall ?: currentCall) { "Нет активного звонка" }
        check(_callStatus.value.state == CallState.ACTIVE) { "Звонок ещё не установлен" }
        check(call.state == Call.State.StreamsRunning) { "Звонок ещё не установлен" }
        if (call.sendDtmf(digit) != 0) error("Не удалось отправить DTMF")
    }

    override suspend fun setMicrophoneMuted(muted: Boolean) = withContext(linphoneDispatcher) {
        microphoneMuted = muted
        localConference?.microphoneMuted = muted || conferenceHeld
        val call = checkNotNull(conferenceSession.foregroundCall ?: currentCall) { "Нет активного звонка" }
        call.setMicrophoneMuted(muted || conferenceHeld)
        guestRoom?.let { sendRoomMessage(call, RoomMessage.Microphone(it.roomId, muted)) }
        Unit
    }

    override suspend fun setSpeakerMuted(muted: Boolean) = withContext(linphoneDispatcher) {
        speakerMuted = muted
        val call = checkNotNull(conferenceSession.foregroundCall ?: currentCall) { "Нет активного звонка" }
        (conferenceSession.calls.ifEmpty { listOf(call) }).forEach { it.setSpeakerMuted(muted || conferenceHeld) }
    }

    override suspend fun setConferenceHeld(held: Boolean) = withContext(linphoneDispatcher) {
        check(_conferenceStatus.value.phase != ConferencePhase.NONE) { "Нет группового звонка" }
        localConference?.let { conference ->
            check((if (held) conference.leave() else conference.enter()) == 0) { "Не удалось изменить удержание" }
            conferenceHeld = held
            conference.microphoneMuted = microphoneMuted || held
            return@withContext
        }
        conferenceHeld = held
        (conferenceSession.calls.ifEmpty { listOfNotNull(currentCall) }).forEach {
            it.setMicrophoneMuted(microphoneMuted || held)
            it.setSpeakerMuted(speakerMuted || held)
        }
    }

    private fun ensureLocalConference(): Conference {
        localConference?.takeIf { it.state in setOf(Conference.State.Instantiated,
            Conference.State.CreationPending, Conference.State.Created) }?.let { return it }
        val params = core.createConferenceParams(null).apply {
            isAudioEnabled = true
            isVideoEnabled = false
            isLocalParticipantEnabled = true
            isOneParticipantConferenceEnabled = true
            account = currentAccount
            conferenceFactoryAddress = null
        }
        return checkNotNull(core.createConferenceWithParams(params)) { "Не удалось создать локальную конференцию" }
            .also { localConference = it }
    }

    override suspend fun inviteConferenceParticipant(number: String) = withContext(linphoneDispatcher) {
        check(!conferenceHeld) { "Сначала снимите звонок с удержания" }
        check(guestRoom == null) { "Приглашать участников может только организатор" }
        check(_registrationState.value == RegistrationState.REGISTERED) { "Нет связи с сервером телефонии" }
        val target = normalizeOutgoingNumber(number)
        require(target != currentAccount?.params?.identityAddress?.username) { "Нельзя добавить свой номер" }
        conferenceSession.begin(checkNotNull(currentCall) { "Нет активного звонка" }, target)
        conferenceInviteJob?.cancel()
        conferenceInviteJob = scope.launch {
            delay(45_000)
            if (conferenceStatus.value.phase == ConferencePhase.INVITING) conferenceSession.cancel()
        }
    }

    override suspend fun mergeConference() = withContext(linphoneDispatcher) {
        conferenceSession.merge()
        conferenceInviteJob?.cancel()
        Unit
    }

    override suspend fun cancelConferenceInvite() = withContext(linphoneDispatcher) {
        conferenceInviteJob?.cancel()
        conferenceSession.cancel()
    }

    override suspend fun removeConferenceParticipant(number: String) = withContext(linphoneDispatcher) {
        conferenceSession.removeParticipant(number)
    }

    override suspend fun inviteConferenceParticipants(numbers: List<String>) = withContext(linphoneDispatcher) {
        check(guestRoom == null && roomInviteJob?.isActive != true) { "Дождитесь завершения приглашения" }
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        val existing = conferenceSession.calls.map { it.remoteAddress?.username } + currentCall?.remoteAddress?.username + own
        val targets = numbers.map(::normalizeOutgoingNumber).distinct().filterNot { it in existing }
        require(targets.isNotEmpty() && targets.size + existing.filterNotNull().distinct().size <= MAX_CONFERENCE_PARTICIPANTS) {
            "Достигнут лимит участников"
        }
        if (roomId.isBlank()) roomId = UUID.randomUUID().toString()
        roomInviteQueue = targets
        roomInviteJob = scope.launch {
            try {
                for (target in targets) {
                    if (currentCall == null) break
                    val ready = withTimeoutOrNull(5_000) {
                        while (currentCall != null && currentCall?.state != Call.State.StreamsRunning) delay(50)
                        currentCall != null
                    } == true
                    if (!ready) break
                    try {
                        inviteConferenceParticipant(target)
                        val answered = withTimeoutOrNull(45_000) {
                            while (conferenceSession.status.value.phase == ConferencePhase.INVITING) delay(50)
                            conferenceSession.status.value.phase == ConferencePhase.CONSULTING
                        } == true
                        if (answered) mergeConference()
                        else if (conferenceSession.status.value.phase == ConferencePhase.INVITING) cancelConferenceInvite()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (conferenceSession.status.value.phase in setOf(ConferencePhase.INVITING, ConferencePhase.CONSULTING)) {
                            cancelConferenceInvite()
                        }
                    }
                    roomInviteQueue = roomInviteQueue.filterNot { it == target }
                    val restored = withTimeoutOrNull(5_000) {
                        while (conferenceSession.status.value.phase == ConferencePhase.RESTORING ||
                            currentCall != null && currentCall?.state != Call.State.StreamsRunning) delay(50)
                        true
                    } == true
                    if (!restored) break
                }
            } finally { roomInviteQueue = emptyList() }
        }
        Unit
    }

    override suspend fun startConference(numbers: List<String>) = withContext(linphoneDispatcher) {
        val targets = numbers.map(::normalizeOutgoingNumber).distinct()
        require(targets.size in 2 until MAX_CONFERENCE_PARTICIPANTS &&
            currentAccount?.params?.identityAddress?.username !in targets) { "Выберите участников для группового звонка" }
        call(targets.first())
        roomId = UUID.randomUUID().toString()
        roomInviteJob = scope.launch {
            val active = withTimeoutOrNull(45_000) {
                while (_callStatus.value.state in setOf(CallState.DIALING, CallState.RINGING)) delay(50)
                _callStatus.value.state == CallState.ACTIVE
            } == true
            roomInviteJob = null
            if (active) inviteConferenceParticipants(targets.drop(1))
        }
        Unit
    }

    private fun sendRoomMessage(call: Call, message: RoomMessage): Boolean {
        if (roomService.enabled) return false
        if (call.state in setOf(Call.State.Idle, Call.State.OutgoingInit, Call.State.OutgoingProgress,
                Call.State.End, Call.State.Error, Call.State.Released)) return false
        val content = factory.createContent().apply {
            type = "application"; subtype = ConferenceRoomWire.subtype; utf8Text = ConferenceRoomWire.encode(message)
        }
        val info = core.createInfoMessage().apply { this.content = content }
        return call.sendInfoMessage(info) == 0
    }

    private fun clearRoom() {
        lanRoom?.close(); lanRoom = null
        lastLanReceivedAt = 0L
        lanAcknowledgement = ""
        conferenceHeld = false
        localSpeechUntil = 0L
        remoteSpeechUntil.clear()
        val closingId = roomId
        val closingToken = roomOwnerToken
        val service = roomService
        if (closingId.isNotBlank() && service.enabled) scope.launch {
            try { service.request("roomClose", mapOf("role" to "host", "room" to closingId, "token" to closingToken)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The server also expires abandoned rooms. */ }
        }
        cloudSnapshot = null
        roomOwnerToken = UUID.randomUUID().toString()
        guestLastSeenAt = 0L
        cloudLastSuccessAt = 0L
        roomInviteJob?.cancel(); roomInviteJob = null; roomInviteQueue = emptyList()
        roomId = ""; guestRoom = null; roomRevision = 0L; roomLastSentAt = 0L; lastRoomSnapshot = null
        reportedMutes.clear()
        roomSentStates.clear()
        roomPeerSince.clear()
    }

    private fun refreshConferenceAudio() {
        val now = System.nanoTime()
        if (now - lastConferenceAudioAt < 150_000_000L) return
        lastConferenceAudioAt = now
        val conference = localConference?.takeIf { conferenceSession.hasSession }
        val participants = conference?.participantList.orEmpty()
        conferenceSession.calls.filter { it.state == Call.State.StreamsRunning && isSpeechLevel(it.playVolume) }
            .mapNotNull { it.remoteAddress?.username }.forEach { remoteSpeechUntil[it] = now + 300_000_000L }
        remoteSpeechUntil.entries.removeAll { it.value <= now }
        val speaking = remoteSpeechUntil.keys.toSet()
        val muted = participants.filter { participant ->
            participant.devices.isNotEmpty() && participant.devices.all { it.getIsMuted() }
        }.mapNotNull { it.address?.username }.toSet()
        val hasLocalSpeech = currentCall?.state == Call.State.StreamsRunning &&
            isSpeechLevel(conference?.inputVolume ?: currentCall?.recordVolume ?: -120f)
        if (hasLocalSpeech && !microphoneMuted && !conferenceHeld) localSpeechUntil = now + 300_000_000L
        val localSpeaking = !microphoneMuted && !conferenceHeld && now < localSpeechUntil
        conferenceSession.updateAudioState(microphoneMuted, speakerMuted, speaking, muted, localSpeaking)
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        if (roomId.isNotBlank() && roomService.enabled && lanRoom == null) {
            val routedAddress = runCatching {
                java.net.DatagramSocket().use { socket ->
                    socket.connect(java.net.InetAddress.getByName(currentAccount?.params?.serverAddress?.domain), 5060)
                    socket.localAddress
                }
            }.getOrNull()?.takeIf { it is java.net.Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
            val address = routedAddress ?: NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }
            if (address != null) lanRoom = runCatching { LanConferenceChannel(address) }.getOrNull()
        }
        lanRoom?.peers?.filterValues { now - it.seenAt < 2_000_000_000L }?.let { peers ->
            conferenceSession.calls.forEach { leg -> peers[leg.remoteAddress?.username]?.let { reportedMutes[leg] = it.muted } }
        }
        val guest = guestRoom
        if (currentCall == null || _callStatus.value.state in setOf(CallState.ENDED, CallState.FAILED)) {
            if (roomId.isNotBlank() || guest != null) clearRoom()
            _conferenceStatus.value = conferenceSession.status.value
        } else if (guest != null) {
            _conferenceStatus.value = ConferenceStatus(ConferencePhase.ACTIVE,
                participants = guest.members.filterNot { it == own }, microphoneMuted = microphoneMuted,
                speakerMuted = speakerMuted,
                mutedNumbers = (guest.muted - own) + if (microphoneMuted || conferenceHeld) setOf(own) else emptySet(),
                speakingNumbers = (guest.speaking - own) + if (localSpeaking) setOf(own) else emptySet(),
                localSpeaking = localSpeaking, isHost = false,
                hostNumber = guest.host, roomId = guest.roomId, pendingNumbers = guest.pending.filterNot { it == own })
                .copy(held = conferenceHeld)
        } else {
            val status = conferenceSession.status.value
            _conferenceStatus.value = status.copy(
                phase = if (roomId.isNotBlank() && status.phase == ConferencePhase.NONE) ConferencePhase.ACTIVE else status.phase,
                participants = status.participants.ifEmpty { if (roomId.isNotBlank()) listOfNotNull(currentCall?.remoteAddress?.username) else emptyList() },
                hostNumber = own, roomId = roomId, pendingNumbers = roomInviteQueue,
                unsynchronizedNumbers = roomPeerSince.filter { (call, since) ->
                    roomId.isNotBlank() && now - since > (if (roomService.enabled) 20_000_000_000L else 8_000_000_000L) && call !in reportedMutes &&
                        call.state == Call.State.StreamsRunning
                }.keys.mapNotNull { it.remoteAddress?.username }.toSet(),
                held = conferenceHeld,
                speakingNumbers = status.speakingNumbers,
                localSpeaking = status.localSpeaking,
                mutedNumbers = (if (roomService.enabled) emptySet() else status.mutedNumbers) +
                    reportedMutes.filterValues { it }.keys.mapNotNull { it.remoteAddress?.username })
            if (roomId.isNotBlank()) {
                val members = (listOf(own) + status.participants.ifEmpty { listOfNotNull(currentCall?.remoteAddress?.username) }).distinct()
                val snapshot = RoomMessage.Snapshot(roomId, 0, own, members,
                    roomInviteQueue.filterNot { it in members }, _conferenceStatus.value.mutedNumbers +
                        if (microphoneMuted || conferenceHeld) setOf(own) else emptySet(), speaking + if (localSpeaking) setOf(own) else emptySet())
                if (roomService.enabled) {
                    val live = snapshot.copy(revision = ++roomRevision, endpoint = lanRoom?.endpoint.orEmpty(),
                        muted = snapshot.muted.intersect(members.toSet()), speaking = snapshot.speaking.intersect(members.toSet()))
                    lanRoom?.snapshot = live
                    cloudSnapshot = live.copy(speaking = emptySet())
                    conferenceSession.calls.ifEmpty { listOfNotNull(currentCall) }.forEach {
                        roomPeerSince.putIfAbsent(it, now)
                    }
                    return
                }
                val recipients = conferenceSession.calls.ifEmpty { listOfNotNull(currentCall) }
                if (now - roomLastSentAt > 500_000_000L && (snapshot != lastRoomSnapshot ||
                        recipients.any { roomSentStates[it] != it.state } || now - roomLastSentAt > 5_000_000_000L)) {
                    roomLastSentAt = now; lastRoomSnapshot = snapshot; roomRevision++
                    recipients.forEach { call ->
                        if (sendRoomMessage(call, snapshot.copy(revision = roomRevision))) {
                            roomSentStates[call] = call.state
                            roomPeerSince.putIfAbsent(call, now)
                        }
                    }
                }
            }
        }
    }

    private fun handleRoomInstruction(instruction: String, room: RoomMessage.Snapshot) {
        if (instruction.isBlank()) return
        val parts = instruction.split('|')
        if (parts.size == 2 && parts[0] == "direct" && parts[1] == room.roomId) {
            ignoredGuestRoom = room.roomId
            guestRoom = null
            return
        }
        if (parts.size != 4 || parts[0] !in setOf("prepare", "switch") ||
            !Regex("[a-f0-9-]{36}").matches(parts[1]) || parts[2] !in room.members || parts[2] == room.host) return
        val members = parts[3].split(',')
        if (members.size !in 2..7 || members.distinct().size != members.size || members.any { it !in room.members || it == room.host }) return
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        if (own !in members) return
        val existing = handoff
        if (parts[0] == "prepare") {
            handoff = Handoff(parts[1], parts[2], members, System.nanoTime() + 20_000_000_000L, microphoneMuted, speakerMuted)
            lanAcknowledgement = parts[1]
        } else if (existing?.id == parts[1] && existing.expiresAt > System.nanoTime() && roomTransitionJob?.isActive != true) {
            handoff = existing.copy(committed = true)
            lanAcknowledgement = "switch:" + parts[1]
            roomTransitionJob = scope.launch {
                delay(700)
                if (currentCall?.remoteAddress?.username?.let { it != room.host } == true) {
                    handoff = null
                    return@launch
                }
                currentCall?.terminate()
                if (own == existing.host) {
                    val disconnected = withTimeoutOrNull(5_000) { while (currentCall != null) delay(30); true } == true
                    if (!disconnected) return@launch
                    val targets = existing.members.filterNot { it == own }
                    delay(500)
                    handoff = null
                    if (targets.size == 1) call(targets.single()) else startConference(targets)
                    microphoneMuted = existing.microphoneMuted
                    speakerMuted = existing.speakerMuted
                    currentCall?.setMicrophoneMuted(microphoneMuted)
                    currentCall?.setSpeakerMuted(speakerMuted)
                }
            }
        }
    }

    private suspend fun transferConferenceHost() {
        check(roomInviteJob?.isActive != true && conferenceSession.status.value.phase == ConferencePhase.ACTIVE) {
            "Дождитесь завершения приглашения перед передачей организатора"
        }
        val channel = checkNotNull(lanRoom) { "Нет прямого соединения для передачи организатора" }
        val members = conferenceSession.calls.mapNotNull { it.remoteAddress?.username }.distinct()
        val now = System.nanoTime()
        check(members.all { channel.peers[it]?.let { peer -> now - peer.seenAt < 2_000_000_000L } == true }) {
            "Не все участники подключены напрямую. Передача организатора пока недоступна"
        }
        val successor = members.first()
        val id = UUID.randomUUID().toString()
        val command = "$id|$successor|${members.joinToString(",")}"
        channel.instruction = "prepare|$command"
        val prepared = withTimeoutOrNull(4_000) {
            while (members.any { channel.peers[it]?.acknowledgement != id }) delay(40)
            true
        } == true
        if (!prepared) {
            channel.instruction = ""
            error("Участники не подтвердили передачу организатора. Звонок продолжается")
        }
        channel.instruction = "switch|$command"
        val committed = withTimeoutOrNull(600) {
            while (members.any { channel.peers[it]?.acknowledgement != "switch:$id" }) delay(20)
            true
        } == true
        if (!committed) delay(200)
    }

    override suspend fun configureConferenceService(endpoint: String) = withContext(linphoneDispatcher) {
        if (roomMaintenanceJob?.isActive != true) roomMaintenanceJob = scope.launch {
            while (isActive) {
                if (conferenceSession.status.value.phase == ConferencePhase.ACTIVE &&
                    conferenceSession.calls.size == 1 && roomInviteJob?.isActive != true && roomId.isNotBlank() &&
                    roomTransitionJob?.isActive != true) {
                    val remaining = conferenceSession.calls.single()
                    val channel = lanRoom
                    channel?.instruction = "direct|$roomId"
                    delay(450)
                    if (conferenceSession.calls.singleOrNull() == remaining && conferenceSession.status.value.phase == ConferencePhase.ACTIVE &&
                        roomInviteJob?.isActive != true) {
                        conferenceSession.reset()
                        // Removing the final native mixer leg terminates it in this SDK.
                        // Keep its audio path and return only the room/UI to one-to-one mode.
                        localConference?.enter()
                        localConference?.microphoneMuted = microphoneMuted
                        currentCall = remaining
                        remaining.setMicrophoneMuted(microphoneMuted)
                        remaining.setSpeakerMuted(speakerMuted)
                        clearRoom()
                        _conferenceStatus.value = ConferenceStatus(microphoneMuted = microphoneMuted, speakerMuted = speakerMuted)
                    }
                }
                delay(200)
            }
        }
        if (lanRoomJob?.isActive != true) lanRoomJob = scope.launch {
            while (isActive) {
                val guest = guestRoom
                val call = currentCall
                val own = currentAccount?.params?.identityAddress?.username.orEmpty()
                if (guest != null && guest.endpoint.isNotBlank() && call != null) {
                    try {
                        val reply = LanConferenceChannel.exchange(guest.endpoint, guest.roomId, own,
                            microphoneMuted || conferenceHeld, _conferenceStatus.value.localSpeaking, lanAcknowledgement)
                        if (currentCall == call && guestRoom?.roomId == reply.snapshot.roomId && reply.snapshot.host == guest.host && own in reply.snapshot.members) {
                            guestRoom = reply.snapshot
                            guestLastSeenAt = System.nanoTime(); lastLanReceivedAt = guestLastSeenAt
                            handleRoomInstruction(reply.instruction, reply.snapshot)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        if (guestRoom?.roomId == guest.roomId && System.nanoTime() - lastLanReceivedAt > 800_000_000L) {
                            guestRoom = guestRoom?.copy(speaking = emptySet())
                        }
                    }
                }
                delay(120)
            }
        }
        if (roomService.endpoint == endpoint && roomServiceJob?.isActive == true) return@withContext
        roomServiceJob?.cancelAndJoin()
        roomService = GoogleSheetsConferenceService(endpoint)
        if (!roomService.enabled) return@withContext
        roomServiceJob = scope.launch {
            while (isActive) {
                val service = roomService
                val call = currentCall
                val own = currentAccount?.params?.identityAddress?.username.orEmpty()
                val peer = call?.remoteAddress?.username.orEmpty()
                val snapshot = cloudSnapshot
                val token = roomOwnerToken
                if (call != null && _callStatus.value.state.isInProgress && own.isNotBlank()) {
                    try {
                        if (snapshot != null && snapshot.roomId == roomId) {
                            val response = service.request("roomSync", mapOf("role" to "host",
                                "snapshot" to ConferenceRoomWire.encode(snapshot), "token" to token))
                            if (currentCall == call && roomId == snapshot.roomId && service === roomService && response.startsWith("peers\n")) {
                                cloudLastSuccessAt = System.nanoTime()
                                reportedMutes.clear()
                                val peers = response.substringAfter('\n').lines().mapNotNull { line ->
                                    val parts = line.split(',')
                                    if (parts.size == 2 && parts[1] in setOf("0", "1")) parts[0] to (parts[1] == "1") else null
                                }.toMap()
                                conferenceSession.calls.ifEmpty { listOf(call) }.forEach { leg ->
                                    peers[leg.remoteAddress?.username]?.let { reportedMutes[leg] = it }
                                }
                            }
                        } else if (roomId.isBlank() && !conferenceSession.hasSession &&
                            System.nanoTime() - lastLanReceivedAt > 2_000_000_000L) {
                            val response = service.request("roomSync", mapOf("extension" to own,
                                "host" to peer, "muted" to if (microphoneMuted || conferenceHeld) "1" else "0"))
                            if (currentCall == call && roomId.isBlank() && service === roomService) {
                                val received = ConferenceRoomWire.decode(response) as? RoomMessage.Snapshot
                                if (received != null && received.roomId != ignoredGuestRoom && received.host == peer && own in received.members &&
                                    (guestRoom == null || guestRoom?.roomId == received.roomId) &&
                                    received.revision >= (guestRoom?.revision ?: 0)) {
                                    guestRoom = received
                                    guestLastSeenAt = System.nanoTime()
                                } else if (response == "none") {
                                    guestRoom = null
                                }
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Signalling failure must not terminate the audio call. */ }
                    if (roomId.isNotBlank() && System.nanoTime() - cloudLastSuccessAt > 20_000_000_000L) reportedMutes.clear()
                    if (guestRoom != null && System.nanoTime() - guestLastSeenAt > 45_000_000_000L) guestRoom = null
                }
                delay(3_000)
            }
        }
    }

    override suspend fun unregister() =
        withContext(linphoneDispatcher) {

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
        withContext(linphoneDispatcher) {
            if (conferenceSession.hasSession && conferenceSession.calls.size >= 2) transferConferenceHost()
        }
        roomTransitionJob?.cancelAndJoin(); roomTransitionJob = null
        roomMaintenanceJob?.cancelAndJoin(); roomMaintenanceJob = null
        lanRoomJob?.cancelAndJoin(); lanRoomJob = null
        roomServiceJob?.cancelAndJoin()
        roomServiceJob = null
        conferenceInviteJob?.cancel()
        stopRingtonePreview()
        iterateJob?.cancelAndJoin()
        iterateJob = null

        withContext(linphoneDispatcher) {
            clearRoom()

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
            localConference = null
            conferenceSession.reset()
            _conferenceStatus.value = ConferenceStatus()
            _callStatus.value = CallStatus()
            _registrationState.value = RegistrationState.DISCONNECTED
            _connectionStatus.value = TelephonyConnection.DISCONNECTED
            registrationStartedAt = 0L
            _registrationMessage.value = ""

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
        val ringtone = (QTalkRingtones + customRingtones()).firstOrNull { it.id == ringtoneId } ?: return@withContext
        val resource = Files.newInputStream(bundledRingtone(ringtone.id))
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
        val ringtone = (QTalkRingtones + customRingtones()).firstOrNull { it.id == ringtoneId } ?: return@withContext
        val volume = _audioSettings.value.ringtoneVolume.coerceIn(0f, 1f)
        audioPreferences.put("ringtoneId", ringtone.id)
        audioPreferences.flush()
        applyRingtoneVolume(volume, ringtone.id)
        _audioSettings.value = _audioSettings.value.copy(selectedRingtoneId = ringtone.id, ringtones = QTalkRingtones + customRingtones())
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
            selectedRingtoneId = ringtoneId,
            ringtones = QTalkRingtones + customRingtones()
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
        .takeIf { stored -> (QTalkRingtones + customRingtones()).any { it.id == stored } }
        ?: QTalkRingtones.first().id

    private fun bundledRingtone(ringtoneId: String): Path {
        if (Regex("custom-[a-f0-9-]{36}").matches(ringtoneId)) {
            val path = localMediaDirectory().resolve("ringtones").resolve(ringtoneId.removePrefix("custom-") + ".wav")
            if (Files.isRegularFile(path)) return path
        }
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
