package com.qvoste.qtalk.voip

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Оставлен в качестве заглушки
class FakeVoipEngine : VoipEngine {
    private val _registrationState = MutableStateFlow(RegistrationState.DISCONNECTED)
    override val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()
    override val registrationMessage: StateFlow<String> = MutableStateFlow("")

    private val _callStatus = MutableStateFlow(CallStatus())
    override val callStatus = _callStatus.asStateFlow()
    private val _audioSettings = MutableStateFlow(
        AudioSettings(
            inputDevices = listOf(AudioDeviceInfo("default-input", "Системный микрофон")),
            outputDevices = listOf(AudioDeviceInfo("default-output", "Системные динамики")),
            selectedInputDeviceId = "default-input",
            selectedOutputDeviceId = "default-output"
        )
    )
    override val audioSettings = _audioSettings.asStateFlow()

    override suspend fun call(number: String) {
        _callStatus.value = CallStatus(CallState.FAILED, number, "Звонки доступны только в desktop-версии")
    }

    override suspend fun acceptCall() {}
    override suspend fun declineCall() {}
    override suspend fun hangUp() {}
    override suspend fun sendDtmf(digit: Char) {}
    override suspend fun setMicrophoneMuted(muted: Boolean) {}
    override suspend fun setSpeakerMuted(muted: Boolean) {}

    override suspend fun start() {}
    override suspend fun stop() {}

    override suspend fun register(account: SipAccount) {
        _registrationState.value = RegistrationState.CONNECTING
        delay(1000)
        _registrationState.value = RegistrationState.REGISTERED
    }

    override suspend fun unregister() {
        _registrationState.value = RegistrationState.DISCONNECTED
    }

    override suspend fun refreshAudioDevices() = Unit

    override suspend fun selectInputAudioDevice(deviceId: String) {
        _audioSettings.value = _audioSettings.value.copy(selectedInputDeviceId = deviceId)
    }

    override suspend fun selectOutputAudioDevice(deviceId: String) {
        _audioSettings.value = _audioSettings.value.copy(selectedOutputDeviceId = deviceId)
    }

    override suspend fun setMicrophoneSensitivity(value: Float) {
        _audioSettings.value = _audioSettings.value.copy(microphoneSensitivity = value.coerceIn(0f, 1f))
    }

    override suspend fun setSpeakerVolume(value: Float) {
        _audioSettings.value = _audioSettings.value.copy(speakerVolume = value.coerceIn(0f, 1f))
    }

    override suspend fun setRingtoneVolume(value: Float) {
        _audioSettings.value = _audioSettings.value.copy(ringtoneVolume = value.coerceIn(0f, 1f))
    }

    override suspend fun previewRingtone(ringtoneId: String) = Unit

    override suspend fun selectRingtone(ringtoneId: String) {
        if (_audioSettings.value.ringtones.none { it.id == ringtoneId }) return
        _audioSettings.value = _audioSettings.value.copy(selectedRingtoneId = ringtoneId)
    }
}
