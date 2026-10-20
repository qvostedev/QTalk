package com.qvoste.qtalk.ui.calls.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Density

data class InterfaceScale(val value: Float = 0f, val onChange: (Float) -> Unit = {}) {
    val label: String get() = if (value == 0f) "Автоматически" else "${(value * 100).toInt()}%"
}

val LocalInterfaceScale = compositionLocalOf { InterfaceScale() }
val LocalUnscaledDensity = compositionLocalOf<Density?> { null }
