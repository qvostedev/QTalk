package com.qvoste.qtalk.ui.calls.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.onSecondaryClick(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (enabled) onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            event.changes.forEach { it.consume() }
            onClick()
        }
    } else this
