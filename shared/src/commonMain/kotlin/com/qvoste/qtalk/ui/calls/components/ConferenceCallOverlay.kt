package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.i18n.uiText
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.model.formatDuration
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.ConferencePhase
import com.qvoste.qtalk.voip.ConferenceStatus
import qtalk.shared.generated.resources.*

@Composable
internal fun ConferenceCallOverlay(
    conference: ConferenceStatus,
    ownNumber: String,
    contactForNumber: (String) -> Contact?,
    seconds: Int,
    error: String?,
    onMerge: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (String) -> Unit,
    onMicrophone: (Boolean) -> Unit,
    onSpeaker: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onHangUp: () -> Unit
) {
    var removingNumber by remember { mutableStateOf<String?>(null) }
    val inviting = conference.phase in setOf(ConferencePhase.INVITING, ConferencePhase.CONSULTING, ConferencePhase.RESTORING)
    val numbers = conference.participants + if (inviting && conference.invitedNumber.isNotBlank())
        listOf(conference.invitedNumber) else emptyList()
    Box(Modifier.fillMaxSize().background(AppBackground.copy(alpha = .97f)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 940.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(uiText("Конференция"), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(formatDuration(seconds), color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConferenceParticipantCard(Modifier.weight(1f), contactForNumber(ownNumber), ownNumber,
                    uiText("Вы"), uiText("Вы") + " • " + uiText("Организатор"), conference.localSpeaking, conference.microphoneMuted)
                numbers.distinct().forEach { number ->
                    val invited = inviting && number == conference.invitedNumber
                    val status = when {
                        invited && conference.phase == ConferencePhase.INVITING -> uiText("Звоним…")
                        invited && conference.phase == ConferencePhase.CONSULTING -> uiText("Готов к подключению")
                        conference.phase == ConferencePhase.RESTORING -> uiText("Возвращаемся к звонку…")
                        inviting && !invited -> uiText("На удержании")
                        conference.phase == ConferencePhase.MERGING -> uiText("Подключаем…")
                        else -> uiText("В звонке")
                    }
                    ConferenceParticipantCard(Modifier.weight(1f), contactForNumber(number), number, null, status,
                        number in conference.speakingNumbers, number in conference.mutedNumbers,
                        onRemove = if (conference.phase == ConferencePhase.ACTIVE) ({ removingNumber = number }) else null)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (inviting) {
                Text(uiText(if (conference.phase == ConferencePhase.CONSULTING)
                    "Участник ответил. Добавьте его в конференцию или вернитесь к первому звонку."
                    else "Первый собеседник на удержании. Приглашение можно отменить."),
                    color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (conference.phase == ConferencePhase.CONSULTING) {
                        Button(onClick = onMerge, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                            Text(uiText("Добавить в конференцию"))
                        }
                    }
                    TextButton(onClick = onCancel, enabled = conference.phase != ConferencePhase.RESTORING) {
                        Text(uiText("Вернуться к первому звонку"), color = Primary)
                    }
                }
            }
            (error ?: conference.message.takeIf { it.isNotBlank() })?.let {
                Text(uiText(it), color = Danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(ControlBackground).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                CallControl(Res.drawable.icon_call_microphone, "Микрофон", conference.microphoneMuted, 16, 25,
                    crossedOut = conference.microphoneMuted) { onMicrophone(!conference.microphoneMuted) }
                CallControl(Res.drawable.icon_call_speaker, "Динамик", conference.speakerMuted, 22, 17,
                    crossedOut = conference.speakerMuted) { onSpeaker(!conference.speakerMuted) }
                CallControl(Res.drawable.icon_call_collapse, "Свернуть", false, 15, 15, onClick = onCollapse)
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onHangUp, colors = ButtonDefaults.buttonColors(containerColor = Danger)) {
                Text(uiText("Завершить для всех"))
            }
            Spacer(Modifier.height(12.dp))
            Text(uiText("Звонок работает на вашем компьютере. При его завершении отключатся все участники."),
                color = TextSecondary, fontSize = 11.sp)
        }
    }
    removingNumber?.let { number ->
        val name = contactForNumber(number)?.name ?: formatPhoneNumber(number)
        AlertDialog(onDismissRequest = { removingNumber = null }, containerColor = ControlBackground,
            title = { Text(uiText("Отключить участника?"), color = TextPrimary) },
            text = { Column { Text(name, color = TextPrimary); Spacer(Modifier.height(8.dp))
                Text(uiText("Остальные участники продолжат разговор."), color = TextSecondary) } },
            confirmButton = { TextButton(onClick = { removingNumber = null; onRemove(number) }) {
                Text(uiText("Отключить"), color = Danger) } },
            dismissButton = { TextButton(onClick = { removingNumber = null }) { Text(uiText("Отмена")) } })
    }
}

@Composable
private fun ConferenceParticipantCard(
    modifier: Modifier, contact: Contact?, number: String, fallbackName: String?, status: String,
    speaking: Boolean, muted: Boolean, onRemove: (() -> Unit)? = null
) {
    var menuOpen by remember(number) { mutableStateOf(false) }
    val name = contact?.name ?: fallbackName ?: formatPhoneNumber(number)
    Column(modifier.height(230.dp).clip(RoundedCornerShape(20.dp)).background(ControlBackground)
        .border(if (speaking) 2.dp else 1.dp, if (speaking) Online else TextSecondary.copy(alpha = .15f), RoundedCornerShape(20.dp))
        .padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterEnd) {
            if (onRemove != null) {
                TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(0.dp)) { Text("⋮", color = TextSecondary) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(uiText("Отключить от конференции")) },
                        onClick = { menuOpen = false; onRemove() })
                }
            }
        }
        if (contact != null) ContactAvatar(contact.name, contact.telegram, 64, contact.avatarUrl)
        else Avatar(fallbackName?.take(1) ?: "?", number, 64)
        Spacer(Modifier.height(16.dp))
        Text(name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatPhoneNumber(number), color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Text(if (muted) uiText("Микрофон выключен") else if (speaking) uiText("Говорит") else status,
            color = if (speaking) Online else TextSecondary, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
