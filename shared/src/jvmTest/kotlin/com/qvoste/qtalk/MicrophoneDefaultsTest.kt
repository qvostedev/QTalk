package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlin.test.*

class MicrophoneDefaultsTest {
    @Test fun freshSettingsAndResetUseFiftyPercent() {
        assertEquals(.5f, AudioSettings().microphoneSensitivity)
        val store = AudioGainPreferences(MemoryAudioPreferences())
        assertEquals(.5f, store.load().microphone)
        store.save(GainLevels(.1f, .2f))
        assertEquals(.5f, store.reset().microphone)
    }

    @Test fun existingUnityBecomesFiftyPercentAndObsoleteBoostIsRemoved() {
        val prefs = MemoryAudioPreferences()
        prefs.putInt("gainSchema", 3)
        prefs.putFloat("microphoneSensitivity", 1f)
        prefs.putFloat("speakerVolume", .3f)
        prefs.putBoolean("microphoneBoost", true)
        val store = AudioGainPreferences(prefs)
        val loaded = store.load()
        assertEquals(.5f, loaded.microphone)
        assertEquals(.3f, loaded.speaker)
        assertNull(prefs.get("microphoneBoost", null))
        assertEquals(4, prefs.getInt("gainSchema", 0))
        assertEquals(loaded, store.load())
    }

    @Test fun migrationPreservesQuietSettingsAndManualZero() {
        for (amplitude in listOf(0f, .1f, .5f, 1f)) {
            val prefs = MemoryAudioPreferences()
            prefs.putInt("gainSchema", 3)
            prefs.putFloat("microphoneSensitivity", amplitude)
            val loaded = AudioGainPreferences(prefs).load()
            assertEquals(amplitude / 2f, loaded.microphone)
        }
    }
}
