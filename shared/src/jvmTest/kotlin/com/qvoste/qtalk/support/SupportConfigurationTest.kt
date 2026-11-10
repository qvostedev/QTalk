package com.qvoste.qtalk.support

import kotlin.test.*

class SupportConfigurationTest {
    @Test fun regularLaunchUsesPublicReceiverWithoutAnyCredentials() {
        assertEquals("https://qtalk-support.malyshevon-tg-2026-a7f3.workers.dev/v1/reports", SupportConfiguration.endpoint(null))
        assertEquals("https", supportEndpoint(SupportConfiguration.endpoint(null), false).scheme)
    }
    @Test fun explicitEndpointOverridesDefaultAndBlankDisablesSending() {
        assertEquals("https://support.example.org/v1/reports", SupportConfiguration.endpoint("https://support.example.org/v1/reports"))
        assertEquals("", SupportConfiguration.endpoint(""))
        assertFailsWith<SupportFailure> { supportEndpoint(SupportConfiguration.endpoint(""), false) }
    }
}
