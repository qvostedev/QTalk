package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.i18n.AppLanguage
import com.qvoste.qtalk.ui.calls.i18n.releaseNotesForLanguage
import com.qvoste.qtalk.ui.calls.i18n.translateUiText
import com.qvoste.qtalk.ui.calls.theme.PreferencesThemeStore
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageTest {
    @Test
    fun selectsReleaseSectionWithoutMixingLanguages() {
        val notes = "## English\n\nEnglish notes\n### Fixes\n- A\n\n## Русский\n\nРусское описание\n### Исправления\n- Б"
        assertEquals("English notes\n### Fixes\n- A", releaseNotesForLanguage(notes, AppLanguage.ENGLISH))
        assertEquals("Русское описание\n### Исправления\n- Б", releaseNotesForLanguage(notes, AppLanguage.RUSSIAN))
        assertFalse(releaseNotesForLanguage(notes, AppLanguage.ENGLISH).contains("Русское"))
        val reverseOrder = "## Русский\r\n\r\nРусское описание\r\n\r\n## English\r\n\r\nEnglish notes"
        assertEquals("Русское описание", releaseNotesForLanguage(reverseOrder, AppLanguage.RUSSIAN))
        assertEquals("English notes", releaseNotesForLanguage(reverseOrder, AppLanguage.ENGLISH))
    }

    @Test
    fun handlesOlderReleasesAndMissingTranslations() {
        assertEquals("Changes: fixed calls", releaseNotesForLanguage("Changes: fixed calls", AppLanguage.ENGLISH))
        assertTrue(releaseNotesForLanguage("Changes: fixed calls", AppLanguage.RUSSIAN).contains("на русском"))
        assertEquals("Исправления", releaseNotesForLanguage("Исправления", AppLanguage.RUSSIAN))
        assertEquals("", releaseNotesForLanguage("", AppLanguage.RUSSIAN))
    }

    @Test
    fun translatesOnlyKnownInterfaceMessages() {
        assertEquals("Settings", translateUiText("Настройки", AppLanguage.ENGLISH))
        assertEquals("Настройки", translateUiText("Настройки", AppLanguage.RUSSIAN))
        assertEquals("Contacts loaded: 44", translateUiText("Загружено контактов: 44", AppLanguage.ENGLISH))
        assertEquals("Missed  3", translateUiText("Пропущенные  3", AppLanguage.ENGLISH))
        assertEquals("12 min 4 sec", translateUiText("12 мин 4 сек", AppLanguage.ENGLISH))
        assertEquals("София Иванова", translateUiText("София Иванова", AppLanguage.ENGLISH))
        assertEquals("ru", AppLanguage.fromCode("unknown").code)
    }

    @Test
    fun savesLanguageIndependentlyOfAppearance() {
        val preferences = Preferences.userRoot().node("com/qvoste/qtalk/test/" + UUID.randomUUID())
        try {
            val store = PreferencesThemeStore(preferences)
            assertEquals(AppLanguage.RUSSIAN, store.loadLanguage())
            store.saveLanguage(AppLanguage.ENGLISH)
            assertEquals(AppLanguage.ENGLISH, PreferencesThemeStore(preferences).loadLanguage())
            store.saveLanguage(AppLanguage.RUSSIAN)
            assertEquals(AppLanguage.RUSSIAN, store.loadLanguage())
        } finally {
            preferences.removeNode()
        }
    }
}
