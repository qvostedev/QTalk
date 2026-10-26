package com.qvoste.qtalk

import com.qvoste.qtalk.voip.SpeechActivityDetector
import kotlin.test.*

class SpeechActivityDetectorTest {
    private class Input {
        val detector = SpeechActivityDetector()
        var time = 0L
        fun feed(level: Float, count: Int, enabled: Boolean = true): List<Boolean> =
            List(count) { time += 50_000_000L; detector.update(level, time, enabled) }
    }

    @Test fun steadyBackgroundAndComfortNoiseStayQuiet() {
        for (noise in listOf(-120f, -60f, -43f, -34f, -27f, -18f)) {
            val input = Input()
            assertTrue(input.feed(noise, 100).none { it }, "Background $noise must not light up")
        }
    }
    @Test fun speechStartsQuicklyAndReleasesAfterShortPauses() {
        val input = Input()
        input.feed(-43f, 20)
        val onset = input.feed(-22f, 5)
        assertTrue(onset.last())
        assertTrue(input.feed(-43f, 2).all { it }, "Don't flicker between syllables")
        assertFalse(input.feed(-43f, 5).last(), "Stop within 250ms of silence")
        assertTrue(input.feed(-26f, 5).last())
    }
    @Test fun isolatedClicksDoNotActivateAndParticipantsAreIndependent() {
        val first = Input(); val second = Input()
        first.feed(-40f, 20); second.feed(-30f, 20)
        assertFalse(first.feed(-8f, 1).single())
        assertFalse(first.feed(-40f, 6).last())
        assertTrue(first.feed(-20f, 5).last())
        assertFalse(second.feed(-30f, 5).last())
    }
    @Test fun muteInvalidReadingsAndStaleStreamsClearImmediately() {
        val input = Input(); input.feed(-50f, 20)
        assertTrue(input.feed(-22f, 5).last())
        assertFalse(input.feed(-22f, 1, enabled = false).single())
        assertFalse(input.feed(Float.NaN, 1).single())
        assertFalse(input.feed(Float.POSITIVE_INFINITY, 1).single())
        input.feed(-50f, 20); input.feed(-22f, 5)
        input.time += 2_000_000_000L
        assertFalse(input.feed(-120f, 1).single())
    }
}
