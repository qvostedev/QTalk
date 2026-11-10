package com.qvoste.qtalk

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.qvoste.qtalk.ui.calls.theme.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class MaterialHoverThemeTest {
    @Test fun materialControlsDoNotDrawHoverOverlayInEitherTheme() {
        for (mode in ThemeMode.entries) runComposeUiTest {
            val interactions = MutableInteractionSource()
            var clicks = 0
            setContent { CallsTheme(mode) {
                Column(Modifier.background(AppBackground).padding(16.dp).testTag("controls")) {
                    TextButton(onClick = { clicks++ }, interactionSource = interactions,
                        modifier = Modifier.testTag("back")) { Text("Назад к настройкам") }
                    OutlinedButton(onClick = {}, interactionSource = interactions) { Text("Сохранить диагностику ZIP") }
                    Checkbox(checked = true, onCheckedChange = {}, interactionSource = interactions)
                }
            } }
            mainClock.advanceTimeBy(500)
            val before = onNodeWithTag("controls").captureToImage().toPixelMap()
            runOnIdle { assertTrue(interactions.tryEmit(HoverInteraction.Enter())) }
            mainClock.advanceTimeBy(500)
            waitForIdle()
            val after = onNodeWithTag("controls").captureToImage().toPixelMap()
            var changed = 0
            for (y in 0 until before.height) for (x in 0 until before.width) {
                if (before[x, y] != after[x, y]) changed++
            }
            assertEquals(0, changed, "Material hover must not add a state-layer background in $mode")
            onNodeWithTag("back").performClick()
            runOnIdle { assertEquals(1, clicks) }
        }
    }
}
