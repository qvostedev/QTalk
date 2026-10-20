package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.components.AvatarDiskCache
import com.qvoste.qtalk.ui.calls.model.GoogleSheetsContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.PreferencesContactStore
import com.qvoste.qtalk.ui.calls.model.HistoryItem
import com.qvoste.qtalk.ui.calls.model.PreferencesCallHistoryStore
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AvatarCacheTest {
    @Test
    fun expiredImageSurvivesOfflineRefreshAndRemainsOnDisk() {
        val directory = Files.createTempDirectory("qtalk-avatar-test")
        val file = directory.resolve("avatar.img")
        try {
            Files.write(file, byteArrayOf(1, 2, 3))
            Files.setLastModifiedTime(file, FileTime.from(Instant.EPOCH))
            val cache = AvatarDiskCache(file, UUID.randomUUID().toString()) { error("offline") }
            assertContentEquals(byteArrayOf(1, 2, 3), cache.load())
            assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(file))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectedResponseCannotOverwriteLastGoodImage() {
        val directory = Files.createTempDirectory("qtalk-avatar-test")
        val file = directory.resolve("avatar.img")
        try {
            Files.write(file, byteArrayOf(1, 2, 3))
            Files.setLastModifiedTime(file, FileTime.from(Instant.EPOCH))
            val cache = AvatarDiskCache(file, UUID.randomUUID().toString(),
                validate = { require(it.first() == 1.toByte()) }) { byteArrayOf(9) }
            assertContentEquals(byteArrayOf(1, 2, 3), cache.load())
            assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(file))
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun freshImageDoesNotTriggerNetworkRequest() {
        val directory = Files.createTempDirectory("qtalk-avatar-test")
        val file = directory.resolve("avatar.img")
        try {
            Files.write(file, byteArrayOf(1))
            var downloads = 0
            val cache = AvatarDiskCache(file, UUID.randomUUID().toString()) {
                downloads++
                byteArrayOf(2)
            }
            assertContentEquals(byteArrayOf(1), cache.load())
            assertEquals(0, downloads)
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun sheetAvatarUrlsAreOptionalValidatedAndPersisted() {
        val node = Preferences.userRoot().node("com/qvoste/qtalk/test/" + UUID.randomUUID())
        try {
            val parser = GoogleSheetsContactDatabaseService(node)
            val contacts = parser.parseContacts(
                "Ф.И.О.,Внутренний,Аватар\n" +
                    "Первый,101,https://drive.google.com/uc?export=download&id=example\n" +
                    "Второй,102,file:///private\n" +
                    "Третий,103,"
            )
            assertEquals("https://drive.google.com/uc?export=download&id=example", contacts[0].avatarUrl)
            assertEquals("", contacts[1].avatarUrl)
            assertEquals("", contacts[2].avatarUrl)
            val store = PreferencesContactStore(node)
            store.save(contacts)
            assertEquals(contacts, store.load())
            val legacy = parser.parseContacts("Ф.И.О.,Внутренний\nСтарый,104").single()
            assertEquals("", legacy.avatarUrl)
            val call = HistoryItem("test", "Первый", "101", "Входящий", 1,
                avatarUrl = contacts[0].avatarUrl)
            val historyStore = PreferencesCallHistoryStore(node.node("history"))
            historyStore.save(listOf(call))
            assertEquals(call, historyStore.load().single())
        } finally {
            node.removeNode()
        }
    }
}
