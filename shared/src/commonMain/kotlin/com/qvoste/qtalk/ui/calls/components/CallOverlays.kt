package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.model.formatDuration
import com.qvoste.qtalk.ui.calls.model.initialsForName
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import com.qvoste.qtalk.ui.calls.theme.*
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import qtalk.shared.generated.resources.*

@Composable
internal fun IncomingCallOverlay(
    number: String,
    callerName: String?,
    callerTelegram: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x88090B0D)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (callerName == null) {
                Avatar("?", number, 88)
            } else {
                ContactAvatar(callerName, callerTelegram, 88)
            }
            Spacer(Modifier.height(20.dp))
            Text(callerName ?: "Входящий вызов", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text("${formatPhoneNumber(number)}  •  Личная линия", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
                CircleIconAction(Res.drawable.icon_phone, 44, Color(0xFFFF3545)) { onDecline() }
                CircleIconAction(Res.drawable.icon_phone, 44, Primary) { onAccept() }
            }
        }
    }
}

@Composable
internal fun ActiveCallOverlay(
    number: String,
    callerName: String?,
    callerTelegram: String,
    connectedAtEpochMillis: Long,
    onHangUp: () -> Unit,
    onCollapse: () -> Unit,
    onDtmf: (Char) -> Unit,
    onMicrophoneMutedChange: (Boolean) -> Unit,
    onSpeakerMutedChange: (Boolean) -> Unit
) {
    var nowEpochMillis by remember(connectedAtEpochMillis) {
        mutableStateOf(Clock.System.now().toEpochMilliseconds())
    }
    var microphoneMuted by remember(number) { mutableStateOf(false) }
    var speakerMuted by remember(number) { mutableStateOf(false) }
    var onHold by rememberSaveable { mutableStateOf(false) }
    var keypadVisible by rememberSaveable { mutableStateOf(false) }
    var enteredDigits by rememberSaveable { mutableStateOf("") }

    fun enterDtmf(digit: Char) {
        if (digit !in "0123456789*#") return
        enteredDigits += digit
        onDtmf(digit)
    }

    LaunchedEffect(connectedAtEpochMillis) {
        while (true) {
            nowEpochMillis = Clock.System.now().toEpochMilliseconds()
            delay(1_000)
        }
    }
    val seconds = if (connectedAtEpochMillis > 0L) {
        ((nowEpochMillis - connectedAtEpochMillis).coerceAtLeast(0L) / 1_000L).toInt()
    } else {
        0
    }

    Box(Modifier.fillMaxSize().background(Color(0x88090B0D)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (callerName == null) {
                Avatar("?", number, 124)
            } else {
                ContactAvatar(callerName, callerTelegram, 124)
            }
            Spacer(Modifier.height(25.dp))
            Text(callerName ?: formatPhoneNumber(number), color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text("${formatPhoneNumber(number)}  •  Личная линия", color = TextSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Text(formatDuration(seconds), color = TextSecondary, fontSize = 18.sp)
            Spacer(Modifier.height(20.dp))

            if (keypadVisible) {
                Row(
                    Modifier.width(280.dp).height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        enteredDigits.ifEmpty { formatPhoneNumber(number) },
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Image(
                        painter = painterResource(Res.drawable.icon_backspace),
                        contentDescription = "Удалить символ",
                        modifier = Modifier.size(22.dp).clickable {
                            if (enteredDigits.isNotEmpty()) enteredDigits = enteredDigits.dropLast(1)
                        }
                    )
                }
                Spacer(Modifier.height(35.dp))
                Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                    listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('*', '0', '#')
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(52.dp)) {
                            row.forEach { digit ->
                                Box(
                                    Modifier.size(60.dp).background(ControlBackground, CircleShape)
                                        .clickable { enterDtmf(digit) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(digit.toString(), color = TextPrimary, fontSize = 21.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(52.dp), verticalAlignment = Alignment.Top) {
                    CallControl(Res.drawable.icon_call_collapse, "Свернуть", true, 22, 22, 60, onClick = onCollapse)
                    CallControl(
                        Res.drawable.icon_phone,
                        "Завершить",
                        true,
                        22,
                        22,
                        60,
                        overrideColor = Color(0xFFFF3545),
                        onClick = onHangUp
                    )
                    TextCallControl("×", "Отмена", 60) { keypadVisible = false }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(35.dp)) {
                    CallControl(
                        Res.drawable.icon_call_microphone,
                        "Микрофон",
                        microphoneMuted,
                        16,
                        25,
                        crossedOut = microphoneMuted
                    ) {
                        microphoneMuted = !microphoneMuted
                        onMicrophoneMutedChange(microphoneMuted)
                    }
                    CallControl(
                        Res.drawable.icon_call_speaker,
                        "Динамик",
                        speakerMuted,
                        22,
                        17,
                        crossedOut = speakerMuted
                    ) {
                        speakerMuted = !speakerMuted
                        onSpeakerMutedChange(speakerMuted)
                    }
                    CallControl(Res.drawable.icon_call_hold, "Удержание", onHold, 18, 20) { onHold = !onHold }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(35.dp)) {
                    CallControl(Res.drawable.icon_call_collapse, "Свернуть", false, 19, 19, onClick = onCollapse)
                    CallControl(Res.drawable.icon_call_keypad, "Клавиатура", false, 22, 14) {
                        keypadVisible = true
                    }
                    CallControl(Res.drawable.icon_call_record, "Запись", false, 20, 20) {}
                }
                Spacer(Modifier.height(20.dp))
                CircleIconAction(Res.drawable.icon_phone, 44, Color(0xFFFF3545)) { onHangUp() }
            }
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
    buttonSize: Int = 44,
    overrideColor: Color? = null,
    crossedOut: Boolean = false,
    onClick: () -> Unit
) {
    Column(Modifier.width(buttonSize.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(buttonSize.dp).background(overrideColor ?: if (active) Primary else ControlBackground, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(iconWidth.dp, iconHeight.dp)
            )
            if (crossedOut) {
                Canvas(Modifier.size(26.dp)) {
                    drawLine(
                        color = Color.White,
                        start = Offset(3.dp.toPx(), 3.dp.toPx()),
                        end = Offset(23.dp.toPx(), 23.dp.toPx()),
                        strokeWidth = 1.7.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun TextCallControl(symbol: String, label: String, buttonSize: Int = 44, onClick: () -> Unit) {
    Column(Modifier.width(buttonSize.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(buttonSize.dp).background(ControlBackground, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = TextPrimary, fontSize = 25.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextSecondary, fontSize = 8.sp, maxLines = 1)
    }
}

