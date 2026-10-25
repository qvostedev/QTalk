package com.qvoste.qtalk.ui.calls.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.qvoste.qtalk.ui.calls.theme.ControlBackground

val LocalModalVisibility = staticCompositionLocalOf<(Int) -> Unit> { {} }

@Composable
internal fun ModalPresence() {
    val callback = LocalModalVisibility.current
    DisposableEffect(callback) { callback(1); onDispose { callback(-1) } }
}

@Composable
internal fun QTalkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = ControlBackground
) {
    ModalPresence()
    androidx.compose.material3.AlertDialog(onDismissRequest, confirmButton, modifier,
        dismissButton, icon, title, text, containerColor = containerColor)
}
