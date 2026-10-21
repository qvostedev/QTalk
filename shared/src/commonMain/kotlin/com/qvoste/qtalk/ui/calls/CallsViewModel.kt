package com.qvoste.qtalk.ui.calls

import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.model.ContactSource
import com.qvoste.qtalk.ui.calls.model.ContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.ContactDatabaseState
import com.qvoste.qtalk.ui.calls.model.ContactStore
import com.qvoste.qtalk.ui.calls.model.ContactNote
import com.qvoste.qtalk.ui.calls.model.ContactNoteStore
import com.qvoste.qtalk.ui.calls.model.CallHistoryStore
import com.qvoste.qtalk.ui.calls.model.HistoryItem
import com.qvoste.qtalk.ui.calls.model.belongsTo
import com.qvoste.qtalk.ui.calls.model.normalizedContactNumber
import com.qvoste.qtalk.ui.calls.model.dialablePhoneNumber
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import com.qvoste.qtalk.ui.calls.model.mergeDownloadedContacts
import com.qvoste.qtalk.ui.calls.model.transferCodeFor
import com.qvoste.qtalk.ui.calls.model.EmployeePresenceService
import com.qvoste.qtalk.ui.calls.model.DisabledEmployeePresenceService
import com.qvoste.qtalk.ui.calls.model.EmployeeStatus
import com.qvoste.qtalk.ui.calls.model.latestFrom
import com.qvoste.qtalk.ui.calls.model.classifyForAccount
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class CallsViewModel(
    private val voipEngine: VoipEngine,
    private val connectSip: ConnectSipUseCase,
    private val accountStore: SipAccountStore,
    private val contactStore: ContactStore,
    private val contactDatabaseService: ContactDatabaseService,
    private val contactNoteStore: ContactNoteStore,
    private val callHistoryStore: CallHistoryStore,
    private val employeePresenceService: EmployeePresenceService = DisabledEmployeePresenceService
) {
    val registrationState = voipEngine.registrationState
    val registrationMessage = voipEngine.registrationMessage
    val connectionStatus = voipEngine.connectionStatus
    val callStatus = voipEngine.callStatus
    val conferenceStatus = voipEngine.conferenceStatus
    val audioSettings = voipEngine.audioSettings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commandMutex = Mutex()
    private val contactSyncMutex = Mutex()
    private val presencePublishMutex = Mutex()
    private val knownEmployeeStatuses = MutableStateFlow<Map<String, EmployeeStatus>>(emptyMap())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val savedAccount = accountStore.load()
    private val _accountNumber = MutableStateFlow(savedAccount?.username?.trim().orEmpty())
    val accountNumber = _accountNumber.asStateFlow()
    private val _authenticationRequired = MutableStateFlow(savedAccount == null)
    val authenticationRequired = _authenticationRequired.asStateFlow()
    private val _accountDraft = MutableStateFlow(savedAccount)
    val accountDraft = _accountDraft.asStateFlow()
    private val _contacts = MutableStateFlow(contactStore.load().classifyForAccount(_accountNumber.value))
    val contacts = _contacts.asStateFlow()
    private val _contactNotes = MutableStateFlow(contactNoteStore.load())
    val contactNotes = _contactNotes.asStateFlow()
    private val _callHistory = MutableStateFlow(
        callHistoryStore.load().sortedByDescending(HistoryItem::startedAtEpochMillis)
    )
    val callHistory = _callHistory.asStateFlow()
    private val savedContactDatabaseUrl = contactDatabaseService.loadSourceUrl()
    private val _contactDatabaseState = MutableStateFlow(
        ContactDatabaseState(
            sourceUrl = savedContactDatabaseUrl,
            message = if (savedContactDatabaseUrl.isBlank()) {
                "Google Таблица не подключена"
            } else {
                "Ожидает синхронизации"
            }
        )
    )
    val contactDatabaseState = _contactDatabaseState.asStateFlow()
    private var pendingAccount: SipAccount? = null
    private var lastRecordedIncomingId: String? = null
    private var activeHistoryId: String? = null
    private var activeCallSeen = false
    private var connectedAtMillis = 0L
    private var lastPublishedStatus: EmployeeStatus? = null

    init {
        scope.launch { voipEngine.configureConferenceService(employeePresenceService.loadEndpoint()) }
        scope.launch {
            registrationState.collectLatest { state ->
                when (state) {
                    RegistrationState.REGISTERED -> {
                        pendingAccount?.let { account ->
                            accountStore.save(account)
                            _accountDraft.value = account.copy(password = null)
                            _authenticationRequired.value = false
                            pendingAccount = null
                        }
                        publishPresence(currentPresenceStatus())
                    }
                    RegistrationState.FAILED -> {
                        publishPresence(EmployeeStatus.OFFLINE)
                    }
                    else -> Unit
                }
            }
        }

        scope.launch {
            callStatus.collectLatest { status ->
                if (registrationState.value == RegistrationState.REGISTERED) {
                    publishPresence(if (status.state.isInProgress) EmployeeStatus.BUSY else EmployeeStatus.AVAILABLE)
                }
                if (status.state == com.qvoste.qtalk.voip.CallState.INCOMING) {
                    val startedAt = status.startedAtEpochMillis.takeIf { it > 0L }
                        ?: Clock.System.now().toEpochMilliseconds()
                    val incomingId = "incoming:${status.number}:$startedAt"
                    if (lastRecordedIncomingId != incomingId) {
                        lastRecordedIncomingId = incomingId
                        recordCall(status.number, "Входящий", startedAt, incomingId)
                    }
                }
                if (status.state.isInProgress) activeCallSeen = true
                if (status.connectedAtEpochMillis > 0L) connectedAtMillis = status.connectedAtEpochMillis
                if (activeCallSeen && status.state in setOf(
                        com.qvoste.qtalk.voip.CallState.ENDED,
                        com.qvoste.qtalk.voip.CallState.FAILED
                    )
                ) {
                    completeRecordedCall(status)
                }
            }
        }

        savedAccount?.let(::connect)

        scope.launch {
            while (true) {
                val url = _contactDatabaseState.value.sourceUrl
                if (url.isNotBlank()) performContactSync(url)
                delay(CONTACT_SYNC_INTERVAL_MS)
            }
        }

        scope.launch {
            while (true) {
                if (employeePresenceService.enabled) {
                    if (registrationState.value == RegistrationState.REGISTERED) {
                        publishPresence(currentPresenceStatus(), force = true)
                    }
                }
                delay(PRESENCE_HEARTBEAT_INTERVAL_MS)
            }
        }
        scope.launch {
            while (true) {
                if (employeePresenceService.enabled) refreshEmployeeStatuses()
                delay(PRESENCE_SYNC_INTERVAL_MS)
            }
        }
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
        _contacts.value = _contacts.value.classifyForAccount(_accountNumber.value)
        _accountDraft.value = account
        pendingAccount = account
        connectSip(account)
    }

    fun logout() = execute {
        publishPresence(EmployeeStatus.OFFLINE, force = true)
        voipEngine.unregister()
        accountStore.clear()
        pendingAccount = null
        _accountDraft.value = null
        _accountNumber.value = ""
        _contacts.value = _contacts.value.classifyForAccount("")
        _authenticationRequired.value = true
    }

    fun call(number: String) = execute {
        val dialableNumber = dialablePhoneNumber(number)
        val startedAt = Clock.System.now().toEpochMilliseconds()
        recordCall(dialableNumber, "Исходящий", startedAt, "outgoing:$dialableNumber:$startedAt")
        voipEngine.call(dialableNumber)
    }
    fun acceptCall() = execute { voipEngine.acceptCall() }
    fun retryRegistration() = execute { voipEngine.retryRegistration() }
    fun editSipAccount() { _authenticationRequired.value = true }
    fun declineCall() = execute { voipEngine.declineCall() }
    fun hangUp() = execute { voipEngine.hangUp() }
    fun inviteConferenceParticipant(number: String) = execute {
        voipEngine.inviteConferenceParticipant(dialablePhoneNumber(number))
    }
    fun mergeConference() = execute { voipEngine.mergeConference() }
    fun cancelConferenceInvite() = execute { voipEngine.cancelConferenceInvite() }
    fun removeConferenceParticipant(number: String) = execute { voipEngine.removeConferenceParticipant(number) }
    fun inviteConferenceParticipants(numbers: List<String>) = execute {
        voipEngine.inviteConferenceParticipants(numbers.map(::dialablePhoneNumber))
    }
    fun startConference(numbers: List<String>) = execute {
        voipEngine.startConference(numbers.map(::dialablePhoneNumber))
    }
    fun sendDtmf(digit: Char) {
        scope.launch {
            try {
                voipEngine.sendDtmf(digit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Не удалось отправить DTMF"
            }
        }
    }
    fun transferToEmployee(contact: Contact) = execute {
        check(conferenceStatus.value.phase == com.qvoste.qtalk.voip.ConferencePhase.NONE) {
            "Перевод недоступен во время конференции"
        }
        val classifiedContact = contact.latestFrom(_contacts.value.classifyForAccount(_accountNumber.value))
            ?: error("Контакт больше не найден в справочнике")
        val code = transferCodeFor(classifiedContact)
            ?: error("Перевод доступен только на внутренний номер сотрудника")
        val activeCall = voipEngine.callStatus.value
        check(activeCall.state == com.qvoste.qtalk.voip.CallState.ACTIVE) {
            "Перевод доступен только во время разговора"
        }
        code.forEachIndexed { index, digit ->
            check(voipEngine.callStatus.value.state == com.qvoste.qtalk.voip.CallState.ACTIVE &&
                voipEngine.callStatus.value.connectedAtEpochMillis == activeCall.connectedAtEpochMillis) {
                "Звонок завершился до перевода"
            }
            voipEngine.sendDtmf(digit)
            if (index != code.lastIndex) delay(120)
        }
    }
    fun setConferenceHeld(held: Boolean) = execute { voipEngine.setConferenceHeld(held) }

    fun setMicrophoneMuted(muted: Boolean) = execute {
        voipEngine.setMicrophoneMuted(muted)
    }
    fun setSpeakerMuted(muted: Boolean) = execute {
        voipEngine.setSpeakerMuted(muted)
    }
    fun refreshAudioDevices() = execute { voipEngine.refreshAudioDevices() }
    fun selectInputAudioDevice(deviceId: String) = execute {
        voipEngine.selectInputAudioDevice(deviceId)
    }
    fun selectOutputAudioDevice(deviceId: String) = execute {
        voipEngine.selectOutputAudioDevice(deviceId)
    }
    fun setMicrophoneSensitivity(value: Float) = execute {
        voipEngine.setMicrophoneSensitivity(value)
    }
    fun setSpeakerVolume(value: Float) = execute {
        voipEngine.setSpeakerVolume(value)
    }
    fun setRingtoneVolume(value: Float) = execute {
        voipEngine.setRingtoneVolume(value)
    }
    fun previewRingtone(ringtoneId: String) = execute {
        voipEngine.previewRingtone(ringtoneId)
    }
    fun selectRingtone(ringtoneId: String) = execute {
        voipEngine.selectRingtone(ringtoneId)
    }
    fun addContact(contact: Contact) {
        val updated = _contacts.value + contact.copy(source = ContactSource.LOCAL)
        contactStore.save(updated)
        _contacts.value = updated.classifyForAccount(_accountNumber.value)
    }
    fun updateContact(previous: Contact, updated: Contact) {
        val contacts = _contacts.value.toMutableList()
        val index = contacts.indexOf(previous.latestFrom(contacts))
        if (index < 0) return
        contacts[index] = updated.copy(status = contacts[index].status)
        contactStore.save(contacts)
        _contacts.value = contacts.classifyForAccount(_accountNumber.value)
        if (previous.name != updated.name || previous.number != updated.number) {
            val notes = _contactNotes.value.map { note ->
                if (note.belongsTo(previous)) note.copy(
                    contactName = updated.name,
                    contactNumber = updated.number
                ) else note
            }
            contactNoteStore.save(notes)
            _contactNotes.value = notes
            val history = _callHistory.value.map { item ->
                if (item.name == previous.name &&
                    item.number.normalizedContactNumber() == previous.number.normalizedContactNumber()
                ) item.copy(name = updated.name) else item
            }
            callHistoryStore.save(history)
            _callHistory.value = history
        }
    }
    fun deleteContact(contact: Contact) {
        val updated = _contacts.value.toMutableList().apply { remove(contact.latestFrom(this)) }
        contactStore.save(updated)
        _contacts.value = updated.classifyForAccount(_accountNumber.value)
    }
    fun markMissedCallsSeen() {
        val current = _callHistory.value
        val updated = current.map { item ->
            if (item.missed && !item.missedAcknowledged) {
                item.copy(missedAcknowledged = true)
            } else {
                item
            }
        }
        if (updated == current) return
        callHistoryStore.save(updated)
        _callHistory.value = updated
    }
    fun addContactNote(contact: Contact, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        val nextId = ((_contactNotes.value.mapNotNull { it.id.toLongOrNull() }.maxOrNull() ?: 0L) + 1L).toString()
        val updated = _contactNotes.value + ContactNote(
            id = nextId,
            contactName = contact.name,
            contactNumber = contact.number,
            text = normalizedText
        )
        contactNoteStore.save(updated)
        _contactNotes.value = updated
    }
    fun updateContactNote(note: ContactNote, text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        updateNotes { notes -> notes.map { if (it.id == note.id) it.copy(text = normalizedText) else it } }
    }
    fun deleteContactNote(note: ContactNote) {
        updateNotes { notes -> notes.filterNot { it.id == note.id } }
    }
    private fun updateNotes(transform: (List<ContactNote>) -> List<ContactNote>) {
        val updated = transform(_contactNotes.value)
        contactNoteStore.save(updated)
        _contactNotes.value = updated
    }

    private fun recordCall(number: String, direction: String, startedAt: Long, id: String) {
        activeHistoryId = id
        activeCallSeen = direction == "Входящий"
        connectedAtMillis = 0L
        val normalizedNumber = number.normalizedContactNumber()
        val contact = _contacts.value.firstOrNull {
            it.number.normalizedContactNumber() == normalizedNumber
        }
        val item = HistoryItem(
            id = id,
            name = contact?.name ?: formatPhoneNumber(number).ifBlank { "Неизвестный номер" },
            number = number,
            direction = direction,
            startedAtEpochMillis = startedAt,
            telegram = contact?.telegram.orEmpty(),
            avatarUrl = contact?.avatarUrl.orEmpty()
        )
        val updated = (listOf(item) + _callHistory.value.filterNot { it.id == id }).take(MAX_HISTORY_ITEMS)
        callHistoryStore.save(updated)
        _callHistory.value = updated
    }

    private fun completeRecordedCall(status: com.qvoste.qtalk.voip.CallStatus) {
        val id = activeHistoryId ?: return
        val connectedAt = status.connectedAtEpochMillis.takeIf { it > 0L }
            ?: connectedAtMillis.takeIf { it > 0L }
        val seconds = if (connectedAt == null) 0L else
            ((Clock.System.now().toEpochMilliseconds() - connectedAt) / 1_000L).coerceAtLeast(1L)
        val updated = _callHistory.value.map { item ->
            if (item.id == id) item.copy(
                missed = item.direction == "Входящий" && connectedAt == null,
                durationSeconds = seconds
            ) else item
        }
        callHistoryStore.save(updated)
        _callHistory.value = updated
        activeHistoryId = null
        activeCallSeen = false
        connectedAtMillis = 0L
    }
    fun saveContactDatabaseUrl(url: String) = execute {
        val normalized = url.trim()
        employeePresenceService.saveEndpoint("")
        voipEngine.configureConferenceService("")
        lastPublishedStatus = null
        knownEmployeeStatuses.value = emptyMap()
        contactDatabaseService.saveSourceUrl(normalized)
        _contactDatabaseState.value = ContactDatabaseState(
            sourceUrl = normalized,
            message = if (normalized.isBlank()) "Google Таблица не подключена" else "Ожидает синхронизации"
        )
        if (normalized.isNotBlank()) syncContactDatabase()
    }
    fun syncContactDatabase() {
        val url = _contactDatabaseState.value.sourceUrl
        if (url.isBlank()) return
        scope.launch { performContactSync(url) }
    }
    fun disconnectContactDatabase() = saveContactDatabaseUrl("")

    private suspend fun performContactSync(url: String) {
        if (!contactSyncMutex.tryLock()) return
        try {
            _contactDatabaseState.value = _contactDatabaseState.value.copy(
                sourceUrl = url,
                syncing = true,
                message = "Загрузка контактов…",
                lastSyncSuccessful = null
            )
            val downloaded = contactDatabaseService.downloadContacts(url)
            val presenceEndpoint = contactDatabaseService.downloadPresenceEndpoint(url)
            if (_contactDatabaseState.value.sourceUrl != url) return
            voipEngine.configureConferenceService(presenceEndpoint)
            if (employeePresenceService.loadEndpoint() != presenceEndpoint) {
                employeePresenceService.saveEndpoint(presenceEndpoint)
                lastPublishedStatus = null
                if (registrationState.value == RegistrationState.REGISTERED) {
                    publishPresence(currentPresenceStatus(), force = true)
                }
            }
            val merged = mergeDownloadedContacts(_contacts.value, downloaded).map { contact ->
                knownEmployeeStatuses.value[contact.number.filter(Char::isDigit)]
                    ?.let { contact.copy(status = it) } ?: contact
            }
            contactStore.save(merged)
            _contacts.value = merged.classifyForAccount(_accountNumber.value)
            _contactDatabaseState.value = _contactDatabaseState.value.copy(
                syncing = false,
                message = "Загружено контактов: ${merged.size}",
                lastSyncSuccessful = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _contactDatabaseState.value = _contactDatabaseState.value.copy(
                syncing = false,
                message = e.message ?: "Не удалось загрузить контакты",
                lastSyncSuccessful = false
            )
        } finally {
            contactSyncMutex.unlock()
        }
    }

    private fun currentPresenceStatus(): EmployeeStatus =
        if (callStatus.value.state.isInProgress) EmployeeStatus.BUSY else EmployeeStatus.AVAILABLE

    private suspend fun publishPresence(status: EmployeeStatus, force: Boolean = false) {
        val extension = _accountNumber.value.filter(Char::isDigit)
            .ifBlank { _accountDraft.value?.username.orEmpty().filter(Char::isDigit) }
        if (extension.isBlank()) return
        applyPresence(mapOf(extension to status))
        if (!employeePresenceService.enabled) return
        presencePublishMutex.withLock {
            if (!force && lastPublishedStatus == status) return
            try {
                employeePresenceService.publish(extension, status)
                lastPublishedStatus = status
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A failed publication is retried by the next heartbeat.
            }
        }
    }

    private suspend fun refreshEmployeeStatuses() {
        try {
            val statuses = employeePresenceService.load().toMutableMap()
            val ownExtension = _accountNumber.value.filter(Char::isDigit)
            // A read started before our publication must not roll back our own status.
            if (ownExtension.isNotBlank()) {
                statuses[ownExtension] = if (registrationState.value == RegistrationState.REGISTERED)
                    currentPresenceStatus() else EmployeeStatus.OFFLINE
            }
            applyPresence(statuses)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the last known statuses during a temporary network failure.
        }
    }

    private fun applyPresence(statuses: Map<String, EmployeeStatus>) {
        if (statuses.isEmpty()) return
        knownEmployeeStatuses.update { it + statuses }
        _contacts.update { contacts -> contacts.map { contact ->
            val extension = contact.number.filter(Char::isDigit)
            statuses[extension]?.let { contact.copy(status = it) } ?: contact
        }.classifyForAccount(_accountNumber.value) }
    }

    fun close() = scope.cancel()

    private companion object {
        const val CONTACT_SYNC_INTERVAL_MS = 15 * 60 * 1000L
        const val PRESENCE_SYNC_INTERVAL_MS = 5 * 1000L
        const val PRESENCE_HEARTBEAT_INTERVAL_MS = 30 * 1000L
        const val MAX_HISTORY_ITEMS = 1000
    }
}
