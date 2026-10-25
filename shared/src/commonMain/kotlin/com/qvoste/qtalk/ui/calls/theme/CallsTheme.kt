package com.qvoste.qtalk.ui.calls.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.logo_black
import qtalk.shared.generated.resources.logo_tray
import com.qvoste.qtalk.ui.calls.model.EmployeeStatus

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
    val online: Color,
    val busy: Color
)

private val darkPalette = CallsPalette(
    Color(0xFF090B0D), Color(0xFF101215), Color(0xFF23262D),
    Color(0xFF0088FF), Color.White, Color(0xFFE8E9EC), Color(0x80FFFFFF),
    Color(0xFFFF383C), Color(0xFF34C759), Color(0xFFFFC247)
)
private val lightPalette = CallsPalette(
    Color.White, Color(0xFFE9EAEC), Color(0xFFD2D5D9),
    Color(0xFF0088FF), Color.White, Color(0xFF101215), Color(0xFF505760),
    Color(0xFFFF383C), Color(0xFF34C759), Color(0xFFFFB020)
)
private val LocalCallsPalette = staticCompositionLocalOf { darkPalette }
internal val LocalCallLightControls = staticCompositionLocalOf { false }

private object PlainIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 1
}

@Composable
fun CallsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCallsPalette provides if (mode == ThemeMode.LIGHT) lightPalette else darkPalette,
        LocalIndication provides PlainIndication) {
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
internal val Busy: Color @Composable get() = LocalCallsPalette.current.busy
internal val IsLightTheme: Boolean @Composable get() = LocalCallsPalette.current === lightPalette

internal fun Color.onHover(hovered: Boolean): Color = if (!hovered) this else
    copy(red = red * 0.7f, green = green * 0.7f, blue = blue * 0.7f)

internal val EmployeeStatus.color: Color
    @Composable get() = when (this) {
        EmployeeStatus.AVAILABLE -> Online
        EmployeeStatus.BUSY -> Busy
        EmployeeStatus.OFFLINE -> TextSecondary
    }

@Composable
fun qTalkWindowIcon(): Painter = painterResource(Res.drawable.logo_black)

@Composable
fun qTalkTrayIcon(): Painter = painterResource(Res.drawable.logo_tray)
