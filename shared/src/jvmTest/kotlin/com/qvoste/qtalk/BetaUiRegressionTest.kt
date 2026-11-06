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
