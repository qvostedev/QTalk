package com.qvoste.qtalk.voip

data class AudioDeviceInfo(
    val id: String,
    val name: String
)

data class RingtoneInfo(
    val id: String,
    val name: String
)

val QTalkRingtones = listOf(
    RingtoneInfo("warm", "Вариант - 1"),
    RingtoneInfo("round", "Вариант - 2")
)

data class AudioSettings(
    val inputDevices: List<AudioDeviceInfo> = emptyList(),
    val outputDevices: List<AudioDeviceInfo> = emptyList(),
    val selectedInputDeviceId: String? = null,
    val selectedOutputDeviceId: String? = null,
    val microphoneSensitivity: Float = 0.5f,
    val speakerVolume: Float = 0.5f,
    val ringtoneVolume: Float = 0.5f,
    val ringtones: List<RingtoneInfo> = QTalkRingtones,
    val selectedRingtoneId: String = QTalkRingtones.first().id
)
