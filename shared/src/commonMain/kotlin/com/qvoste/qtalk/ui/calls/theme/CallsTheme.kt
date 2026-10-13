package com.qvoste.qtalk.ui.calls.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.logo_black

enum class ThemeMode { DARK, LIGHT }

private data class CallsPalette(
    val background: Color,
    val control: Color,
    val divider: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val secondary: Color,
    val danger: Color,
    val online: Color
)

private val darkPalette = CallsPalette(
    Color(0xFF090B0D), Color(0xFF101215), Color(0xFF23262D),
    Color(0xFF0088FF), Color.White, Color(0xFFE8E9EC), Color(0x80FFFFFF),
    Color(0xFFFF383C), Color(0xFF34C759)
)
private val lightPalette = CallsPalette(
    Color.White, Color(0xFFE9EAEC), Color(0xFFD2D5D9),
    Color(0xFF0088FF), Color.White, Color(0xFF101215), Color(0xFF505760),
    Color(0xFFFF383C), Color(0xFF34C759)
)
private val LocalCallsPalette = staticCompositionLocalOf { darkPalette }

@Composable
fun CallsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCallsPalette provides if (mode == ThemeMode.LIGHT) lightPalette else darkPalette) {
        content()
    }
}

internal val AppBackground: Color @Composable get() = LocalCallsPalette.current.background
internal val PanelBackground: Color @Composable get() = AppBackground
internal val ControlBackground: Color @Composable get() = LocalCallsPalette.current.control
internal val DividerColor: Color @Composable get() = LocalCallsPalette.current.divider
internal val Primary: Color @Composable get() = LocalCallsPalette.current.primary
internal val OnPrimary: Color @Composable get() = LocalCallsPalette.current.onPrimary
internal val TextPrimary: Color @Composable get() = LocalCallsPalette.current.text
internal val TextSecondary: Color @Composable get() = LocalCallsPalette.current.secondary
internal val Danger: Color @Composable get() = LocalCallsPalette.current.danger
internal val Online: Color @Composable get() = LocalCallsPalette.current.online
internal val IsLightTheme: Boolean @Composable get() = LocalCallsPalette.current === lightPalette

@Composable
fun qTalkWindowIcon(): Painter = painterResource(Res.drawable.logo_black)
