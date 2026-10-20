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

class DirectoryConfigurationTest {
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
