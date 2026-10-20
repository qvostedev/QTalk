package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.model.formatDuration
import com.qvoste.qtalk.ui.calls.theme.ControlBackground
import com.qvoste.qtalk.ui.calls.theme.AppBackground
import com.qvoste.qtalk.ui.calls.theme.IsLightTheme
import com.qvoste.qtalk.ui.calls.theme.Primary
import com.qvoste.qtalk.ui.calls.theme.Online
import com.qvoste.qtalk.ui.calls.theme.Danger
import com.qvoste.qtalk.ui.calls.theme.TextPrimary
import com.qvoste.qtalk.ui.calls.theme.TextSecondary
import com.qvoste.qtalk.voip.CallState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.icon_call_microphone
import qtalk.shared.generated.resources.icon_call_speaker
import qtalk.shared.generated.resources.icon_phone

/** Compact Mango-style call surface shown over QTalk or above the desktop. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactCallWidget(
    state: CallState,
    number: String,
    callerName: String?,
    callerTelegram: String,
    connectedAtEpochMillis: Long,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onMicrophoneMutedChange: (Boolean) -> Unit = {},
    onSpeakerMutedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
    callerAvatarUrl: String = ""
) {
    var nowEpochMillis by remember(connectedAtEpochMillis) {
        mutableStateOf(Clock.System.now().toEpochMilliseconds())
    }
    var microphoneMuted by remember(number) { mutableStateOf(false) }
    var speakerMuted by remember(number) { mutableStateOf(false) }
    LaunchedEffect(connectedAtEpochMillis, state) {
        if (state == CallState.ACTIVE) {
            while (true) {
                nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                delay(1_000)
            }
        }
    }
    val seconds = if (connectedAtEpochMillis > 0L) {
        ((nowEpochMillis - connectedAtEpochMillis).coerceAtLeast(0L) / 1_000L).toInt()
    } else {
        0
    }

    Box(
        modifier
            .background(AppBackground)
            .combinedClickable(
                onClick = {},
                onDoubleClick = onOpen
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (callerName == null) {
                Avatar("?", number, 38)
            } else {
                ContactAvatar(callerName, callerTelegram, 38, callerAvatarUrl)
            }
            Spacer(Modifier.width(7.dp))
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    callerName ?: formatPhoneNumber(number).ifBlank { "Неизвестный номер" },
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when (state) {
                        CallState.INCOMING -> "Входящий звонок"
                        CallState.DIALING -> "Вызов  •  ${formatPhoneNumber(number)}"
                        CallState.RINGING -> "Ожидание ответа"
                        CallState.ENDING -> "Завершение звонка"
                        else -> formatDuration(seconds)
                    },
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(5.dp))
            if (state == CallState.INCOMING) {
                IncomingWidgetAction(accept = true, onClick = onAccept)
                Spacer(Modifier.width(5.dp))
                IncomingWidgetAction(accept = false, onClick = onDecline)
            } else {
                WidgetIconAction(
                    icon = Res.drawable.icon_call_microphone,
                    active = microphoneMuted,
                    crossedOut = microphoneMuted
                ) {
                    microphoneMuted = !microphoneMuted
                    onMicrophoneMutedChange(microphoneMuted)
                }
                Spacer(Modifier.width(5.dp))
                WidgetIconAction(
                    icon = Res.drawable.icon_call_speaker,
                    active = speakerMuted,
                    crossedOut = speakerMuted
                ) {
                    speakerMuted = !speakerMuted
                    onSpeakerMutedChange(speakerMuted)
                }
                Spacer(Modifier.width(5.dp))
                CircleIconAction(Res.drawable.icon_phone, 30, Danger) { onHangUp() }
            }
        }
    }
}

@Composable
private fun IncomingWidgetAction(accept: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (accept) Online else Danger)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(Res.drawable.icon_phone),
            contentDescription = if (accept) "Принять" else "Отклонить",
            modifier = Modifier.size(14.dp).rotate(if (accept) 0f else 135f)
        )
    }
}

@Composable
private fun WidgetIconAction(
    icon: DrawableResource,
    active: Boolean,
    crossedOut: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(if (active) Primary else ControlBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(icon), null, Modifier.size(14.dp),
            colorFilter = if (IsLightTheme && !active) ColorFilter.tint(TextPrimary) else null)
        if (crossedOut) {
            val slashColor = if (IsLightTheme && !active) TextPrimary else Color.White
            Canvas(Modifier.size(20.dp)) {
                drawLine(
                    slashColor,
                    Offset(3.dp.toPx(), 3.dp.toPx()),
                    Offset(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                    1.6.dp.toPx(),
                    StrokeCap.Round
                )
            }
        }
    }
}
