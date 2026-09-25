package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.model.colorFor
import com.qvoste.qtalk.ui.calls.model.formatDuration
import com.qvoste.qtalk.ui.calls.model.initialsForName
import com.qvoste.qtalk.ui.calls.theme.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import qtalk.shared.generated.resources.*

@Composable
internal fun IncomingCallOverlay(
    number: String,
    callerName: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x880B0D10)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(
                callerName?.let(::initialsForName) ?: number.take(2),
                colorFor(callerName ?: number),
                88
            )
            Spacer(Modifier.height(20.dp))
            Text(callerName ?: "Входящий вызов", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text("$number  •  Личная линия", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
                CircleIconAction(Res.drawable.icon_phone, 44, Color(0xFFFF3545)) { onDecline() }
                CircleIconAction(Res.drawable.icon_phone, 44, Primary) { onAccept() }
            }
        }
    }
}

@Composable
internal fun ActiveCallOverlay(number: String, callerName: String?, onHangUp: () -> Unit) {
    var seconds by remember(number) { mutableStateOf(0) }
    var microphoneEnabled by rememberSaveable { mutableStateOf(true) }
    var speakerEnabled by rememberSaveable { mutableStateOf(true) }
    var onHold by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(number) {
        while (true) {
            delay(1_000)
            seconds++
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0x880B0D10)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(callerName?.let(::initialsForName) ?: number.take(2), colorFor(callerName ?: number), 88)
            Spacer(Modifier.height(20.dp))
            Text(callerName ?: number, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text("$number  •  Личная линия", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Text(formatDuration(seconds), color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(35.dp)) {
                CallControl(Res.drawable.icon_call_microphone, "Микрофон", microphoneEnabled, 16, 25) {
                    microphoneEnabled = !microphoneEnabled
                }
                CallControl(Res.drawable.icon_call_speaker, "Динамик", speakerEnabled, 22, 17) {
                    speakerEnabled = !speakerEnabled
                }
                CallControl(Res.drawable.icon_call_hold, "Удержание", onHold, 18, 20) { onHold = !onHold }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(35.dp)) {
                CallControl(Res.drawable.icon_call_keypad, "Клавиатура", false, 22, 14) {}
                CallControl(Res.drawable.icon_call_transfer, "Перевести", false, 12, 12) {}
                CallControl(Res.drawable.icon_call_record, "Запись", false, 20, 20) {}
            }
            Spacer(Modifier.height(20.dp))
            CircleIconAction(Res.drawable.icon_phone, 44, Color(0xFFFF3545)) { onHangUp() }
        }
    }
}

@Composable
private fun CallControl(
    icon: DrawableResource,
    label: String,
    active: Boolean,
    iconWidth: Int,
    iconHeight: Int,
    onClick: () -> Unit
) {
    Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(44.dp).background(if (active) Primary else ControlBackground, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(iconWidth.dp, iconHeight.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 8.sp, maxLines = 1)
    }
}

