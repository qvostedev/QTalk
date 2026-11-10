package com.qvoste.qtalk.support

import com.qvoste.qtalk.ui.calls.i18n.AppLanguage
import com.qvoste.qtalk.ui.calls.i18n.translateUiText
import kotlin.test.*

class SupportDraftTest {
    @Test fun requiresTrimmedLengthsForBothKinds() {
        assertFalse(SupportDraft(" ab ", " description ").canSend)
        assertTrue(SupportDraft(" abc ", " description ").canSend)
        assertTrue(SupportDraft(kind = SupportKind.IMPROVEMENT, title = " abc ", description = " description ").canSend)
        assertFalse(SupportDraft("abc", "123456789").canSend)
        assertFalse(SupportDraft("a".repeat(161), "d".repeat(10)).canSend)
        assertFalse(SupportDraft("abc", "d".repeat(12001)).canSend)
    }
    @Test fun everySupportMessageHasEnglishTranslation() {
        for (source in supportUiStrings + SupportError.entries.map { it.text }) {
            assertNotEquals(source, translateUiText(source, AppLanguage.ENGLISH), source)
            assertEquals(source, translateUiText(source, AppLanguage.RUSSIAN))
        }
    }
    @Test fun unsupportedNeverPretendsSuccess() {
        assertEquals(SupportError.UNSUPPORTED, UnsupportedSupportService.error)
    }
}
