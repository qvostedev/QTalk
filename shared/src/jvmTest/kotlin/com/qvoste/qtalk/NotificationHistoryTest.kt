package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.model.*
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*

class NotificationHistoryTest {
    @Test fun persistsDescriptionsDatesReadAndDeletedVersionsWithoutDuplicates() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/${UUID.randomUUID()}")
        try {
            val store = PreferencesNotificationHistoryStore(node)
            val original = emptyList<SavedNotification>().withUpdate("2.0.0", "Описание релиза\n".repeat(300), 1234)
            store.save(original.map { it.copy(read = true) })
            val loaded = PreferencesNotificationHistoryStore(node).load()
            assertEquals(original.single().description, loaded.single().description)
            assertEquals(1234, loaded.single().timestamp)
            assertTrue(loaded.single().read)
            assertEquals(1, loaded.withUpdate("2.0.0", "New description", 9999).size)
            val deleted = loaded.map { it.copy(deleted = true, description = "", title = "", version = "") }
            store.save(deleted)
            val checkedAgain = PreferencesNotificationHistoryStore(node).load().withUpdate("2.0.0", "New description", 9999)
            assertTrue(checkedAgain.single().deleted)
            assertEquals("", checkedAgain.single().description)
        } finally { node.removeNode() }
    }
}
