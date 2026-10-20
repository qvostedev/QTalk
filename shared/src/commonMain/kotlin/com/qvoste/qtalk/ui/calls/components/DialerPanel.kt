package com.qvoste.qtalk.ui.calls.components

import com.qvoste.qtalk.ui.calls.i18n.uiText

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.CallState
import com.qvoste.qtalk.voip.RegistrationState
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.*

@Composable
internal fun DialerPanel(
    number: String,
    contact: Contact?,
    contactBeingEdited: Contact?,
    creatingContact: Boolean,
    accountNumber: String,
    registration: RegistrationState,
    call: CallStatus,
    error: String?,
    compact: Boolean,
    contactHistory: List<HistoryItem>,
    contactNotes: List<ContactNote>,
    onNumberChange: (String) -> Unit,
    onEditContact: (Contact) -> Unit,
    onToggleFavorite: (Contact) -> Unit,
    onCancelContactEdit: () -> Unit,
    onSaveContact: (Contact) -> Unit,
    onDeleteContact: (() -> Unit)?,
    onAddNote: (String) -> Unit,
    onUpdateNote: (ContactNote, String) -> Unit,
    onDeleteNote: (ContactNote) -> Unit,
    onTextInputFocusChange: (Boolean) -> Unit,
    onCall: () -> Unit,
    onHangUp: () -> Unit,
    onTransferEmployee: (Contact) -> Unit
) {
    var infoTab by rememberSaveable { mutableStateOf("Информация") }
    val uriHandler = LocalUriHandler.current
    val editing = creatingContact || contactBeingEdited != null
    val displayedContact = if (creatingContact) null else contactBeingEdited ?: contact
    var selectedNoteId by remember(displayedContact?.name, displayedContact?.number) { mutableStateOf<String?>(null) }
    var noteBeingEditedId by remember(displayedContact?.name, displayedContact?.number) { mutableStateOf<String?>(null) }
    var creatingNote by remember(displayedContact?.name, displayedContact?.number) { mutableStateOf(false) }
    var noteDraft by remember(displayedContact?.name, displayedContact?.number) { mutableStateOf("") }
    val selectedNote = contactNotes.firstOrNull { it.id == selectedNoteId }
    val noteBeingEdited = contactNotes.firstOrNull { it.id == noteBeingEditedId }
    var draftName by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.name.orEmpty())
    }
    var draftNumber by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.number.orEmpty())
    }
    var draftPosition by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.position.orEmpty())
    }
    var draftDepartment by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.department.orEmpty())
    }
    var draftCompany by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.company ?: "ПОРТ")
    }
    var draftTelegram by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.telegram.orEmpty())
    }
    var draftFavorite by remember(creatingContact, contactBeingEdited) {
        mutableStateOf(contactBeingEdited?.favorite ?: false)
    }
    val canSaveContact = draftName.isNotBlank() && (!creatingContact || draftNumber.isNotBlank())
    LaunchedEffect(editing) {
        if (editing) infoTab = "Информация"
    }
    LaunchedEffect(contactNotes, infoTab) {
        if (infoTab == "Заметки" && selectedNoteId !in contactNotes.map { it.id }) {
            selectedNoteId = contactNotes.firstOrNull()?.id
        }
    }
    val shownNumber = if (call.state.isInProgress) {
        formatPhoneNumber(call.number.ifBlank { number })
    } else number
    val panelWidth = if (compact) 311.dp else 320.dp
    val panelHorizontalPadding = if (compact) 32.dp else 37.dp
    val panelContentWidth = panelWidth - panelHorizontalPadding * 2

    BoxWithConstraints(Modifier.width(panelWidth).fillMaxHeight().background(PanelBackground)) {
    val keypadSize = if (maxHeight < 650.dp) 44.dp else 54.dp
    val roomy = maxHeight >= 940.dp
    val keypadGap = ((maxHeight.value - 620f) / 10f).coerceIn(8f, 34f).dp
    Column(
        Modifier.fillMaxWidth().heightIn(max = 960.dp).fillMaxHeight()
            .padding(
                start = panelHorizontalPadding,
                top = 16.dp,
                end = panelHorizontalPadding,
                bottom = if (roomy) 39.dp else 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Row(
            Modifier.width(panelContentWidth).height(62.dp)
                .transferOnRightClick(
                    !editing && call.state == CallState.ACTIVE &&
                        displayedContact?.let(::transferCodeFor) != null
                ) { displayedContact?.let(onTransferEmployee) },
            verticalAlignment = Alignment.Top
        ) {
            val avatarContact = if (creatingContact) null else displayedContact
            if (avatarContact != null) {
                ContactAvatarWithStatus(
                    avatarContact.name,
                    avatarContact.telegram,
                    62,
                    avatarContact.status.color,
                    avatarContact.avatarUrl
                )
            } else {
                val avatarSeed = draftName.takeIf(String::isNotBlank)
                    ?: (if (creatingContact) draftNumber else shownNumber).takeIf(String::isNotBlank)
                Avatar(
                    draftName.takeIf(String::isNotBlank)?.let(::initialsForName) ?: "?",
                    avatarSeed,
                    62
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (editing) {
                    InlineTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = "ФИО контакта",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp
                    )
                } else {
                    Text(
                        displayedContact?.name ?: uiText("Контакт не выбран"),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiText("Номер  •  "), color = TextSecondary, fontSize = 10.sp, lineHeight = 10.sp)
                        InlineTextField(
                            value = draftNumber,
                            onValueChange = { draftNumber = it.filter(Char::isDigit) },
                            placeholder = "номер",
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            textColor = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        displayedContact?.let {
                            uiText("Номер  •  ") + formatPhoneNumber(it.number.ifBlank { it.phone }).ifBlank { "—" }
                        }
                            ?: uiText("Здесь появится информация"),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        maxLines = 1
                    )
                }
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
                editing -> Column(Modifier.padding(top = 8.dp)) {
                    EditableContactInfo(
                        Res.drawable.icon_position,
                        "Должность",
                        draftPosition,
                        { draftPosition = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    EditableContactInfo(
                        Res.drawable.icon_department,
                        "Отдел",
                        draftDepartment,
                        { draftDepartment = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    EditableContactInfo(
                        Res.drawable.icon_company,
                        "Компания",
                        draftCompany,
                        { draftCompany = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    EditableContactInfo(
                        Res.drawable.icon_telegram,
                        "Telegram",
                        draftTelegram,
                        { draftTelegram = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        ContactTextAction(
                            text = "Отмена",
                            color = TextSecondary,
                            onClick = onCancelContactEdit
                        )
                        Spacer(Modifier.width(24.dp))
                        ContactTextAction(
                            text = "Сохранить",
                            color = Primary,
                            enabled = canSaveContact
                        ) {
                                val source = contactBeingEdited ?: Contact("", "", false)
                                onSaveContact(
                                    source.copy(
                                        name = draftName.trim(),
                                        number = draftNumber.trim(),
                                        favorite = draftFavorite,
                                        position = draftPosition.trim(),
                                        department = draftDepartment.trim(),
                                        company = draftCompany.trim(),
                                        telegram = normalizeTelegramInput(draftTelegram)
                                    )
                                )
                        }
                    }
                }
                displayedContact == null -> EmptyMessage("Выберите контакт или наберите номер")
                infoTab == "Информация" -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(top = 8.dp)) {
                        ContactInfo(Res.drawable.icon_position, "Должность", displayedContact.position.ifBlank { uiText("Не указана") })
                        Spacer(Modifier.height(14.dp))
                        ContactInfo(Res.drawable.icon_department, "Отдел", displayedContact.department.ifBlank { uiText("Не указан") })
                        Spacer(Modifier.height(14.dp))
                        ContactInfo(
                            Res.drawable.icon_company,
                            "Компания",
                            listOf(displayedContact.company, displayedContact.office).filter(String::isNotBlank)
                                .joinToString(" • ").ifBlank { uiText("Не указана") }
                        )
                        Spacer(Modifier.height(14.dp))
                        ContactInfo(
                            Res.drawable.icon_telegram,
                            "Telegram",
                            displayedContact.telegram.ifBlank { uiText("Не указан") },
                            displayedContact.telegram.isNotBlank()
                        ) {
                            val username = displayedContact.telegram.removePrefix("@").trim()
                            if (username.isNotBlank()) uriHandler.openUri("https://t.me/$username")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        onDeleteContact?.let { deleteContact ->
                            ContactTextAction("Удалить", Danger, onClick = deleteContact)
                        }
                        Spacer(Modifier.weight(1f))
                        ContactTextAction(
                            if (displayedContact.favorite) "Открепить" else "Закрепить",
                            TextPrimary
                        ) { onToggleFavorite(displayedContact) }
                        Spacer(Modifier.width(14.dp))
                        ContactTextAction("Изменить", TextPrimary) { onEditContact(displayedContact) }
                    }
                }
                infoTab == "История" -> ContactHistoryList(contactHistory)
                infoTab == "Заметки" -> ContactNotes(
                    notes = contactNotes,
                    selectedNote = selectedNote,
                    editingNote = noteBeingEdited,
                    creatingNote = creatingNote,
                    draft = noteDraft,
                    onDraftChange = { noteDraft = it },
                    onSelect = { selectedNoteId = it.id },
                    onAdd = {
                        creatingNote = true
                        noteBeingEditedId = null
                        noteDraft = ""
                    },
                    onEdit = { note ->
                        creatingNote = false
                        noteBeingEditedId = note.id
                        noteDraft = note.text
                    },
                    onDelete = { note ->
                        onDeleteNote(note)
                        selectedNoteId = null
                        noteBeingEditedId = null
                        creatingNote = false
                        noteDraft = ""
                    },
                    onCancel = {
                        creatingNote = false
                        noteBeingEditedId = null
                        noteDraft = ""
                    },
                    onSave = {
                        if (creatingNote) onAddNote(noteDraft)
                        else noteBeingEdited?.let { onUpdateNote(it, noteDraft) }
                        creatingNote = false
                        noteBeingEditedId = null
                        noteDraft = ""
                    },
                    onTextInputFocusChange = onTextInputFocusChange
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        }
        Text(
            uiText("Личная линия сотрудника:  ${accountNumber.ifBlank { "—" }}"),
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            modifier = Modifier.width(panelContentWidth),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(if (roomy) 22.dp else 10.dp))
        Box(Modifier.width(panelContentWidth).height(29.dp), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = shownNumber,
                onValueChange = { if (!call.state.isInProgress) onNumberChange(it) },
                enabled = !call.state.isInProgress,
                singleLine = true,
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp)
                    .onFocusChanged { onTextInputFocusChange(it.isFocused) },
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (shownNumber.isBlank()) Text("—", color = TextPrimary, fontSize = 20.sp)
                        innerTextField()
                    }
                }
            )
            Image(
                painterResource(Res.drawable.icon_backspace),
                "Удалить цифру",
                Modifier.align(Alignment.CenterEnd).size(24.dp, 18.dp)
                    .clickable(enabled = number.isNotEmpty() && !call.state.isInProgress) { onNumberChange(number.dropLast(1)) },
                colorFilter = if (IsLightTheme) ColorFilter.tint(TextSecondary) else null
            )
        }
        Spacer(Modifier.height(if (roomy) 28.dp else 12.dp))
        Column(
            Modifier.width(234.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(keypadGap)
        ) {
            keypadRows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { key ->
                        KeypadButton(key, keypadSize) {
                            if (!call.state.isInProgress) onNumberChange(number + key)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (roomy) 34.dp else 12.dp))
        val canCall = registration == RegistrationState.REGISTERED && number.isNotBlank() && !call.state.isInProgress
        CircleIconAction(
            Res.drawable.icon_phone,
            54,
            if (call.state.isInProgress) Danger else if (canCall) Primary
                else if (IsLightTheme) ControlBackground else Color(0xFF293142),
            Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (call.state.isInProgress) onHangUp() else if (canCall) onCall()
        }
        error?.let { Text(it, color = Danger, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
    }
}

@Composable
private fun ContactHistoryList(history: List<HistoryItem>) {
    if (history.isEmpty()) {
        EmptyMessage("Звонков пока нет")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(history) { item ->
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.Top
            ) {
                ContactAvatar(item.name, item.telegram, 36, item.avatarUrl)
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f).offset(y = (-3).dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(item.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (item.missed) "×  " else if (item.direction == "Исходящий") "↗  " else "↙  ") + uiText(item.direction),
                            color = if (item.missed) Danger else TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatHistoryTimestamp(item.startedAtEpochMillis, Clock.System.now().toEpochMilliseconds()),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactNotes(
    notes: List<ContactNote>,
    selectedNote: ContactNote?,
    editingNote: ContactNote?,
    creatingNote: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSelect: (ContactNote) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ContactNote) -> Unit,
    onDelete: (ContactNote) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onTextInputFocusChange: (Boolean) -> Unit
) {
    val editing = creatingNote || editingNote != null
    Box(Modifier.fillMaxSize()) {
        if (editing) {
            DisposableEffect(Unit) {
                onDispose { onTextInputFocusChange(false) }
            }
            Column(Modifier.fillMaxSize()) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 11.sp, lineHeight = 15.sp),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.fillMaxWidth().height(184.dp)
                        .onFocusChanged { onTextInputFocusChange(it.isFocused) }
                        .background(ControlBackground, RoundedCornerShape(10.dp)).padding(12.dp),
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxSize()) {
                            if (draft.isBlank()) Text(uiText("Введите текст заметки"), color = TextSecondary, fontSize = 11.sp)
                            innerTextField()
                        }
                    }
                )
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
                if (editingNote != null) ContactTextAction("Удалить", Danger) { onDelete(editingNote) }
                Spacer(Modifier.weight(1f))
                ContactTextAction("Отмена", TextSecondary, onClick = onCancel)
                Spacer(Modifier.width(14.dp))
                ContactTextAction("Сохранить", Primary, enabled = draft.isNotBlank(), onClick = onSave)
            }
        } else {
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                    Text(uiText("Заметок пока нет"), color = TextSecondary, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().height(190.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(
                                    if (selectedNote?.id == note.id) Primary.copy(alpha = 0.14f) else ControlBackground,
                                    RoundedCornerShape(9.dp)
                                )
                                .clickable { onSelect(note) }
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                        ) {
                            Text(
                                note.text,
                                color = TextPrimary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), verticalAlignment = Alignment.CenterVertically) {
                ContactTextAction("Удалить", Danger, enabled = selectedNote != null) {
                    selectedNote?.let(onDelete)
                }
                Spacer(Modifier.weight(1f))
                ContactTextAction("Изменить", TextPrimary, enabled = selectedNote != null) {
                    selectedNote?.let(onEdit)
                }
                Spacer(Modifier.width(14.dp))
                ContactTextAction("Добавить", TextPrimary, onClick = onAdd)
            }
        }
    }
}

@Composable
private fun ContactTextAction(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = uiText(text),
        color = if (enabled) color else color.copy(alpha = 0.35f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp)
    )
}

@Composable
private fun InfoTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(uiText(text), color = if (selected) Primary else TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.width(58.dp).height(1.dp).background(if (selected) Primary else Color.Transparent))
    }
}

@Composable
private fun ContactInfo(icon: DrawableResource, label: String, value: String, isLink: Boolean = false, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(34.dp).clickable(enabled = isLink, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), label, Modifier.size(22.dp),
            colorFilter = if (IsLightTheme) ColorFilter.tint(TextPrimary) else null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(uiText(label), color = TextPrimary, fontSize = 11.sp, lineHeight = 14.sp)
            Text(value, color = if (isLink) Primary else TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun EditableContactInfo(
    icon: DrawableResource,
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), label, Modifier.size(22.dp),
            colorFilter = if (IsLightTheme) ColorFilter.tint(TextPrimary) else null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(uiText(label), color = TextPrimary, fontSize = 11.sp, lineHeight = 14.sp)
            InlineTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Не указано",
                fontSize = 11.sp,
                lineHeight = 14.sp,
                textColor = TextSecondary
            )
        }
    }
}

@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textColor: Color = TextPrimary,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = textColor,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight
        ),
        cursorBrush = SolidColor(Primary),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(uiText(placeholder), color = TextSecondary, fontSize = fontSize, lineHeight = lineHeight)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun KeypadButton(value: String, size: androidx.compose.ui.unit.Dp = 54.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(ControlBackground).clickable(onClick = onClick), Alignment.Center) {
        Text(value, color = TextPrimary, fontSize = 19.sp, lineHeight = 19.sp)
    }
}
