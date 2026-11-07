package com.qvoste.qtalk.voip

import java.util.prefs.Preferences
import kotlin.math.*

internal object AudioGain {
    fun level(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    // Zero is gated separately. A finite floor keeps the native API free of infinities.
    fun db(value: Float): Float = if (level(value) == 0f) -120f else 20f * log10(level(value))
    // Midpoint is unmodified input; full scale is bounded at 2x (+6.02 dB).
    fun microphoneDb(value: Float): Float = if (level(value) == 0f) -120f else 20f * log10(2f * level(value))
    fun muted(manual: Boolean, held: Boolean, level: Float, staged: Boolean = false): Boolean =
        manual || held || level(level) == 0f || staged
    // Old zero meant -30 dB, not mute: preserve that quiet nonzero level deliberately.
    fun migrate(value: Float?): Float = when {
        value == null -> 1f
        value.isNaN() -> 0f
        else -> 10f.pow((level(value) * 60f - 30f) / 20f).coerceAtMost(1f)
    }
}

internal data class GainLevels(val microphone: Float = .5f, val speaker: Float = 1f) {
    fun step(target: GainLevels): GainLevels {
        fun move(value: Float, to: Float, amount: Float): Float = value + (to - value).coerceIn(-amount, amount)
        return GainLevels(move(microphone, target.microphone, .02f), move(speaker, target.speaker, .04f))
    }
}

internal class AudioGainPreferences(private val prefs: Preferences) {
    fun load(): GainLevels {
        val schema = prefs.getInt("gainSchema", 0)
        fun stored(key: String): Float? = prefs.get(key, null)?.toFloatOrNull()
        val microphone = stored("microphoneSensitivity")
        val speaker = stored("speakerVolume")
        return GainLevels(
            when {
                schema >= 4 -> AudioGain.level(microphone ?: .5f)
                schema >= 3 -> AudioGain.level(microphone ?: 1f) / 2f
                else -> AudioGain.migrate(microphone) / 2f
            },
            if (schema >= 3) AudioGain.level(speaker ?: 1f) else AudioGain.migrate(speaker)
        ).also {
            if (schema < 4 || prefs.get("microphoneBoost", null) != null) save(it)
        }
    }
    fun save(levels: GainLevels) {
        prefs.putFloat("microphoneSensitivity", AudioGain.level(levels.microphone))
        prefs.putFloat("speakerVolume", AudioGain.level(levels.speaker))
        prefs.remove("microphoneBoost")
        prefs.putInt("gainSchema", 4)
        prefs.flush()
    }
    fun reset(): GainLevels = GainLevels().also(::save)
}
