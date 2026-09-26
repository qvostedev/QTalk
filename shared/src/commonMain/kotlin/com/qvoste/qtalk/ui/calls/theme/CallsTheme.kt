package com.qvoste.qtalk.ui.calls.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.logo_black

internal val AppBackground = Color(0xFF0B0D10)
internal val PanelBackground = AppBackground
internal val ControlBackground = Color(0xFF191C20)
internal val DividerColor = Color(0xFF23262D)
internal val Primary = Color(0xFF4B6DC4)
internal val TextPrimary = Color(0xFFE8E9EC)
internal val TextSecondary = Color(0x80FFFFFF)
internal val Danger = Color(0xFF971818)
internal val Online = Color(0xFF58A800)

@Composable
fun qTalkWindowIcon(): Painter = painterResource(Res.drawable.logo_black)
