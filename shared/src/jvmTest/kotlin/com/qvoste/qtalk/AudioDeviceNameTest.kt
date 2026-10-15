package com.qvoste.qtalk

import com.qvoste.qtalk.voip.readableAudioDeviceName
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioDeviceNameTest {
    @Test
    fun replacesBrokenCyrillicPrefixButKeepsReadableDeviceSuffix() {
        assertEquals(
            "Микрофон (2- USB Audio Device)",
            readableAudioDeviceName("�������� (2- USB Audio Device)", "capture", "Микрофон")
        )
        assertEquals(
            "Динамики (USB Audio Device)",
            readableAudioDeviceName("???????? - (USB Audio Device)", "playback", "Динамики")
        )
    }

    @Test
    fun localizesDefaultDevices() {
        assertEquals(
            "Системный микрофон",
            readableAudioDeviceName("Default Capture", "capture", "Микрофон")
        )
        assertEquals(
            "Системные динамики",
            readableAudioDeviceName("Default Playback", "playback", "Динамики")
        )
    }
}
