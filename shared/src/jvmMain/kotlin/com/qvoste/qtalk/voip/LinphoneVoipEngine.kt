package com.qvoste.qtalk.voip

import com.qvoste.qtalk.QTalkBuildInfo
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
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.prefs.Preferences
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
    @Volatile private var stopping = false
    private var currentAccount: Account? = null
    @Volatile private var diagnosticExtension = ""
    private val lanSip by lazy { LanHandoffSip(core, factory) }
    private var advertisedSip: LanSipEndpoint? = null
    private val consumedTransfers = mutableSetOf<String>()
    private var consumedRoom = ""
    private var lastLanErrorAt = 0L
    private fun diagnostic(code: String, transfer: String? = handoff?.id ?: hostTransfer?.id,
        phase: String = "", peer: String = "", state: String = "", reason: String = "", protocol: Int = 0,
        target: LanSipEndpoint? = null) = Lan2Diagnostics.event(code,
        diagnosticExtension, transfer, phase, peer, state, reason, protocol, target)
    private fun acknowledge(value: String) {
        if (value != lanAcknowledgement) diagnostic("guest_ack", phase = value.substringBefore(':'))
        lanAcknowledgement = value
    }
    private fun rememberTransfer(id: String) { consumedTransfers.add(id) }
    private fun localSipEndpoint(controlEndpoint: String): LanSipEndpoint {
        val address = checkNotNull(LanConferenceChannel.parseEndpoint(controlEndpoint)).first
        val host = java.net.DatagramSocket().use { socket -> socket.connect(address); socket.localAddress.hostAddress }
        return lanSip.endpoint(host).also { advertisedSip = it }
    }
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
    private val localSpeechDetector = SpeechActivityDetector()
    private val remoteSpeechDetectors = mutableMapOf<String, SpeechActivityDetector>()
    private var lastConferenceAudioAt = 0L
    private var roomId = ""
    private var roomCreator = ""
    private var transferMessage = ""
    private var hostTransfer: ConferenceHandoff? = null
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
    private class Handoff(val id: String, val host: String, val members: List<String>, val expiresAt: Long,
        val microphoneMuted: Boolean, val speakerMuted: Boolean, val room: RoomMessage.Snapshot, val originalCall: Call,
        var connecting: Boolean = false, var committed: Boolean = false, var aborting: Boolean = false,
        val endpoints: Map<String, LanSipEndpoint>, val account: Account,
        val staged: MutableList<Call> = mutableListOf(), var mixer: Conference? = null)
    private var handoff: Handoff? = null
    private var commitOriginal: Call? = null
    private fun awaitingHostCommitCompletion() = commitOriginal?.state?.let {
        it !in setOf(Call.State.End, Call.State.Error, Call.State.Released)
    } == true
    private val _conferenceStatus = MutableStateFlow(ConferenceStatus())
    private val conferenceSession = LocalConferenceSession(object : LocalConferenceBackend<Call> {
        override fun number(call: Call): String = call.remoteAddress?.username.orEmpty()
        override fun state(call: Call): ConferenceLegState = conferenceLegState(call.state) ?: ConferenceLegState.PAUSING
        override fun pause(call: Call) { check(call.pause() == 0) { "Не удалось поставить первый звонок на удержание" } }
        override fun prepareConference(call: Call): Boolean {
            val conference = ensureLocalConference()
            if (call.conference != conference) check(conference.addParticipant(call) == 0) { "Не удалось подключить звук конференции" }
            check(conference.enter() == 0) { "Не удалось подключить звук конференции" }
            conference.microphoneMuted = effectiveMicrophoneMute()
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
            params.account = currentAccount
            val conference = checkNotNull(localConference)
            val previousCalls = core.calls.toSet()
            conference.inviteParticipants(arrayOf(address), params)
            val invited = core.calls.firstOrNull { it !in previousCalls && it.remoteAddress?.username == number }
            return checkNotNull(invited) { "Не удалось добавить участника" }.also {
                it.setMicrophoneMuted(effectiveMicrophoneMute())
                it.setSpeakerMuted(effectiveSpeakerMute())
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
                conference.microphoneMuted = effectiveMicrophoneMute()
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
    private val gainPreferences = AudioGainPreferences(audioPreferences)
    private val initialGain = gainPreferences.load()
    private val audioGain = AudioGainController(scope, initialGain, { levels ->
        applyAudioGain(core, localConference, levels, microphoneMuted, speakerMuted, conferenceHeld,
            handoff?.takeIf { !it.committed }?.staged?.toSet().orEmpty(),
            _audioSettings.value.microphoneSensitivity == 0f, _audioSettings.value.speakerVolume == 0f)
    }, { levels -> withContext(Dispatchers.IO) { gainPreferences.save(levels) } })
    private fun effectiveMicrophoneMute(manual: Boolean = microphoneMuted, held: Boolean = conferenceHeld) =
        AudioGain.muted(manual, held, audioGain.target.microphone)
    private fun effectiveSpeakerMute(manual: Boolean = speakerMuted, held: Boolean = conferenceHeld) =
        AudioGain.muted(manual, held, audioGain.target.speaker)

    private val ringtonePreview = RingtonePreview(CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)) { id, volume ->
        openRingtonePreview(bundledRingtone(id), volume)
    }.apply { setAllowed(false) }
    private val _audioSettings = MutableStateFlow(
        AudioSettings(
            microphoneSensitivity = initialGain.microphone,
            speakerVolume = initialGain.speaker,

            ringtoneVolume = audioPreferences.getFloat("ringtoneVolume", 0.5f),
            selectedRingtoneId = storedRingtoneId()
        )
    )
    override val audioSettings = _audioSettings.asStateFlow()

    init {
        scope.launch {
            ringtonePreview.playing.collect { id ->
                _audioSettings.value = _audioSettings.value.copy(previewRingtoneId = id)
            }
        }
    }

    private val _registrationState =
        MutableStateFlow(RegistrationState.DISCONNECTED)
    override val registrationState: StateFlow<RegistrationState> =
        _registrationState.asStateFlow()
    private val _registrationMessage = MutableStateFlow("")
    override val registrationMessage: StateFlow<String> =
        _registrationMessage.asStateFlow()

    private val coreListener = object : CoreListenerStub() {
        override fun onInfoReceived(core: Core, call: Call, message: InfoMessage) {
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
                    if (previous != null && (previous.roomId != room.roomId || previous.creator != room.creator ||
                        room.revision < previous.revision)) return
                    guestRoom = room
                    sendRoomMessage(call, RoomMessage.Microphone(room.roomId, microphoneMuted))
                }
                null -> Unit
            }
        }
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            if (state == Call.State.IncomingReceived || state == Call.State.OutgoingInit ||
                state == Call.State.Connected || state == Call.State.StreamsRunning) ringtonePreview.setAllowed(false)
            diagnostic("native_call", peer = call.remoteAddress?.username.orEmpty(), state = state.toString(),
                reason = call.reason.toString(), protocol = call.errorInfo?.protocolCode ?: 0)
            val staging = handoff
            if (staging != null && !staging.committed && !staging.aborting) {
                if (call in staging.staged) return
                val own = currentAccount?.params?.identityAddress?.username.orEmpty()
                if (state == Call.State.IncomingReceived && own != staging.host &&
                    LanHandoffAdmission.accepts(
                        ConferenceHandoffPermit(staging.id, staging.host, staging.room.roomId, staging.expiresAt),
                        call.remoteAddress?.username.orEmpty(), call.remoteParams?.getCustomHeader("X-QTalk-Handoff"),
                        guestRoom?.roomId.orEmpty(), System.nanoTime(), staging.staged.isNotEmpty(),
                        currentCall == staging.originalCall, call.remoteAddress?.domain,
                        call.remoteContactAddress?.let { LanSipEndpoint.parse("${it.domain}:${it.port}") },
                        staging.endpoints[staging.host])) {
                    staging.connecting = true
                    staging.staged += call
                    diagnostic("admission_accept", peer = staging.host, target = staging.endpoints[staging.host])
                    val params = lanSip.params(staging.account, call)
                    call.setMicrophoneMuted(true); call.setSpeakerMuted(true)
                    if (call.acceptWithParams(params) != 0) {
                        diagnostic("admission_error", reason = "native_accept_failed")
                        // Keep the admission consumed for this transaction even on native error.
                        call.decline(Reason.NotAcceptable)
                    }
                    return
                }
            }
            val conferenceState = conferenceLegState(state)
            if (conferenceState != null && conferenceSession.onCallState(call, conferenceState)) {
                audioGain.restore()
                return
            }
            if (state == Call.State.IncomingReceived) {
                if (call.remoteParams?.getCustomHeader("X-QTalk-Handoff") != null || staging != null) {
                    diagnostic("admission_deny", peer = call.remoteAddress?.username.orEmpty(), reason = "permit_or_endpoint_mismatch")
                    call.decline(Reason.NotAcceptable)
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
                audioGain.restore()
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
            audioGain.restore()
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
            if (!next.isInProgress && coreStarted && !stopping) ringtonePreview.setAllowed(true)
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
        stopping = false
        if (iterateJob?.isActive == true) {
            return@withContext
        }


        if (!coreStarted) {
            core.addListener(coreListener)
            diagnostic("build_start", reason = QTalkBuildInfo.VERSION)
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
        ringtonePreview.setAllowed(!_callStatus.value.state.isInProgress)

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

            diagnosticExtension = account.username
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
            transports.udpPort = core.transportsUsed.udpPort.takeIf { it > 0 } ?: -1
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
        ringtonePreview.setAllowed(false)
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
            ringtonePreview.setAllowed(true)
            _callStatus.value = CallStatus(CallState.FAILED, extension, "Не удалось начать звонок")
        } else {
            currentCall = call
            audioGain.restore()
        }
    }

    override suspend fun acceptCall() = withContext(linphoneDispatcher) {
        ringtonePreview.setAllowed(false)
        val call = checkNotNull(currentCall) { "Нет входящего звонка" }
        check(_callStatus.value.state == CallState.INCOMING) { "Звонок уже обработан" }

        val params = checkNotNull(core.createCallParams(call))
        params.isVideoEnabled = false
        audioGain.restore()
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
        check(handoff == null && !awaitingHostCommitCompletion()) { "Дождитесь завершения передачи звонка" }
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
        val call = checkNotNull(conferenceSession.foregroundCall ?: currentCall) { "Нет активного звонка" }
        microphoneMuted = muted
        audioGain.restore()
        guestRoom?.let { sendRoomMessage(call, RoomMessage.Microphone(it.roomId, muted)) }
        Unit
    }

    override suspend fun setSpeakerMuted(muted: Boolean) = withContext(linphoneDispatcher) {
        checkNotNull(conferenceSession.foregroundCall ?: currentCall) { "Нет активного звонка" }
        speakerMuted = muted
        audioGain.restore()
    }

    override suspend fun setConferenceHeld(held: Boolean) = withContext(linphoneDispatcher) {
        check(_conferenceStatus.value.phase != ConferencePhase.NONE) { "Нет группового звонка" }
        localConference?.let { conference ->
            check((if (held) conference.leave() else conference.enter()) == 0) { "Не удалось изменить удержание" }
        }
        conferenceHeld = held
        audioGain.restore()
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
        check(hostTransfer == null && handoff == null && !awaitingHostCommitCompletion()) { "Дождитесь завершения передачи звонка" }
        check(!conferenceHeld) { "Сначала снимите звонок с удержания" }
        check(guestRoom == null) { "Приглашать участников может только организатор" }
        check(_registrationState.value == RegistrationState.REGISTERED) { "Нет связи с сервером телефонии" }
        val target = normalizeOutgoingNumber(number)
        require(target != currentAccount?.params?.identityAddress?.username) { "Нельзя добавить свой номер" }
        if (roomId.isBlank()) {
            roomId = UUID.randomUUID().toString()
            roomCreator = currentAccount?.params?.identityAddress?.username.orEmpty()
        }
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
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        check(hostTransfer == null && handoff == null && !awaitingHostCommitCompletion() &&
            _conferenceStatus.value.canRemoveParticipant(own, number) && roomCreator == own && guestRoom == null) {
            "Удалять подключённых участников может только создатель на устройстве организатора"
        }
        conferenceSession.removeParticipant(number)
    }

    override suspend fun inviteConferenceParticipants(numbers: List<String>) = withContext(linphoneDispatcher) {
        check(hostTransfer == null && handoff == null && !awaitingHostCommitCompletion()) { "Дождитесь завершения передачи звонка" }
        check(guestRoom == null && roomInviteJob?.isActive != true) { "Дождитесь завершения приглашения" }
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        val existing = conferenceSession.calls.map { it.remoteAddress?.username } + currentCall?.remoteAddress?.username + own
        val targets = numbers.map(::normalizeOutgoingNumber).distinct().filterNot { it in existing }
        require(targets.isNotEmpty() && targets.size + existing.filterNotNull().distinct().size <= MAX_CONFERENCE_PARTICIPANTS) {
            "Достигнут лимит участников"
        }
        if (roomId.isBlank()) {
            roomId = UUID.randomUUID().toString()
            roomCreator = own
        }
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
        roomCreator = currentAccount?.params?.identityAddress?.username.orEmpty()
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
        advertisedSip = null
        lanAcknowledgement = ""
        conferenceHeld = false
        localSpeechDetector.reset()
        remoteSpeechDetectors.clear()
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
        roomId = ""; roomCreator = ""; guestRoom = null; roomRevision = 0L; roomLastSentAt = 0L; lastRoomSnapshot = null
        hostTransfer = null
        transferMessage = ""
        reportedMutes.clear()
        roomSentStates.clear()
        roomPeerSince.clear()
    }

    private fun refreshConferenceAudio() {
        val now = System.nanoTime()
        if (now - lastConferenceAudioAt < 50_000_000L) return
        lastConferenceAudioAt = now
        if (core.calls.isEmpty() && localConference == null && handoff == null && hostTransfer == null) lanSip.releaseUnused()
        val conference = localConference?.takeIf { conferenceSession.hasSession }
        val participants = conference?.participantList.orEmpty()
        val muted = participants.filter { participant ->
            participant.devices.isNotEmpty() && participant.devices.all { it.getIsMuted() }
        }.mapNotNull { it.address?.username }.toSet() +
            reportedMutes.filterValues { it }.keys.mapNotNull { it.remoteAddress?.username }
        val callsByNumber = conferenceSession.calls.mapNotNull { call ->
            call.remoteAddress?.username?.let { it to call }
        }.toMap()
        remoteSpeechDetectors.keys.retainAll(callsByNumber.keys)
        val speaking = callsByNumber.filter { (number, call) ->
            remoteSpeechDetectors.getOrPut(number) { SpeechActivityDetector() }.update(
                call.playVolume, now, call.state == Call.State.StreamsRunning && number !in muted
            )
        }.keys.toSet()
        // In a host conference, recordVolume on a call may contain the outgoing mix.
        // The conference input is the host's microphone, never another participant.
        val localSpeaking = localSpeechDetector.update(
            conference?.inputVolume ?: currentCall?.recordVolume ?: -120f, now,
            currentCall?.state == Call.State.StreamsRunning && !microphoneMuted && !conferenceHeld
        )
        conferenceSession.updateAudioState(microphoneMuted, speakerMuted, speaking, muted, localSpeaking)
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        if (roomId.isNotBlank() && lanRoom == null) {
            val routedAddress = runCatching {
                java.net.DatagramSocket().use { socket ->
                    socket.connect(java.net.InetAddress.getByName(currentAccount?.params?.serverAddress?.domain), 5060)
                    socket.localAddress
                }
            }.getOrNull()?.takeIf { it is java.net.Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
            val address = routedAddress ?: NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }
            if (address != null) lanRoom = runCatching { LanConferenceChannel(address) { code -> diagnostic("control_reject", reason = code) } }.onFailure { diagnostic("control_bind_error", reason = it.javaClass.simpleName) }.getOrNull()
        }
        lanRoom?.peers?.filterValues { now - it.seenAt < ConferenceHandoffTiming.PEER_LIFETIME_NANOS }?.let { peers ->
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
                hostNumber = guest.host, roomId = guest.roomId, pendingNumbers = guest.pending.filterNot { it == own },
                originalCreatorNumber = guest.creator, message = transferMessage)
                .copy(held = conferenceHeld)
        } else {
            val status = conferenceSession.status.value
            _conferenceStatus.value = status.copy(
                phase = if (roomId.isNotBlank() && status.phase == ConferencePhase.NONE) ConferencePhase.ACTIVE else status.phase,
                participants = status.participants.ifEmpty { if (roomId.isNotBlank()) listOfNotNull(currentCall?.remoteAddress?.username) else emptyList() },
                hostNumber = own, roomId = roomId, pendingNumbers = roomInviteQueue,
                originalCreatorNumber = roomCreator, message = transferMessage.ifBlank { status.message },
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
                        if (microphoneMuted || conferenceHeld) setOf(own) else emptySet(), speaking + if (localSpeaking) setOf(own) else emptySet(),
                    endpoint = lanRoom?.endpoint.orEmpty(), creator = roomCreator.ifBlank { own })
                lanRoom?.snapshot = snapshot.copy(revision = roomRevision)
                if (roomService.enabled) {
                    val live = snapshot.copy(revision = ++roomRevision, endpoint = lanRoom?.endpoint.orEmpty(),
                        muted = snapshot.muted.intersect(members.toSet()), speaking = snapshot.speaking.intersect(members.toSet()))
                    lanRoom?.snapshot = live
                    cloudSnapshot = live.copy(speaking = emptySet())
                    conferenceSession.calls.ifEmpty { listOfNotNull(currentCall) }.forEach {
                        roomPeerSince.putIfAbsent(it, now)
                    }
                }
                val recipients = conferenceSession.calls.ifEmpty { listOfNotNull(currentCall) }
                if (now - roomLastSentAt > 500_000_000L && (snapshot != lastRoomSnapshot ||
                        recipients.any { roomSentStates[it] != it.state } || now - roomLastSentAt > ConferenceHandoffTiming.ROOM_SNAPSHOT_REFRESH_NANOS)) {
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

    private fun handleRoomInstruction(instruction: String, room: RoomMessage.Snapshot, endpoints: Map<String, LanSipEndpoint>) {
        if (instruction.isBlank()) return
        val parts = instruction.removePrefix("LAN2|").split('|')
        if (!instruction.startsWith("LAN2|")) {
            diagnostic("instruction_deny", reason = "update_all_required")
            return
        }
        if (parts.size == 2 && parts[0] == "direct" && parts[1] == room.roomId && handoff == null) {
            ignoredGuestRoom = room.roomId
            guestRoom = null
            return
        }
        if (parts.size != 4 || parts[0] !in setOf("prepare", "connect", "commit", "abort") ||
            !Regex("[a-f0-9-]{36}").matches(parts[1]) || parts[2] !in room.members || parts[2] == room.host) {
            diagnostic("instruction_deny", reason = "invalid_command"); return
        }
        val members = parts[3].split(',')
        if (members.size !in 2..7 || members.distinct().size != members.size || parts[2] !in members ||
            members.toSet() != (room.members - room.host).toSet() || room.pending.isNotEmpty()) {
            diagnostic("instruction_deny", reason = "membership_or_pending"); return
        }
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        val original = currentCall ?: run { diagnostic("instruction_deny", reason = "no_original"); return }
        if (own !in members || original.remoteAddress?.username != room.host) {
            diagnostic("instruction_deny", reason = "original_host_mismatch"); return
        }
        val existing = handoff
        if (parts[0] == "abort" && existing == null) {
            lanAcknowledgement = "aborted:" + parts[1]
            return
        }
        if (parts[0] == "prepare" && existing == null) {
            if (consumedRoom != room.roomId) { consumedTransfers.clear(); consumedRoom = room.roomId }
            if (parts[1] in consumedTransfers || consumedTransfers.size >= ConferenceHandoffLimits.MAX_REMEMBERED_TRANSFERS_PER_ROOM || endpoints.keys != members.toSet() ||
                endpoints[own] != advertisedSip || guestRoom?.roomId != room.roomId) {
                diagnostic("prepare_deny", transfer = parts[1], reason = "endpoint_room_or_replay")
                return
            }
            val account = try { lanSip.account(own, checkNotNull(advertisedSip)) }
                catch (e: Exception) {
                    diagnostic("prepare_error", transfer = parts[1], reason = e.javaClass.simpleName)
                    return
                }
            rememberTransfer(parts[1])
            handoff = Handoff(parts[1], parts[2], members, System.nanoTime() + ConferenceHandoffTiming.ADMISSION_LIFETIME_NANOS,
                microphoneMuted, speakerMuted, room, original, endpoints = endpoints.toMap(), account = account)
            transferMessage = "Подготовка передачи звонка…"
            acknowledge("prepared:" + parts[1])
        } else if (existing?.id == parts[1] && existing.room.roomId == room.roomId &&
            existing.host == parts[2] && existing.members == members && endpoints == existing.endpoints &&
            !existing.committed && !existing.aborting) {
            when (parts[0]) {
                "abort" -> abortGuestHandoff(existing)
                "connect" -> if (!existing.connecting) {
                    existing.connecting = true
                    transferMessage = "Подключение нового организатора…"
                    if (own == existing.host) roomTransitionJob = scope.launch {
                        try {
                            val mixer = lanSip.mixer(existing.account)
                            existing.mixer = mixer
                            // Keep the original SIP leg outside the replacement mixer until commit.
                            for (target in existing.members.filterNot { it == own }) {
                                val endpoint = checkNotNull(existing.endpoints[target])
                                diagnostic("direct_invite", peer = target, target = endpoint)
                                val leg = lanSip.invite(mixer, existing.account, target, endpoint, existing.id)
                                existing.staged += leg
                                leg.setMicrophoneMuted(true); leg.setSpeakerMuted(true)
                            }
                            val connected = withTimeoutOrNull(ConferenceHandoffTiming.REPLACEMENT_CONNECT_TIMEOUT_MILLIS) {
                                while (existing.staged.any { it.state != Call.State.StreamsRunning }) delay(ConferenceHandoffTiming.STATE_POLL_MILLIS)
                                true
                            } == true
                            check(connected) { "Новые аудиосоединения не готовы" }
                            existing.staged.forEach { leg ->
                                if (leg.conference != mixer) check(mixer.addParticipant(leg) == 0) { "Не удалось подготовить новый микшер" }
                            }
                            check(mixer.enter() == 0) { "Не удалось подготовить новый микшер" }
                            mixer.microphoneMuted = true
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            diagnostic("staging_error", reason = e.javaClass.simpleName)
                            transferMessage = "Не удалось подготовить прямое LAN-соединение. Исходный звонок сохранён"
                        }
                    }
                }
                "commit" -> if (guestHandoffReady(existing)) {
                    existing.committed = true
                    roomTransitionJob?.cancel()
                    commitGuestHandoff(existing)
                }
            }
        }
    }

    private fun guestHandoffReady(transition: Handoff): Boolean {
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        val expected = if (own == transition.host) transition.members.size - 1 else 1
        return !transition.aborting && transition.connecting && LanHandoffSip.ready(transition.staged, expected, transition.mixer, own == transition.host)
    }

    private fun abortGuestHandoff(transition: Handoff) {
        if (transition.committed || transition.aborting) return
        transition.aborting = true
        roomTransitionJob?.cancel(); roomTransitionJob = null
        transition.staged.forEach { runCatching { it.terminate() } }
        transition.mixer?.let { runCatching { it.terminate() }; localConference = null }
        if (transition.originalCall.state in setOf(Call.State.End, Call.State.Error, Call.State.Released)) {
            handoff = null
            transferMessage = "Организатор отключился. Автоматическое восстановление звонка недоступно"
            return
        }
        diagnostic("rollback_start", transfer = transition.id)
        acknowledge("aborting:" + transition.id)
        roomTransitionJob = scope.launch {
            val original = transition.originalCall
            try {
                val released = withTimeoutOrNull(ConferenceHandoffTiming.REPLACEMENT_RELEASE_TIMEOUT_MILLIS) {
                    while (transition.staged.any { it in core.calls } || original.state == Call.State.Pausing) delay(ConferenceHandoffTiming.STATE_POLL_MILLIS)
                    true
                } == true
                check(released) { "Восстановление исходного звонка ещё не завершено" }
                if (original.state == Call.State.Paused) check(original.resume() == 0) { "Не удалось восстановить исходный звонок" }
                original.setMicrophoneMuted(effectiveMicrophoneMute(manual = transition.microphoneMuted))
                original.setSpeakerMuted(effectiveSpeakerMute(manual = transition.speakerMuted))
                val restored = withTimeoutOrNull(ConferenceHandoffTiming.ORIGINAL_RESUME_TIMEOUT_MILLIS) {
                    while (original.state != Call.State.StreamsRunning) delay(ConferenceHandoffTiming.STATE_POLL_MILLIS)
                    true
                } == true
                check(restored) { "Восстановление исходного звонка ещё не завершено" }
                acknowledge("aborted:" + transition.id)
                diagnostic("rollback_result", transfer = transition.id, reason = "original_restored")
                transferMessage = "Передача отменена. Исходные соединения сохранены"
                if (handoff === transition) handoff = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                diagnostic("rollback_error", transfer = transition.id, reason = e.javaClass.simpleName)
                transferMessage = "Восстановление исходного звонка ещё не завершено"
            }
            finally { transition.aborting = false }
        }
    }

    private fun commitGuestHandoff(transition: Handoff) {
        diagnostic("commit_apply", transfer = transition.id)
        val own = currentAccount?.params?.identityAddress?.username.orEmpty()
        val oldRoom = transition.room
        val wasHeld = conferenceHeld
        // Do not close the old host's discovery record: this client was a guest.
        clearRoom()
        microphoneMuted = transition.microphoneMuted
        speakerMuted = transition.speakerMuted
        conferenceHeld = wasHeld
        if (own == transition.host) {
            roomId = transition.id
            roomCreator = oldRoom.creator
            val mixer = checkNotNull(transition.mixer)
            localConference = mixer
            conferenceSession.adoptConnected(transition.staged)
            mixer.microphoneMuted = effectiveMicrophoneMute()
        } else {
            currentCall = transition.staged.single()
            guestRoom = RoomMessage.Snapshot(transition.id, 0, transition.host, transition.members,
                emptyList(), emptySet(), emptySet(), creator = oldRoom.creator)
        }
        transition.staged.forEach {
            it.setMicrophoneMuted(effectiveMicrophoneMute()); it.setSpeakerMuted(effectiveSpeakerMute())
        }
        handoff = null
        _callStatus.value = _callStatus.value.copy(state = CallState.ACTIVE,
            number = currentCall?.remoteAddress?.username.orEmpty())
        refreshConferenceAudio()
        commitOriginal = transition.originalCall
        roomTransitionJob = scope.launch {
            var reported = false
            try {
                while (isActive && awaitingHostCommitCompletion()) {
                    try {
                        LanConferenceChannel.exchange(oldRoom.endpoint, oldRoom.roomId, own,
                            microphoneMuted, false, "committed:" + transition.id, transition.endpoints[own])
                        if (!reported) {
                            diagnostic("commit_ack_result", transfer = transition.id, reason = "delivered")
                            reported = true
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (System.nanoTime() - lastLanErrorAt > ConferenceHandoffTiming.DIAGNOSTIC_THROTTLE_NANOS) {
                            lastLanErrorAt = System.nanoTime()
                            diagnostic("commit_ack_error", transfer = transition.id, reason = e.javaClass.simpleName)
                        }
                    }
                    delay(ConferenceHandoffTiming.COMMIT_ACK_RETRY_MILLIS)
                }
            } finally {
                if (commitOriginal == transition.originalCall) commitOriginal = null
            }
        }
    }

    private suspend fun transferConferenceHost() {
        check(roomInviteJob?.isActive != true && roomInviteQueue.isEmpty() &&
            conferenceSession.status.value.phase == ConferencePhase.ACTIVE) {
            "Дождитесь завершения приглашения перед передачей организатора"
        }
        val channel = checkNotNull(lanRoom) {
            diagnostic("handoff_deny", reason = "control_unavailable")
            "Нет прямого соединения для передачи организатора. Звонок сохранён"
        }
        val members = conferenceSession.calls.mapNotNull { it.remoteAddress?.username }.distinct()
        val now = System.nanoTime()
        check(members.all { channel.peers[it]?.let { peer -> now - peer.seenAt < ConferenceHandoffTiming.PEER_LIFETIME_NANOS } == true }) {
            diagnostic("handoff_deny", reason = "peer_missing_or_old_build")
            "Обновите QTalk у всех участников и проверьте прямое LAN-соединение. Звонок сохранён"
        }
        hostTransfer?.takeIf { it.phase == ConferenceHandoff.Phase.ABORTED }?.let { aborted ->
            check(aborted.rollbackAcknowledged || aborted.members.all {
                channel.peers[it]?.acknowledgement == "aborted:" + aborted.id
            }) { "Восстановление исходного звонка ещё не завершено. Оставьте QTalk открытым" }
            hostTransfer = null
            channel.handoffEndpoints = emptyMap()
        }
        if (hostTransfer == null) {
            check(members.all { channel.peers[it]?.capability == LanConferenceChannel.CAPABILITY &&
                channel.peers[it]?.sipEndpoint != null }) {
                diagnostic("handoff_deny", reason = "update_all_required")
                "Обновите QTalk у всех участников. Исходный звонок сохранён"
            }
            channel.handoffEndpoints = members.associateWith { checkNotNull(channel.peers[it]?.sipEndpoint) }
        }
        val plan = hostTransfer ?: ConferenceHandoff(
            UUID.randomUUID().toString(), members.sorted().first(), members).also { hostTransfer = it }
        transferMessage = "Передача звонка новому организатору…"
        try {
            plan.transfer(object : ConferenceHandoff.Transport {
                override fun instruct(instruction: String) {
                    channel.instruction = "LAN2|$instruction"
                    diagnostic("host_phase", transfer = plan.id, phase = instruction.substringBefore('|'))
                }
                override suspend fun awaitAcknowledgements(ack: String, timeoutMillis: Long): Boolean {
                    val observed = mutableSetOf<String>()
                    fun missing(): List<String> = plan.members.filter { member ->
                        val peer = channel.peers[member]
                        val matches = peer != null && peer.acknowledgement == ack &&
                            peer.capability == LanConferenceChannel.CAPABILITY &&
                            peer.sipEndpoint == channel.handoffEndpoints[member] &&
                            System.nanoTime() - peer.seenAt < ConferenceHandoffTiming.PEER_LIFETIME_NANOS
                        if (matches && observed.add(member)) diagnostic("peer_ack", transfer = plan.id,
                            phase = ack.substringBefore(':'), peer = member)
                        !matches
                    }
                    val result = withTimeoutOrNull(timeoutMillis) {
                        while (missing().isNotEmpty()) delay(ConferenceHandoffTiming.ACK_POLL_MILLIS)
                        true
                    } == true
                    missing().forEach { diagnostic("missing_peer", transfer = plan.id, phase = ack.substringBefore(':'), peer = it) }
                    diagnostic("barrier_result", transfer = plan.id, phase = ack.substringBefore(':'), reason = if (result) "all_ack" else "timeout")
                    return result
                }
            })
            diagnostic("handoff_result", transfer = plan.id, reason = "all_committed")
            transferMessage = "Передача звонка подтверждена"
        } catch (e: Exception) {
            diagnostic("handoff_error", transfer = plan.id, phase = plan.phase.toString(), reason = e.javaClass.simpleName)
            transferMessage = e.message.orEmpty()
            if (plan.phase == ConferenceHandoff.Phase.ABORTED && plan.rollbackAcknowledged) {
                hostTransfer = null
                channel.handoffEndpoints = emptyMap()
            }
            throw e
        }
    }

    override suspend fun configureConferenceService(endpoint: String) = withContext(linphoneDispatcher) {
        if (roomMaintenanceJob?.isActive != true) roomMaintenanceJob = scope.launch {
            while (isActive) {
                if (conferenceSession.status.value.phase == ConferencePhase.ACTIVE &&
                    conferenceSession.calls.size == 1 && roomInviteJob?.isActive != true && roomId.isNotBlank() &&
                    roomTransitionJob?.isActive != true && handoff == null && hostTransfer == null) {
                    val remaining = conferenceSession.calls.single()
                    val channel = lanRoom
                    channel?.instruction = "LAN2|direct|$roomId"
                    delay(450)
                    if (conferenceSession.calls.singleOrNull() == remaining && conferenceSession.status.value.phase == ConferencePhase.ACTIVE &&
                        roomInviteJob?.isActive != true) {
                        conferenceSession.reset()
                        // Removing the final native mixer leg terminates it in this SDK.
                        // Keep its audio path and return only the room/UI to one-to-one mode.
                        localConference?.enter()
                        localConference?.microphoneMuted = effectiveMicrophoneMute()
                        currentCall = remaining
                        remaining.setMicrophoneMuted(effectiveMicrophoneMute())
                        remaining.setSpeakerMuted(effectiveSpeakerMute())
                        clearRoom()
                        _conferenceStatus.value = ConferenceStatus(microphoneMuted = microphoneMuted, speakerMuted = speakerMuted)
                    }
                }
                delay(ConferenceHandoffTiming.ROOM_MAINTENANCE_POLL_MILLIS)
            }
        }
        if (lanRoomJob?.isActive != true) lanRoomJob = scope.launch {
            while (isActive) {
                handoff?.takeIf { !it.committed && !it.aborting }?.let { transition ->
                    if (transition.expiresAt < System.nanoTime() ||
                        transition.originalCall.state in setOf(Call.State.End, Call.State.Error, Call.State.Released)) abortGuestHandoff(transition)
                    else if (guestHandoffReady(transition)) acknowledge("ready:" + transition.id)
                    else if (transition.connecting) acknowledge("connecting:" + transition.id)
                }
                val guest = guestRoom
                val call = currentCall
                val own = currentAccount?.params?.identityAddress?.username.orEmpty()
                if (guest != null && guest.endpoint.isNotBlank() && call != null) {
                    try {
                        val reply = LanConferenceChannel.exchange(guest.endpoint, guest.roomId, own,
                            microphoneMuted || conferenceHeld, _conferenceStatus.value.localSpeaking, lanAcknowledgement,
                            localSipEndpoint(guest.endpoint))
                        if (currentCall == call && guestRoom?.roomId == reply.snapshot.roomId &&
                            reply.snapshot.host == guest.host && reply.snapshot.creator == guest.creator && own in reply.snapshot.members) {
                            guestRoom = reply.snapshot
                            guestLastSeenAt = System.nanoTime(); lastLanReceivedAt = guestLastSeenAt
                            handleRoomInstruction(reply.instruction, reply.snapshot, reply.endpoints)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (System.nanoTime() - lastLanErrorAt > ConferenceHandoffTiming.DIAGNOSTIC_THROTTLE_NANOS) {
                            lastLanErrorAt = System.nanoTime()
                            diagnostic("control_exchange_error", reason = e.javaClass.simpleName)
                        }
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
                            System.nanoTime() - lastLanReceivedAt > ConferenceHandoffTiming.PEER_LIFETIME_NANOS) {
                            val response = service.request("roomSync", mapOf("extension" to own,
                                "host" to peer, "muted" to if (microphoneMuted || conferenceHeld) "1" else "0"))
                            if (currentCall == call && roomId.isBlank() && service === roomService) {
                                val received = ConferenceRoomWire.decode(response) as? RoomMessage.Snapshot
                                if (received != null && received.roomId != ignoredGuestRoom && received.host == peer && own in received.members &&
                                    (guestRoom == null || guestRoom?.roomId == received.roomId) &&
                                    (guestRoom == null || guestRoom?.creator == received.creator) &&
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
        stopping = true
        ringtonePreview.setAllowed(false)
        withContext(linphoneDispatcher) {
            check(handoff == null && !awaitingHostCommitCompletion()) { "Дождитесь завершения передачи звонка" }
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
            audioGain.flush()
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
                if (core.calls.isEmpty()) lanSip.releaseUnused()
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
        val normalized = AudioGain.level(value)
        _audioSettings.value = _audioSettings.value.copy(microphoneSensitivity = normalized)
        audioGain.target(audioGain.target.copy(microphone = normalized))
    }

    override suspend fun setSpeakerVolume(value: Float) = withContext(linphoneDispatcher) {
        val normalized = AudioGain.level(value)
        _audioSettings.value = _audioSettings.value.copy(speakerVolume = normalized)
        audioGain.target(audioGain.target.copy(speaker = normalized))
    }



    override suspend fun setRingtoneVolume(value: Float) = withContext(linphoneDispatcher) {
        val normalized = value.coerceIn(0f, 1f)
        applyRingtoneVolume(normalized)
        audioPreferences.putFloat("ringtoneVolume", normalized)
        audioPreferences.flush()
        _audioSettings.value = _audioSettings.value.copy(ringtoneVolume = normalized)
    }

    override fun previewRingtone(ringtoneId: String) {
        if ((QTalkRingtones + customRingtones()).none { it.id == ringtoneId }) return
        ringtonePreview.play(ringtoneId, _audioSettings.value.ringtoneVolume)
    }

    override suspend fun selectRingtone(ringtoneId: String) = withContext(linphoneDispatcher) {
        stopRingtonePreview()
        val ringtone = (QTalkRingtones + customRingtones()).firstOrNull { it.id == ringtoneId } ?: return@withContext
        val volume = _audioSettings.value.ringtoneVolume.coerceIn(0f, 1f)
        audioPreferences.put("ringtoneId", ringtone.id)
        audioPreferences.flush()
        applyRingtoneVolume(volume, ringtone.id)
        _audioSettings.value = _audioSettings.value.copy(selectedRingtoneId = ringtone.id, ringtones = QTalkRingtones + customRingtones())
    }

    override fun stopRingtonePreview() = ringtonePreview.stop()

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

        val microphoneSensitivity = audioGain.target.microphone
        val speakerVolume = audioGain.target.speaker
        val ringtoneVolume = audioPreferences
            .getFloat("ringtoneVolume", 0.5f)
            .coerceIn(0f, 1f)
        val ringtoneId = storedRingtoneId()
        audioGain.restore()
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

            previewRingtoneId = ringtonePreview.playing.value,
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
