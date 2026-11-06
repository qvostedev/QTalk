package com.qvoste.qtalk

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.qvoste.qtalk.ui.calls.components.*
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class BetaUiRegressionTest {
    @Test fun unknownNumberKeepsInformationAndOffersCreationWithoutSaving() = runComposeUiTest {
        var saved: Contact? = null
        var edited: Contact? = null
        setContent { CallsTheme(ThemeMode.DARK) {
            DialerPanel("555123", null, null, false, "101", RegistrationState.DISCONNECTED,
                CallStatus(), null, false, emptyList(), emptyList(), {}, { edited = it }, {}, {},
                { saved = it }, null, {}, { _, _ -> }, {}, {}, {}, {}, {})
        } }
        onNodeWithText("Должность").assertExists()
        onNodeWithText("Отдел").assertExists()
        onNodeWithText("Компания").assertExists()
        onNodeWithText("Telegram").assertExists()
        onNodeWithText("Добавить контакт").performScrollTo().performClick()
        runOnIdle { assertEquals("555123", edited?.number) }
        assertNull(saved)
        onNodeWithText("Заметки").performClick()
        onNodeWithText("Выберите контакт или наберите номер").assertExists()
        onNodeWithText("Добавить").assertDoesNotExist()
    }

    @Test fun contactCreationSnapshotsNumberAndDoesNotResetTypedDraftOnCallChange() = runComposeUiTest {
        val dialed = mutableStateOf("555123")
        val call = mutableStateOf(CallStatus())
        var saved: Contact? = null
        setContent { CallsTheme(ThemeMode.DARK) {
            DialerPanel(dialed.value, null, null, true, "101", RegistrationState.DISCONNECTED,
                call.value, null, false, emptyList(), emptyList(), {}, {}, {}, {},
                { saved = it }, null, {}, { _, _ -> }, {}, {}, {}, {}, {})
        } }
        onAllNodesWithText("555123").assertCountEquals(2)
        // The editable draft and the lower dial field both contain the initial number.
        onAllNodes(hasSetTextAction() and hasText("555123"))[0].performTextReplacement("555999")
        onAllNodes(hasSetTextAction())[0].performTextReplacement("Test contact")
        runOnIdle { dialed.value = "444444"; call.value = CallStatus(CallState.ACTIVE, "777777") }
        onNodeWithText("555999").assertExists()
        onNodeWithText("Сохранить").performScrollTo().performClick()
        runOnIdle { assertEquals("555999", saved?.number) }
    }

    @Test fun onlyCreatorSeesJoinedParticipantRemovalAndConfirmationCallsCallback() = runComposeUiTest {
        val state = mutableStateOf(ConferenceStatus(ConferencePhase.ACTIVE,
            participants = listOf("102", "103"), pendingNumbers = listOf("104"),
            hostNumber = "101", roomId = "room", originalCreatorNumber = "101"))
        var removed = ""
        setContent { CallsTheme(ThemeMode.DARK) {
            ConferenceCallOverlay(state.value, "101", { null }, 0, null,
                {}, {}, { removed = it }, {}, {}, {}, {})
        } }
        onAllNodesWithText("⋮").assertCountEquals(2)
        onAllNodesWithText("⋮")[0].performClick()
        onNodeWithText("Удалить").performClick()
        assertEquals("", removed)
        onNodeWithText("Удалить").performClick()
        assertEquals("102", removed)
        runOnIdle { state.value = state.value.copy(hostNumber = "102", originalCreatorNumber = "103") }
        onAllNodesWithText("⋮").assertCountEquals(0)
    }
}
