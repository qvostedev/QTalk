package com.qvoste.qtalk

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.qvoste.qtalk.ui.calls.components.*
import com.qvoste.qtalk.ui.calls.RingtoneDialog
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class Audio3UiTest {
    @Test fun ringtoneControlsStopOnSelectionSaveCancelAndDisposal() = runComposeUiTest {
        val shown = mutableStateOf(true)
        val playing = mutableStateOf<String?>(null)
        var stopped = 0
        var saved = ""
        setContent { CallsTheme(ThemeMode.DARK) {
            if (shown.value) RingtoneDialog(QTalkRingtones, "warm", { playing.value = it }, playing.value,
                { stopped++; playing.value = null }, { saved = it; shown.value = false }, { shown.value = false })
        } }
        onNodeWithText("Прослушать").performClick()
        runOnIdle { assertEquals("warm", playing.value) }
        onNodeWithText("Остановить").performClick()
        runOnIdle { assertNull(playing.value) }
        onNodeWithText("Прослушать").performClick()
        onNodeWithText("Вариант - 2").performClick()
        runOnIdle { assertNull(playing.value) }
        onNodeWithText("Прослушать").performClick()
        runOnIdle { assertEquals("round", playing.value) }
        onNodeWithText("Сохранить").performClick()
        runOnIdle { assertEquals("round", saved); assertNull(playing.value); shown.value = true }
        onNodeWithText("Прослушать").performClick()
        onNodeWithText("Отмена").performClick()
        runOnIdle { assertNull(playing.value); shown.value = true }
        onNodeWithText("Прослушать").performClick()
        runOnIdle { shown.value = false }
        runOnIdle { assertNull(playing.value); assertTrue(stopped >= 5) }
    }

    @Test fun clearOnlyEmptiesEditableUnknownDraft() = runComposeUiTest {
        val number = mutableStateOf("555123")
        val call = mutableStateOf(CallStatus())
        val contact = mutableStateOf<Contact?>(null)
        var changes = 0
        var unrelated = 0
        var edits = 0
        var deletes = 0
        setContent { CallsTheme(ThemeMode.DARK) {
            DialerPanel(number.value, contact.value, null, false, "101", RegistrationState.DISCONNECTED,
                call.value, null, false, emptyList(), emptyList(), { number.value = it; changes++ },
                { edits++ }, { unrelated++ }, { unrelated++ }, { unrelated++ }, { deletes++ },
                { unrelated++ }, { _, _ -> unrelated++ }, { unrelated++ }, {}, { unrelated++ },
                { unrelated++ }, { unrelated++ })
        } }
        onNodeWithText("Очистить").performScrollTo().assertIsEnabled().performClick()
        runOnIdle { assertEquals("", number.value); assertEquals(1, changes); assertEquals(0, unrelated + edits + deletes) }
        onNodeWithText("Очистить").assertIsNotEnabled()
        for (state in CallState.entries.filter { it.isInProgress }) {
            runOnIdle { number.value = "555999"; call.value = CallStatus(state, "555999") }
            onNodeWithText("Очистить").assertIsNotEnabled()
        }
        runOnIdle { call.value = CallStatus(); contact.value = Contact("Saved", "555999", false) }
        onNodeWithText("Очистить").assertDoesNotExist()
        onNodeWithText("Изменить").performClick()
        onNodeWithText("Удалить").performClick()
        runOnIdle { assertEquals(1, edits); assertEquals(1, deletes); assertEquals(0, unrelated); assertEquals(1, changes) }
    }

    @Test fun ordinaryAndConferenceMicRenderedBoundsStayCompactWhenMuted() = runComposeUiTest {
        val conference = mutableStateOf(ConferenceStatus())
        setContent { CallsTheme(ThemeMode.DARK) {
            ActiveCallOverlay(conference.value, null, "101", { null }, {}, {}, {}, {}, "102", null, "", "", 0L,
                {}, {}, {}, { conference.value = conference.value.copy(microphoneMuted = it) }, {}, {})
        } }
        fun checkMic() {
            onNodeWithContentDescription("Микрофон", useUnmergedTree = true)
                .assertWidthIsEqualTo(13.dp).assertHeightIsEqualTo(20.dp)
            onNodeWithContentDescription("Микрофон").assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
        }
        checkMic()
        onNodeWithContentDescription("Микрофон").performClick()
        checkMic()
        runOnIdle { conference.value = ConferenceStatus(ConferencePhase.ACTIVE, participants = listOf("102")) }
        checkMic()
        onNodeWithContentDescription("Микрофон").performClick()
        checkMic()
    }
}
