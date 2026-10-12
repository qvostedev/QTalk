package com.qvoste.qtalk.voip

data class AudioDeviceInfo(
    val id: String,
    val name: String
)

data class AudioSettings(
    val inputDevices: List<AudioDeviceInfo> = emptyList(),
    val outputDevices: List<AudioDeviceInfo> = emptyList(),
    val selectedInputDeviceId: String? = null,
    val selectedOutputDeviceId: String? = null,
    val microphoneSensitivity: Float = 0.5f,
    val ringtoneVolume: Float = 0.5f
)
