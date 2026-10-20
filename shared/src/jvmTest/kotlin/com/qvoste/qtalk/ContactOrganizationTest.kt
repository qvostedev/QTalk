package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.model.ContactSource
import com.qvoste.qtalk.ui.calls.model.classifyForAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactOrganizationTest {
    private val directory = listOf(
        Contact("User", "216", false, company = "Example Company"),
        Contact("Colleague", "217", false, company = "  example   COMPANY  "),
        Contact("Other company", "218", false, company = "Another company"),
        Contact("No company", "219", false)
    )

    @Test fun classifiesRelativeToSignedInUser() {
        assertEquals(listOf(true, true, false, false), directory.classifyForAccount(" 216 ").map { it.isEmployee })
        assertEquals(listOf(false, false, true, false), directory.classifyForAccount("218").map { it.isEmployee })
    }

    @Test fun missingUserOrOrganizationDoesNotClassifyUnknownContactsAsColleagues() {
        for (account in listOf("", "999", "219")) {
            assertTrue(directory.classifyForAccount(account).none { it.isEmployee })
        }
    }

    @Test fun reclassificationDiscardsPreviousAccountFlags() {
        val previous = directory.classifyForAccount("216")
        assertEquals(listOf(false, false, true, false), previous.classifyForAccount("218").map { it.isEmployee })
        assertTrue(previous.map { it.copy(company = "") }.classifyForAccount("216").none { it.isEmployee })
    }

    @Test fun directoryProfileTakesPrecedenceOverLocalContactWithSameNumber() {
        val contacts = listOf(directory[0].copy(company = "Other", source = ContactSource.LOCAL)) +
            directory.map { it.copy(source = ContactSource.DIRECTORY) }
        assertEquals(listOf(false, true, true, false, false), contacts.classifyForAccount("216").map { it.isEmployee })
    }
}
