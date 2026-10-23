package com.qvoste.qtalk

import com.qvoste.qtalk.voip.isSpeechLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechLevelTest {
    @Test fun silenceComfortNoiseAndUnavailableReadingsAreNotSpeech() {
        listOf(-120f, -60f, -43.27f, -40f, -35f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
            .forEach { assertFalse(isSpeechLevel(it), "Invalid speech indication at $it") }
        listOf(-30f, -20f, -4f, 0f).forEach { assertTrue(isSpeechLevel(it)) }
    }
}
