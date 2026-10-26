package com.qvoste.qtalk.ui.calls.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Reveal the new palette beneath a snapshot, keeping a single live screen and its state. */
@Composable
internal fun CircularThemeReveal(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    content: @Composable ((ThemeMode) -> Unit) -> Unit
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var previousFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var changing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }) {
            content { selected ->
                if (selected != mode && !changing) {
                    changing = true
                    scope.launch {
                        try {
                            progress.snapTo(0f)
                            previousFrame = layer.toImageBitmap()
                            onModeChange(selected)
                            // Allow the new palette to reach the live layer before revealing it.
                            withFrameNanos { }
                            progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                        } finally {
                            previousFrame = null
                            changing = false
                        }
                    }
                }
            }
        }
        previousFrame?.let { frame ->
            Canvas(Modifier.matchParentSize()) {
                val radius = hypot(size.width, size.height) / 2f * progress.value
                val reveal = Path().apply {
                    addOval(Rect(center.x - radius, center.y - radius,
                        center.x + radius, center.y + radius))
                }
                clipPath(reveal, ClipOp.Difference) {
                    drawImage(frame, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                }
            }
        }
    }
}
