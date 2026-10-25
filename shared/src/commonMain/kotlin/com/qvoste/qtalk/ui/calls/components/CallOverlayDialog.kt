package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

internal val CallOverlayTint = Color(0xD9090B0D)
internal val CallOverlayBlur = 8.dp
val LocalCallBackdropVisibility = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** Uses the call backdrop, without the additional black scrim of a platform dialog. */
@Composable
internal fun CallOverlayDialog(
    onDismissRequest: () -> Unit,
    containerColor: Color,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    val visibility = LocalCallBackdropVisibility.current
    DisposableEffect(visibility) { visibility(1); onDispose { visibility(-1) } }
    Box(Modifier.fillMaxSize().background(CallOverlayTint).clickable(onClick = onDismissRequest)) {
        Popup(alignment = Alignment.Center, onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp), color = containerColor) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    title()
                    text()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                        confirmButton()
                    }
                }
            }
        }
    }
}
