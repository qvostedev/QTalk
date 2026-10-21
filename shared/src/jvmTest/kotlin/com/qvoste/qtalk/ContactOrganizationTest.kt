package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.model.ContactSource
import com.qvoste.qtalk.ui.calls.model.classifyForAccount
import com.qvoste.qtalk.ui.calls.model.GoogleSheetsContactDatabaseService
import java.util.UUID
import java.util.prefs.Preferences
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
        for (account in listOf("", "999")) {
            assertTrue(directory.classifyForAccount(account).none { it.isEmployee })
        }
        assertEquals(listOf(false, false, false, true), directory.classifyForAccount("219").map { it.isEmployee })
    }

    @Test fun reclassificationDiscardsPreviousAccountFlags() {
        val previous = directory.classifyForAccount("216")
        assertEquals(listOf(false, false, true, false), previous.classifyForAccount("218").map { it.isEmployee })
        assertEquals(listOf(true, false, false, false), previous.map { it.copy(company = "") }.classifyForAccount("216").map { it.isEmployee })
    }

    @Test fun directoryProfileTakesPrecedenceOverLocalContactWithSameNumber() {
        val contacts = listOf(directory[0].copy(company = "Other", source = ContactSource.LOCAL)) +
            directory.map { it.copy(source = ContactSource.DIRECTORY) }
        assertEquals(listOf(true, true, true, false, false), contacts.classifyForAccount("216").map { it.isEmployee })
    }

    @Test fun branchAddressesDoNotSplitOneOrganization() {
        val contacts = listOf(
            Contact("Self", "216", false, company = "Example Молокова, 68"),
            Contact("Colleague", "217", false, company = "EXAMPLE Крылова, 1"),
            Contact("Customer", "218", false, company = "Other Молокова, 68"),
            Contact("Similar name", "219", false, company = "Example Services")
        )
        assertEquals(listOf(true, true, false, false), contacts.classifyForAccount("216").map { it.isEmployee })
    }

    @Test fun legacyOfficeColumnCanContainOrganizationAndBranchTogether() {
        val contacts = listOf(
            Contact("Self", "216", false, office = "Example Молокова, 68"),
            Contact("Same branch", "220", false, office = "Example Молокова, 68"),
            Contact("Other branch", "217", false, office = "EXAMPLE Крылова, 1"),
            Contact("Explicit company", "218", false, company = "Example", office = "Крылова, 1"),
            Contact("Different company", "219", false, office = "Another Молокова, 68"),
            Contact("Office only", "221", false, office = "Молокова, 68"),
            Contact("No organization", "222", false, office = "Main office")
        )
        assertEquals(listOf(true, true, true, true, false, false, false),
            contacts.classifyForAccount("216").map { it.isEmployee })
        assertEquals(contacts.classifyForAccount("216").map { it.isEmployee },
            contacts.classifyForAccount("218").map { it.isEmployee })
    }

    @Test fun importedOfficeOnlyDirectoryClassifiesAllBranches() {
        val preferences = Preferences.userRoot().node("qtalk-test/organization-${UUID.randomUUID()}")
        try {
            val csv = """
                Ф.И.О.,Отдел,Должность,Офис,Внутренний
                Test User,Development,Engineer,"Example Молокова, 68",216
                Test Colleague,Sales,Manager,"Example Крылова, 1",220
                Test Customer,,,"Other Молокова, 68",221
            """.trimIndent()
            val contacts = GoogleSheetsContactDatabaseService(preferences).parseContacts(csv)
            assertTrue(contacts.all { it.company.isBlank() })
            assertEquals(listOf(true, true, false), contacts.classifyForAccount("216").map { it.isEmployee })
        } finally { preferences.removeNode() }
    }
}
