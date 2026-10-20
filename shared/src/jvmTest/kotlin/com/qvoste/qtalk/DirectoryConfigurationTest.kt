package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.model.GoogleSheetsContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.GoogleSheetsEmployeePresenceService
import com.qvoste.qtalk.ui.calls.model.EmployeeStatus
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class DirectoryConfigurationTest {
    @Test
    fun directoryPresenceConfigurationIsOptionalAndValidated() {
        val service = GoogleSheetsContactDatabaseService()
        val url = "https://script.google.com/macros/s/example-deployment/exec"
        assertEquals(url, service.parsePresenceEndpoint("\"key\",\"value\"\n\"presence_url\",\"$url\""))
        assertEquals("", service.parsePresenceEndpoint("\"name\",\"number\"\n\"Person\",\"101\""))
        assertFailsWith<IllegalArgumentException> {
            service.parsePresenceEndpoint("presence_url,https://evil.example/exec")
        }
        assertFailsWith<IllegalArgumentException> {
            service.parsePresenceEndpoint("presence_url,$url\npresence_url,$url")
        }
    }

    @Test
    fun presenceEndpointIsExplicitPersistentAndCanBeCleared() {
        val preferences = Preferences.userRoot().node("com/qvoste/qtalk/test/" + UUID.randomUUID())
        try {
            val service = GoogleSheetsEmployeePresenceService(null, preferences)
            assertFalse(service.enabled)
            assertEquals("", service.loadEndpoint())
            val url = "https://script.google.com/macros/s/example-deployment/exec"
            service.saveEndpoint("  $url  ")
            assertTrue(service.enabled)
            assertEquals(url, GoogleSheetsEmployeePresenceService(null, preferences).loadEndpoint())
            for (invalid in listOf(
                "http://script.google.com/macros/s/example/exec",
                "https://script.google.com.evil.example/macros/s/example/exec",
                "$url?key=secret",
                "https://script.google.com/macros/s/example/dev"
            )) {
                assertFailsWith<IllegalArgumentException> { service.saveEndpoint(invalid) }
                assertEquals(url, service.loadEndpoint())
            }
            service.saveEndpoint("")
            assertFalse(service.enabled)
            assertFalse(GoogleSheetsEmployeePresenceService(null, preferences).enabled)
        } finally {
            preferences.removeNode()
        }
    }

    @Test
    fun directoryStartsEmptyAndRetainsExplicitConfiguration() {
        val preferences = Preferences.userRoot().node("com/qvoste/qtalk/test/" + UUID.randomUUID())
        try {
            val service = GoogleSheetsContactDatabaseService(preferences)
            assertEquals("", service.loadSourceUrl())
            service.saveSourceUrl("  https://example.com/directory  ")
            assertEquals("https://example.com/directory", service.loadSourceUrl())
        } finally {
            preferences.removeNode()
        }
    }

    @Test
    fun unconfiguredPresenceIsDisabled() = runBlocking {
        val service = GoogleSheetsEmployeePresenceService("")
        assertFalse(service.enabled)
        assertEquals(emptyMap(), service.load())
        service.publish("101", EmployeeStatus.AVAILABLE)
    }
}
