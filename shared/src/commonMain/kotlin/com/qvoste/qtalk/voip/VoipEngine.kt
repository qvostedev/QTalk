package com.qvoste.qtalk.voip

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

private val disconnectedTelephony = MutableStateFlow(TelephonyConnection.DISCONNECTED)

interface VoipEngine {
    val registrationState: StateFlow<RegistrationState>
    val registrationMessage: StateFlow<String>
    val connectionStatus: StateFlow<TelephonyConnection> get() = disconnectedTelephony
    suspend fun retryRegistration() = Unit

    val callStatus: StateFlow<CallStatus>
    val audioSettings: StateFlow<AudioSettings>

    suspend fun call(number: String)
    suspend fun acceptCall()
    suspend fun declineCall()
    suspend fun hangUp()
    suspend fun sendDtmf(digit: Char)
    suspend fun setMicrophoneMuted(muted: Boolean)
    suspend fun setSpeakerMuted(muted: Boolean)

    suspend fun start()
    suspend fun stop()

    suspend fun register(account: SipAccount)
    suspend fun unregister()

    suspend fun refreshAudioDevices()
    suspend fun selectInputAudioDevice(deviceId: String)
    suspend fun selectOutputAudioDevice(deviceId: String)
    suspend fun setMicrophoneSensitivity(value: Float)
    suspend fun setSpeakerVolume(value: Float)
    suspend fun setRingtoneVolume(value: Float)
    suspend fun previewRingtone(ringtoneId: String)
    suspend fun selectRingtone(ringtoneId: String)
}
