package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.voip.*
import kotlin.test.*

class ContactSaveSelectionTest {
    @Test fun savedContactForActiveUnknownNumberBecomesVisibleWithoutChangingDialField() {
        val saved = Contact("Saved", "555123", false, source = ContactSource.LOCAL)
        val selection = contactSelectionAfterSave(saved, emptyList(), "old dial field", CallStatus(CallState.ACTIVE, "555123"))
        assertEquals(saved, selection.contact)
        assertEquals("old dial field", selection.dialNumber)
    }
    @Test fun existingContactEditKeepsUpdatedDetailsDuringItsCall() {
        val previous = Contact("Old name", "555123", false)
        val saved = previous.copy(name = "New name")
        assertEquals(saved, contactSelectionAfterSave(saved, listOf(previous), "555123",
            CallStatus(CallState.ACTIVE, "555123")).contact)
    }
    @Test fun staleDraftCannotSelectItsContactOverADifferentActiveCall() {
        val saved = Contact("Draft", "555123", false)
        val active = Contact("Current caller", "777777", false)
        val result = contactSelectionAfterSave(saved, listOf(active), "777777", CallStatus(CallState.ACTIVE, "777777"))
        assertEquals(active, result.contact)
        assertEquals("777777", result.dialNumber)
        assertNull(contactSelectionAfterSave(saved, emptyList(), "777777", CallStatus(CallState.ACTIVE, "777777")).contact)
    }
    @Test fun idleSaveSelectsContactAndUpdatesDialField() {
        val saved = Contact("Saved", "555123", false)
        assertEquals(ContactSaveSelection(saved, "555123"), contactSelectionAfterSave(saved, emptyList(), "", CallStatus()))
    }
}
