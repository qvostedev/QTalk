package com.qvoste.qtalk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QTalkBuildInfoTest {
    @Test fun generatedVersionMatchesTheBuildProperty() {
        val expected = assertNotNull(System.getProperty("qtalk.version"))
        assertEquals(expected, QTalkBuildInfo.VERSION)
    }
}
