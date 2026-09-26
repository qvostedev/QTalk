package com.qvoste.qtalk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.qvoste.qtalk.ui.calls.theme.qTalkWindowIcon
import com.qvoste.qtalk.app.App
import com.qvoste.qtalk.voip.LinphoneVoipEngine
import com.qvoste.qtalk.voip.PreferencesSipAccountStore
import kotlinx.coroutines.launch
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

private const val SNAP_EDGE_PX = 18

private fun workArea(window: java.awt.Window): Rectangle {
    val configuration = window.graphicsConfiguration
    val screen = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return Rectangle(
        screen.x + insets.left,
        screen.y + insets.top,
        screen.width - insets.left - insets.right,
        screen.height - insets.top - insets.bottom
    )
}

fun main() = application {
    val voipEngine = remember { LinphoneVoipEngine() }
    val accountStore = remember { PreferencesSipAccountStore() }
    val scope = rememberCoroutineScope()
    val windowState = rememberWindowState(width = 1440.dp, height = 1024.dp)
    var maximized by remember { mutableStateOf(false) }
    var snapped by remember { mutableStateOf(false) }
    var restoredBounds by remember { mutableStateOf<Rectangle?>(null) }
    var dragPointerOrigin by remember { mutableStateOf<Point?>(null) }
    var dragWindowOrigin by remember { mutableStateOf<Point?>(null) }

    fun closeApplication() {
        scope.launch {
            try { voipEngine.stop() } finally { exitApplication() }
        }
    }

    Window(
        onCloseRequest = ::closeApplication,
        state = windowState,
        title = "QTalk",
        icon = qTalkWindowIcon(),
        // Системную панель заменяет шапка в стиле приложения.
        undecorated = true,
        resizable = true
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D10))) {
            App(voipEngine, accountStore)

            // Узкая пустая область позволяет перетаскивать окно мышью.
            Box(
                Modifier.fillMaxWidth().height(32.dp).align(Alignment.TopCenter)
                    .pointerInput(window) {
                        detectDragGestures(
                            onDragStart = {
                                if (!maximized) {
                                    // Запоминаем обычный размер до прилипания к краю.
                                    if (!snapped) restoredBounds = window.bounds
                                    dragPointerOrigin = MouseInfo.getPointerInfo()?.location
                                    dragWindowOrigin = window.location
                                }
                            },
                            onDragEnd = {
                                val pointer = MouseInfo.getPointerInfo()?.location
                                if (!maximized && pointer != null) {
                                    val area = workArea(window)
                                    val halfWidth = area.width / 2
                                    when {
                                        pointer.y <= area.y + SNAP_EDGE_PX -> {
                                            window.bounds = area
                                            maximized = true
                                            snapped = false
                                        }
                                        pointer.x <= area.x + SNAP_EDGE_PX -> {
                                            window.bounds = Rectangle(area.x, area.y, halfWidth, area.height)
                                            snapped = true
                                        }
                                        pointer.x >= area.x + area.width - SNAP_EDGE_PX -> {
                                            window.bounds = Rectangle(area.x + halfWidth, area.y, area.width - halfWidth, area.height)
                                            snapped = true
                                        }
                                        else -> snapped = false
                                    }
                                }
                                dragPointerOrigin = null
                                dragWindowOrigin = null
                            },
                            onDragCancel = {
                                dragPointerOrigin = null
                                dragWindowOrigin = null
                            }
                        ) { change, _ ->
                            change.consume()
                            val pointerStart = dragPointerOrigin
                            val windowStart = dragWindowOrigin
                            val pointerNow = MouseInfo.getPointerInfo()?.location
                            if (!maximized && pointerStart != null && windowStart != null && pointerNow != null) {
                                // Абсолютные координаты не накапливают дрожание движущегося окна.
                                window.setLocation(
                                    windowStart.x + pointerNow.x - pointerStart.x,
                                    windowStart.y + pointerNow.y - pointerStart.y
                                )
                            }
                        }
                    }
            )

            WindowControls(
                maximized = maximized,
                onMinimize = { window.isMinimized = true },
                onToggleMaximize = {
                    if (maximized) {
                        restoredBounds?.let(window::setBounds)
                        maximized = false
                        snapped = false
                    } else {
                        restoredBounds = window.bounds
                        window.bounds = workArea(window)
                        maximized = true
                        snapped = false
                    }
                },
                onClose = ::closeApplication,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 6.dp)
            )
        }
    }
}

private enum class WindowActionIcon { MINIMIZE, MAXIMIZE, RESTORE, CLOSE }

@androidx.compose.runtime.Composable
private fun WindowControls(
    maximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WindowAction(WindowActionIcon.MINIMIZE, "Свернуть", onMinimize)
        WindowAction(
            if (maximized) WindowActionIcon.RESTORE else WindowActionIcon.MAXIMIZE,
            if (maximized) "Восстановить" else "Развернуть",
            onToggleMaximize
        )
        WindowAction(WindowActionIcon.CLOSE, "Закрыть", onClose)
    }
}

@androidx.compose.runtime.Composable
private fun WindowAction(icon: WindowActionIcon, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(20.dp).semantics { contentDescription = description }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(12.dp)) {
            val lineColor = Color(0x80FFFFFF)
            val stroke = 1.5.dp.toPx()
            when (icon) {
                WindowActionIcon.MINIMIZE -> drawLine(
                    lineColor,
                    Offset(1.dp.toPx(), 9.dp.toPx()),
                    Offset(11.dp.toPx(), 9.dp.toPx()),
                    stroke,
                    StrokeCap.Round
                )
                WindowActionIcon.MAXIMIZE -> drawRect(
                    lineColor,
                    Offset(2.dp.toPx(), 2.dp.toPx()),
                    size.copy(width = 8.dp.toPx(), height = 8.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                )
                WindowActionIcon.RESTORE -> {
                    drawRect(
                        lineColor,
                        Offset(1.dp.toPx(), 3.dp.toPx()),
                        size.copy(width = 7.dp.toPx(), height = 7.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                    )
                    drawLine(lineColor, Offset(4.dp.toPx(), 1.dp.toPx()), Offset(11.dp.toPx(), 1.dp.toPx()), stroke)
                    drawLine(lineColor, Offset(11.dp.toPx(), 1.dp.toPx()), Offset(11.dp.toPx(), 8.dp.toPx()), stroke)
                }
                WindowActionIcon.CLOSE -> {
                    drawLine(lineColor, Offset(2.dp.toPx(), 2.dp.toPx()), Offset(10.dp.toPx(), 10.dp.toPx()), stroke, StrokeCap.Round)
                    drawLine(lineColor, Offset(10.dp.toPx(), 2.dp.toPx()), Offset(2.dp.toPx(), 10.dp.toPx()), stroke, StrokeCap.Round)
                }
            }
        }
    }
}
