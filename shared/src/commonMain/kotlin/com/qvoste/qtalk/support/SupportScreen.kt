package com.qvoste.qtalk.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.i18n.uiText
import com.qvoste.qtalk.ui.calls.theme.*
import kotlinx.coroutines.launch

@Composable
fun SupportScreen(session: SupportSession = LocalSupportSession.current, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val editable = !state.busy && state.receipt == null
    val improvement = state.draft.kind == SupportKind.IMPROVEMENT
    val textColors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        disabledTextColor = TextSecondary, focusedBorderColor = Primary, unfocusedBorderColor = DividerColor,
        focusedLabelColor = Primary, unfocusedLabelColor = TextSecondary, cursorColor = Primary)
    Column(modifier.fillMaxSize().background(AppBackground).padding(24.dp)) {
        TextButton(onClick = onBack) { Text("‹  " + uiText("Назад к настройкам"), color = Primary) }
        Spacer(Modifier.height(12.dp))
        Text(uiText("Техническая поддержка"), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                for (kind in SupportKind.entries) {
                    Row(Modifier.selectable(selected = state.draft.kind == kind, enabled = editable, role = Role.RadioButton,
                        onClick = { session.edit { it.copy(kind = kind) } }).testTag("support-kind-" + kind.name.lowercase()),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.draft.kind == kind, onClick = null, enabled = editable,
                            colors = RadioButtonDefaults.colors(selectedColor = Primary, unselectedColor = TextSecondary))
                        Spacer(Modifier.width(8.dp))
                        Text(uiText(kind.title), color = if (state.draft.kind == kind) TextPrimary else TextSecondary, fontSize = 14.sp)
                    }
                }
            }
            Text(uiText(if (improvement)
                    "Предложение отправляется без диагностических данных. Проверьте текст и скриншот перед отправкой."
                else "К обращению автоматически приложат ограниченный архив очищенных журналов QTalk."),
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.testTag("support-note"))
            OutlinedTextField(state.draft.title, { value -> session.edit { it.copy(title = value.take(160)) } },
                label = { Text(uiText("Заголовок * (3–160 символов)")) }, enabled = editable,
                singleLine = true, colors = textColors, modifier = Modifier.fillMaxWidth().testTag("support-title"))
            OutlinedTextField(state.draft.description, { value -> session.edit { it.copy(description = value.take(12000)) } },
                label = { Text(uiText("Описание * (10–12000 символов)")) }, enabled = editable,
                minLines = 6, maxLines = 14, colors = textColors, modifier = Modifier.fillMaxWidth().testTag("support-description"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { scope.launch { session.chooseScreenshot() } }, enabled = editable,
                    modifier = Modifier.testTag("support-attach")) {
                    Text(uiText("Добавить скриншот (PNG / JPEG)"), color = Primary)
                }
                Button(onClick = { scope.launch { session.send() } }, enabled = state.draft.canSend && editable,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary,
                        disabledContainerColor = ControlBackground, disabledContentColor = TextSecondary),
                    modifier = Modifier.testTag("support-send")) {
                    Text(uiText("Отправить в поддержку"))
                }
            }
            state.draft.attachment?.let { attachment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(attachment.filename, color = TextPrimary, fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { session.edit { it.copy(attachment = null) } }, enabled = editable) {
                        Text(uiText("Удалить скриншот"), color = Primary)
                    }
                }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                Text(uiText(state.stage.text), color = TextPrimary)
            }
            state.error?.let { Text(uiText(it.text), color = Danger, modifier = Modifier.testTag("support-error")) }
            state.receipt?.let { receipt ->
                Text(uiText("Обращение принято:") + " " + receipt.ticketId.replace(Regex("^QTK-(?=\\d{5,}$)"), "№"), color = Online)
                TextButton(onClick = { session.edit { SupportDraft(kind = state.draft.kind) } }, enabled = !state.busy) {
                    Text(uiText("Новое обращение"), color = Primary)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
