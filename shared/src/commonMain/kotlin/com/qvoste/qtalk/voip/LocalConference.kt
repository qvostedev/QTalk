package com.qvoste.qtalk.voip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConferencePhase { NONE, INVITING, CONSULTING, MERGING, ACTIVE, RESTORING }
enum class ConferenceLegState { DIALING, RINGING, ACTIVE, PAUSING, PAUSED, ENDED, FAILED, RELEASED }

data class ConferenceStatus(
    val phase: ConferencePhase = ConferencePhase.NONE,
    val participants: List<String> = emptyList(),
    val invitedNumber: String = "",
    val message: String = "",
    val microphoneMuted: Boolean = false,
    val speakerMuted: Boolean = false,
    val speakingNumbers: Set<String> = emptySet(),
    val mutedNumbers: Set<String> = emptySet(),
    val localSpeaking: Boolean = false
)

internal interface LocalConferenceBackend<T : Any> {
    fun number(call: T): String
    fun state(call: T): ConferenceLegState
    fun pause(call: T)
    fun resume(call: T)
    fun invite(number: String): T
    fun merge(first: T, second: T)
    fun end(call: T)
}

/** Coordinates two SIP legs. The backend and callbacks run on the engine's single dispatcher. */
internal class LocalConferenceSession<T : Any>(
    private val backend: LocalConferenceBackend<T>,
    private val onPrimaryChanged: (T) -> Unit,
    private val onFinished: () -> Unit
) {
    private val mutableStatus = MutableStateFlow(ConferenceStatus())
    val status = mutableStatus.asStateFlow()
    private var first: T? = null
    private var second: T? = null
    private var pendingInvite = false
    private var creatingInvite = false
    private var endingAll = false

    val foregroundCall: T? get() = second?.takeIf { status.value.phase == ConferencePhase.CONSULTING } ?: first ?: second
    val calls: List<T> get() = listOfNotNull(first, second).distinct()
    val hasSession: Boolean get() = status.value.phase != ConferencePhase.NONE

    fun begin(primary: T, number: String) {
        check(!hasSession) { "Конференция уже создаётся" }
        require(number.isNotBlank() && number != backend.number(primary)) { "Выберите другого участника" }
        check(backend.state(primary) == ConferenceLegState.ACTIVE) { "Сначала дождитесь ответа собеседника" }
        first = primary
        second = null
        pendingInvite = true
        endingAll = false
        mutableStatus.value = ConferenceStatus(ConferencePhase.INVITING, listOf(backend.number(primary)), number)
        try { backend.pause(primary) } catch (error: Exception) {
            reset(error.message.orEmpty())
            throw error
        }
    }

    fun onCallState(call: T, state: ConferenceLegState): Boolean {
        if (!hasSession) return false
        if (creatingInvite && call != first && second == null) second = call
        if (call != first && call != second) return false
        if (state in setOf(ConferenceLegState.ENDED, ConferenceLegState.FAILED, ConferenceLegState.RELEASED)) {
            if (call == second) second = null else first = null
            if (endingAll) {
                if (first == null && second == null) { reset(); onFinished() }
                else (first ?: second)?.let(onPrimaryChanged)
                return true
            }
            if (first == null && second == null) { reset(); onFinished(); return true }
            if (first == null) {
                first = second
                second = null
                first?.let(onPrimaryChanged)
                if (status.value.phase != ConferencePhase.ACTIVE) restore("Первый собеседник завершил звонок")
                else updateParticipants()
            } else if (status.value.phase == ConferencePhase.ACTIVE) updateParticipants()
            else restore("Участник отключился или не ответил")
            return true
        }
        if (call == first && state == ConferenceLegState.PAUSED) {
            if (pendingInvite) startInvite() else if (status.value.phase == ConferencePhase.RESTORING) resumeFirst()
        } else if (call == second && state == ConferenceLegState.ACTIVE && status.value.phase == ConferencePhase.INVITING) {
            mutableStatus.value = status.value.copy(phase = ConferencePhase.CONSULTING)
        } else if (call == first && state == ConferenceLegState.ACTIVE && status.value.phase == ConferencePhase.RESTORING) {
            reset(status.value.message)
            return false
        }
        return true
    }

    private fun startInvite() {
        pendingInvite = false
        creatingInvite = true
        try {
            val invited = backend.invite(status.value.invitedNumber)
            if (status.value.phase == ConferencePhase.INVITING) second = invited
        } catch (error: Exception) { restore(error.message ?: "Не удалось добавить участника") }
        finally { creatingInvite = false }
    }

    fun merge() {
        check(status.value.phase == ConferencePhase.CONSULTING) { "Дождитесь ответа участника" }
        val primary = checkNotNull(first)
        val invited = checkNotNull(second)
        mutableStatus.value = status.value.copy(phase = ConferencePhase.MERGING)
        try {
            backend.merge(primary, invited)
            if (status.value.phase != ConferencePhase.MERGING || first != primary || second != invited) return
            mutableStatus.value = status.value.copy(phase = ConferencePhase.ACTIVE, message = "")
            updateParticipants()
        } catch (error: Exception) {
            if (status.value.phase == ConferencePhase.MERGING) {
                mutableStatus.value = status.value.copy(phase = ConferencePhase.CONSULTING,
                    message = error.message ?: "Не удалось объединить звонки")
            }
            throw error
        }
    }

    fun cancel() {
        if (!hasSession) return
        check(status.value.phase != ConferencePhase.ACTIVE) { "Конференция уже началась" }
        pendingInvite = false
        val invited = second
        mutableStatus.value = status.value.copy(phase = ConferencePhase.RESTORING)
        if (invited != null) backend.end(invited) else resumeFirst()
    }

    fun hangUpAll() {
        endingAll = true
        pendingInvite = false
        val calls = this.calls
        var failure: Exception? = null
        calls.forEach { call ->
            try { backend.end(call) } catch (error: Exception) { failure = error }
        }
        if (calls.isEmpty()) { reset(); onFinished() }
        failure?.let { throw it }
    }

    fun removeParticipant(number: String) {
        check(status.value.phase == ConferencePhase.ACTIVE) { "Конференция ещё не началась" }
        val call = calls.firstOrNull { backend.number(it) == number } ?: return
        backend.end(call)
    }

    fun updateAudioState(microphoneMuted: Boolean, speakerMuted: Boolean,
        speakingNumbers: Set<String>, mutedNumbers: Set<String>, localSpeaking: Boolean) {
        mutableStatus.value = status.value.copy(microphoneMuted = microphoneMuted, speakerMuted = speakerMuted,
            speakingNumbers = speakingNumbers, mutedNumbers = mutedNumbers,
            localSpeaking = localSpeaking && !microphoneMuted)
    }

    private fun restore(message: String) {
        pendingInvite = false
        mutableStatus.value = status.value.copy(phase = ConferencePhase.RESTORING, message = message)
        resumeFirst()
    }

    private fun resumeFirst() {
        val primary = first ?: return
        try {
            when (backend.state(primary)) {
                ConferenceLegState.PAUSED -> backend.resume(primary)
                ConferenceLegState.ACTIVE -> { onPrimaryChanged(primary); reset(status.value.message) }
                else -> Unit
            }
        } catch (error: Exception) {
            mutableStatus.value = status.value.copy(message = error.message ?: "Не удалось вернуться к первому звонку")
        }
    }

    private fun updateParticipants() {
        mutableStatus.value = status.value.copy(participants = listOfNotNull(first, second).map(backend::number))
    }

    fun reset(message: String = "") {
        first = null
        second = null
        pendingInvite = false
        creatingInvite = false
        endingAll = false
        mutableStatus.value = ConferenceStatus(message = message,
            microphoneMuted = status.value.microphoneMuted, speakerMuted = status.value.speakerMuted)
    }
}
