package com.qvoste.qtalk.voip

import java.util.ArrayDeque
import kotlin.math.max

/** Per-input adaptive noise gate. Times are monotonic nanoseconds. */
internal class SpeechActivityDetector {
    private data class Sample(val time: Long, val level: Float)
    private val samples = ArrayDeque<Sample>()
    private var aboveSince: Long? = null
    private var lastVoiceAt: Long? = null
    private var lastSampleAt: Long? = null

    fun update(level: Float, now: Long, enabled: Boolean = true): Boolean {
        if (!enabled || !level.isFinite() || level > 0f) {
            reset()
            return false
        }
        if (lastSampleAt?.let { now < it || now - it > 1_000_000_000L } == true) reset()
        lastSampleAt = now
        while (samples.isNotEmpty() && now - samples.first.time > 3_000_000_000L) samples.removeFirst()
        samples.addLast(Sample(now, level.coerceIn(-120f, 0f)))
        // A low percentile follows the background, rather than a loud syllable.
        val levels = samples.map { it.level }.sorted()
        val calibrated = now - samples.first.time >= 400_000_000L
        val floor = if (calibrated) levels[(levels.size - 1) / 5].coerceIn(-65f, 0f) else -50f
        val threshold = max(-48f, floor + if (lastVoiceAt != null) 4.5f else 8f)
        if (calibrated && level > threshold) {
            val start = aboveSince ?: now.also { aboveSince = it }
            if (now - start >= 60_000_000L) lastVoiceAt = now
        } else aboveSince = null
        val speaking = lastVoiceAt?.let { now - it < 240_000_000L } == true
        if (!speaking) lastVoiceAt = null
        return speaking
    }

    fun reset() {
        samples.clear()
        aboveSince = null
        lastVoiceAt = null
        lastSampleAt = null
    }
}
