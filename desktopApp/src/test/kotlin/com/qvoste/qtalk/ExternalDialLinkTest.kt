package com.qvoste.qtalk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExternalDialLinkTest {
    @Test
    fun readsEncodedPhoneNumberWithoutStartingACall() {
        assertEquals("+79080214506", ExternalDialLink.numberFromArgument(
            "qtalk://dial?number=%2B79080214506"
        ))
        assertEquals("703", ExternalDialLink.numberFromArgument("qtalk://dial?number=703"))
    }

    @Test
    fun rejectsUnrelatedOrInvalidLinks() {
        assertNull(ExternalDialLink.numberFromArgument("https://example.com/?number=703"))
        assertNull(ExternalDialLink.numberFromArgument("qtalk://dial?number=%2B7%0A703"))
        assertNull(ExternalDialLink.numberFromArgument("qtalk://dial?number="))
    }

    @Test
    fun forwardsLinkToRunningInstance() {
        val directory = Files.createTempDirectory("qtalk-dial-test")
        val delivered = CountDownLatch(1)
        var received: String? = null
        val primary = assertNotNull(ExternalDialBridge.start(
            emptyArray(),
            onDial = { received = it; delivered.countDown() },
            onShow = {},
            directory = directory
        ))
        try {
            assertNull(ExternalDialBridge.start(
                arrayOf("qtalk://dial?number=%2B79080214506"),
                onDial = {},
                onShow = {},
                directory = directory
            ))
            kotlin.test.assertTrue(delivered.await(3, TimeUnit.SECONDS))
            assertEquals("+79080214506", received)
        } finally {
            primary.close()
            Files.deleteIfExists(directory.resolve("desktop.lock"))
            Files.deleteIfExists(directory)
        }
    }
}
