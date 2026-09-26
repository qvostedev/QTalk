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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.RegistrationState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.*

@Composable
internal fun DialerPanel(
    number: String,
    contact: Contact?,
    accountNumber: String,
    registration: RegistrationState,
    call: CallStatus,
    error: String?,
    compact: Boolean,
    onNumberChange: (String) -> Unit,
    onCall: () -> Unit,
    onHangUp: () -> Unit
) {
    var infoTab by rememberSaveable { mutableStateOf("Информация") }
    val uriHandler = LocalUriHandler.current
    val shownNumber = if (call.state.isInProgress) call.number.ifBlank { number } else number
    // Номеронабиратель не меняет геометрию в компактном режиме.
    val panelContentWidth = 285.dp

    Column(
        Modifier.width(416.dp).fillMaxHeight().background(PanelBackground)
            .padding(horizontal = 37.dp, vertical = 39.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.width(panelContentWidth).height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                contact?.let { initialsForName(it.name) } ?: "?",
                contact?.let { colorFor(it.name) } ?: ControlBackground,
                62
            )
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    contact?.name ?: "Контакт не выбран",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    contact?.let { "Внутренний  •  ${it.number}" }
                        ?: "Здесь появится информация о контакте.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.width(panelContentWidth), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Информация", "История", "Заметки").forEach { tab ->
                InfoTab(tab, infoTab == tab) { infoTab = tab }
            }
        }
        Spacer(Modifier.height(29.dp))
        Box(Modifier.width(panelContentWidth).height(246.dp)) {
            when {
                contact == null -> EmptyMessage("Выберите контакт или наберите номер")
                infoTab == "Информация" -> Column(
                    Modifier.padding(top = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    ContactInfo(Res.drawable.icon_position, "Должность", contact.position.ifBlank { "Не указана" })
                    ContactInfo(Res.drawable.icon_department, "Отдел", contact.department.ifBlank { "Не указан" })
                    ContactInfo(Res.drawable.icon_company, "Компания", contact.company.ifBlank { "Не указана" })
                    ContactInfo(
                        Res.drawable.icon_telegram,
                        "Telegram",
                        contact.telegram.ifBlank { "Не указан" },
                        contact.telegram.isNotBlank()
                    ) {
                        val username = contact.telegram.removePrefix("@").trim()
                        if (username.isNotBlank()) uriHandler.openUri("https://t.me/$username")
                    }
                }
                else -> EmptyMessage(if (infoTab == "История") "Звонков пока нет" else "Заметок пока нет")
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Личная линия сотрудника:  ${accountNumber.ifBlank { "—" }}",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            modifier = Modifier.width(panelContentWidth),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Box(Modifier.width(panelContentWidth).height(29.dp), contentAlignment = Alignment.Center) {
            Text(shownNumber.ifBlank { "—" }, color = TextPrimary, fontSize = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Image(
                painterResource(Res.drawable.icon_backspace),
                "Удалить цифру",
                Modifier.align(Alignment.CenterEnd).size(24.dp, 18.dp)
                    .clickable(enabled = number.isNotEmpty() && !call.state.isInProgress) { onNumberChange(number.dropLast(1)) }
            )
        }
        Spacer(Modifier.height(28.dp))
        Column(
            Modifier.width(234.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(34.dp)
        ) {
            keypadRows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { key ->
                        KeypadButton(key.first, key.second) {
                            if (!call.state.isInProgress) onNumberChange(number + key.first)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(34.dp))
        val canCall = registration == RegistrationState.REGISTERED && number.isNotBlank() && !call.state.isInProgress
        CircleIconAction(
            Res.drawable.icon_phone,
            54,
            if (call.state.isInProgress) Color(0xFFFF3545) else if (canCall) Primary else Color(0xFF293142),
            Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (call.state.isInProgress) onHangUp() else if (canCall) onCall()
        }
        error?.let { Text(it, color = Danger, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
private fun InfoTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = if (selected) Primary else TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.width(58.dp).height(1.dp).background(if (selected) Primary else Color.Transparent))
    }
}

@Composable
private fun ContactInfo(icon: DrawableResource, label: String, value: String, isLink: Boolean = false, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(26.dp).clickable(enabled = isLink, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), label, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = TextPrimary, fontSize = 10.sp, lineHeight = 12.sp)
            Text(value, color = if (isLink) Primary else TextSecondary, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun KeypadButton(value: String, letters: String, onClick: () -> Unit) {
    Box(Modifier.size(54.dp).background(ControlBackground, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = TextPrimary, fontSize = 19.sp, lineHeight = 19.sp)
            if (letters.isNotBlank()) Text(letters, color = TextSecondary, fontSize = 8.sp, lineHeight = 8.sp)
        }
    }
}
