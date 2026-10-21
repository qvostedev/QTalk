package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.i18n.uiText
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.model.formatDuration
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import com.qvoste.qtalk.voip.*
import com.qvoste.qtalk.ui.calls.theme.*
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.*

@Composable
internal fun ConferenceCallOverlay(
    conference: ConferenceStatus, ownNumber: String, contactForNumber: (String) -> Contact?,
    seconds: Int, error: String?, onMerge: () -> Unit, onCancel: () -> Unit,
    onRemove: (String) -> Unit, onMicrophone: (Boolean) -> Unit, onSpeaker: (Boolean) -> Unit,
    onCollapse: () -> Unit, onHangUp: () -> Unit, onAdd: () -> Unit = {},
    onDtmf: (Char) -> Unit = {}, onHold: (Boolean) -> Unit = {}
) {
    var keypadVisible by remember { mutableStateOf(false) }
    val pending = (conference.pendingNumbers + conference.invitedNumber.takeIf { conference.phase != ConferencePhase.ACTIVE }.orEmpty())
        .filter { it.isNotBlank() && it !in conference.participants && it != ownNumber }.distinct()
    val members = (listOf(ownNumber) + conference.participants + pending).filter(String::isNotBlank).distinct()
    Box(Modifier.fillMaxSize().background(Color(0xD9090B0D)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 850.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                members.forEach { number ->
                    val own = number == ownNumber
                    val speaking = if (own) conference.localSpeaking else number in conference.speakingNumbers
                    val muted = if (own) conference.microphoneMuted || conference.held else number in conference.mutedNumbers
                    Column(Modifier.width(156.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(132.dp).border(2.dp, if (speaking && !muted) Online else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) {
                            val contact = contactForNumber(number)
                            if (contact != null) ContactAvatar(contact.name, contact.telegram, 124, contact.avatarUrl)
                            else Avatar("?", number, 124)
                            if (muted) Box(Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp)
                                .size(28.dp).background(Danger, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Image(painterResource(Res.drawable.icon_call_microphone), uiText("Микрофон выключен"), Modifier.size(13.dp, 19.dp))
                                Canvas(Modifier.size(24.dp)) {
                                    drawLine(Color.White, Offset(3.dp.toPx(), 3.dp.toPx()), Offset(21.dp.toPx(), 21.dp.toPx()),
                                        strokeWidth = 1.7.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(contactForNumber(number)?.name ?: formatPhoneNumber(number), color = TextPrimary,
                            fontSize = 16.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(25.dp))
            Text(uiText("Групповой звонок"), color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Text(uiText("Выход организатора завершит звонок"), color = TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text(formatDuration(seconds), color = TextSecondary, fontSize = 18.sp)
            error?.let {
                Text(uiText(it), color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(20.dp))
            if (keypadVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    "123456789*0#".toList().chunked(3).forEach { digits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            digits.forEach { digit ->
                                Box(Modifier.size(42.dp).clip(CircleShape).background(ControlBackground).clickable { onDtmf(digit) },
                                    contentAlignment = Alignment.Center) { Text(digit.toString(), color = TextPrimary, fontSize = 19.sp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallControl(Res.drawable.icon_call_microphone, "Микрофон", conference.microphoneMuted, 16, 25,
                    crossedOut = conference.microphoneMuted, enabled = !conference.held) { onMicrophone(!conference.microphoneMuted) }
                CallControl(Res.drawable.icon_call_speaker, "Динамик", conference.speakerMuted, 22, 17,
                    crossedOut = conference.speakerMuted, enabled = !conference.held) { onSpeaker(!conference.speakerMuted) }
                CallControl(Res.drawable.icon_call_hold, "Удержание", conference.held, 18, 20,
                    enabled = conference.phase == ConferencePhase.ACTIVE) { onHold(!conference.held) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallControl(Res.drawable.icon_call_collapse, "Свернуть", false, 15, 15, onClick = onCollapse)
                CallControl(Res.drawable.icon_call_keypad, "Клавиатура", keypadVisible, 22, 14) { keypadVisible = !keypadVisible }
                CallControl(Res.drawable.icon_call_add, "Добавить", false, 22, 22,
                    enabled = conference.isHost && !conference.held && pending.isEmpty() &&
                        conference.phase == ConferencePhase.ACTIVE && members.size < MAX_CONFERENCE_PARTICIPANTS, onClick = onAdd)
            }
            Spacer(Modifier.height(18.dp))
            CircleIconAction(Res.drawable.icon_phone, 42, Danger, onClick = onHangUp)
        }
    }
}
