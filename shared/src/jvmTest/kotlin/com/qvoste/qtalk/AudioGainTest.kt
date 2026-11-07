package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import java.util.prefs.AbstractPreferences
import kotlin.math.*
import kotlin.test.*

internal class MemoryAudioPreferences : AbstractPreferences(null, "") {
    private val values = mutableMapOf<String, String>()
    var flushes = 0
    override fun putSpi(key: String, value: String) { values[key] = value }
    override fun getSpi(key: String): String? = values[key]
    override fun removeSpi(key: String) { values.remove(key) }
    override fun keysSpi(): Array<String> = values.keys.toTypedArray()
    override fun childrenNamesSpi(): Array<String> = emptyArray()
    override fun childSpi(name: String): AbstractPreferences = error("no children")
    override fun removeNodeSpi() = Unit
    override fun syncSpi() = Unit
    override fun flushSpi() { flushes++ }
}

class AudioGainTest {
    @Test fun regularControlsHaveUnitySilenceAndFiniteBounds() {
        assertEquals(1f, AudioSettings().microphoneSensitivity)
        assertEquals(1f, AudioSettings().speakerVolume)
        assertEquals(0f, AudioGain.db(1f))
        assertEquals(0f, AudioGain.level(Float.NaN))
        assertEquals(1f, AudioGain.level(Float.POSITIVE_INFINITY))
        assertEquals(0f, AudioGain.level(Float.NEGATIVE_INFINITY))
        assertEquals(1f, AudioGain.level(10f))
        assertEquals(0f, AudioGain.level(-10f))
        assertTrue(AudioGain.db(0f).isFinite())
        for (i in 1..100) {
            val amplitude = 10.0.pow(AudioGain.db(i / 100f) / 20.0)
            assertEquals(i / 100.0, amplitude, .000001)
            if (i > 1) assertTrue(AudioGain.db(i / 100f) > AudioGain.db((i - 1) / 100f))
        }
        assertTrue(AudioGain.muted(false, false, 0f))
        assertFalse(AudioGain.muted(false, false, 1f))
    }

    @Test fun migrationPreservesOldAttenuationClampsBoostAndRunsOnce() {
        for (old in listOf(0f, .25f, .5f, 1f)) {
            val prefs = MemoryAudioPreferences()
            prefs.putFloat("microphoneSensitivity", old); prefs.putFloat("speakerVolume", old)
            val store = AudioGainPreferences(prefs)
            val expected = 10.0.pow((old * 60 - 30) / 20.0).toFloat().coerceAtMost(1f)
            val migrated = store.load()
            assertEquals(expected, migrated.microphone, .000001f)
            assertEquals(expected, migrated.speaker, .000001f)
            assertEquals(0f, migrated.boostDb)
            assertEquals(3, prefs.getInt("gainSchema", 0))
            assertEquals(migrated, AudioGainPreferences(prefs).load())
            store.save(GainLevels(.2f, 0f, 6f))
            assertEquals(GainLevels(.2f, 0f, 6f), AudioGainPreferences(prefs).load())
            assertEquals(GainLevels(), store.reset())
            assertEquals(GainLevels(), store.reset())
            assertEquals(GainLevels(), store.load())
        }
        assertEquals(GainLevels(), AudioGainPreferences(MemoryAudioPreferences()).load())
        assertEquals(0f, AudioGain.migrate(Float.NaN))
    }

    @Test fun rampMovesInSmallMonotonicStepsAndLatestTargetWins() {
        var applied = GainLevels(0f, 0f)
        repeat(10) {
            val next = applied.step(GainLevels(1f, 1f, 6f))
            assertTrue(next.microphone > applied.microphone && next.microphone - applied.microphone <= .041f)
            assertTrue(next.speaker > applied.speaker && next.speaker - applied.speaker <= .041f)
            assertTrue(next.boostDb - applied.boostDb <= .51f)
            applied = next
        }
        val target = GainLevels(.1f, .2f)
        repeat(30) { applied = applied.step(target) }
        assertEquals(target, applied)
    }

    @Test fun manualMuteHoldAndStagedLegsSurviveVolumeChanges() {
        for (level in listOf(0f, .1f, 1f)) {
            assertTrue(AudioGain.muted(true, false, level))
            assertTrue(AudioGain.muted(false, true, level))
            assertTrue(AudioGain.muted(false, false, level, staged = true))
        }
        assertFalse(AudioGain.muted(false, false, 1f))
    }
}
