package com.qvoste.qtalk.ui.calls.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.onPointerEvent

/** Keep left click for selecting a contact; only a secondary press starts a transfer. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.transferOnRightClick(enabled: Boolean, onTransfer: () -> Unit): Modifier =
    if (enabled) onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            event.changes.forEach { it.consume() }
            onTransfer()
        }
    } else this
