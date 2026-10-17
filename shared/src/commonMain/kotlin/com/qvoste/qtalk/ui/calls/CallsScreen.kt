package com.qvoste.qtalk.ui.calls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import com.qvoste.qtalk.ui.calls.model.formatPhoneNumber
import com.qvoste.qtalk.voip.CallState
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.AudioDeviceInfo
import com.qvoste.qtalk.voip.AudioSettings
import com.qvoste.qtalk.voip.RingtoneInfo
import com.qvoste.qtalk.voip.RegistrationState
import com.qvoste.qtalk.voip.SipAccount
import com.qvoste.qtalk.voip.SipTransport
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.icon_calls
import qtalk.shared.generated.resources.icon_history_missed
import qtalk.shared.generated.resources.icon_history_outgoing
import qtalk.shared.generated.resources.icon_history_incoming
import qtalk.shared.generated.resources.icon_contacts
import qtalk.shared.generated.resources.icon_notifications
import qtalk.shared.generated.resources.icon_backspace
import qtalk.shared.generated.resources.icon_call_hold
import qtalk.shared.generated.resources.icon_call_keypad
import qtalk.shared.generated.resources.icon_call_microphone
import qtalk.shared.generated.resources.icon_call_record
import qtalk.shared.generated.resources.icon_call_speaker
import qtalk.shared.generated.resources.icon_call_transfer
import qtalk.shared.generated.resources.icon_company
import qtalk.shared.generated.resources.icon_department
import qtalk.shared.generated.resources.icon_phone
import qtalk.shared.generated.resources.icon_position
import qtalk.shared.generated.resources.icon_search
import qtalk.shared.generated.resources.icon_settings
import qtalk.shared.generated.resources.icon_settings_about
import qtalk.shared.generated.resources.icon_settings_headphones
import qtalk.shared.generated.resources.icon_settings_logout
import qtalk.shared.generated.resources.icon_settings_microphone
import qtalk.shared.generated.resources.icon_settings_ringtone
import qtalk.shared.generated.resources.icon_settings_volume_low
import qtalk.shared.generated.resources.icon_settings_theme
import qtalk.shared.generated.resources.icon_telegram
import qtalk.shared.generated.resources.icon_add_contact
import qtalk.shared.generated.resources.icon_plus
import qtalk.shared.generated.resources.logo_white_transparent
import qtalk.shared.generated.resources.logo_light_transparent
import qtalk.shared.generated.resources.logo_black
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.components.transferOnRightClick
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.ui.calls.components.Sidebar
import com.qvoste.qtalk.ui.calls.components.Avatar
import com.qvoste.qtalk.ui.calls.components.ContactAvatar
import com.qvoste.qtalk.ui.calls.components.ContactAvatarWithStatus
import com.qvoste.qtalk.ui.calls.components.CircleIconAction
import com.qvoste.qtalk.ui.calls.components.CircleResourceAction
import com.qvoste.qtalk.ui.calls.components.EmptyMessage
import com.qvoste.qtalk.ui.calls.components.DialerPanel
import com.qvoste.qtalk.ui.calls.components.IncomingCallOverlay
import com.qvoste.qtalk.ui.calls.components.ActiveCallOverlay


@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    appVersion: String = "1.0.3",
    availableUpdateVersion: String? = null,
    onCheckForUpdates: suspend ((String, Float?) -> Unit) -> Unit = { it("Проверка обновлений недоступна", null) },
    callOverlayCollapsed: Boolean = false,
    onCollapseCall: () -> Unit = {},
    externalDialRequest: ExternalDialRequest? = null,
    onExternalDialRequestConsumed: (Long) -> Unit = {}
) {
    val registration by viewModel.registrationState.collectAsState()
    val registrationMessage by viewModel.registrationMessage.collectAsState()
    val call by viewModel.callStatus.collectAsState()
    val error by viewModel.error.collectAsState()
    val accountNumber by viewModel.accountNumber.collectAsState()
    val authenticationRequired by viewModel.authenticationRequired.collectAsState()
    val accountDraft by viewModel.accountDraft.collectAsState()
    val audioSettings by viewModel.audioSettings.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val contactNotes by viewModel.contactNotes.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()
    val contactDatabaseState by viewModel.contactDatabaseState.collectAsState()

    if (authenticationRequired) {
        SipLoginScreen(
            draft = accountDraft,
            registration = registration,
            registrationMessage = registrationMessage,
            error = error,
            onLogin = viewModel::connect
        )
        return
    }

    CallsContent(
        registration = registration,
        call = call,
        error = error,
        accountNumber = accountNumber,
        account = accountDraft,
        audioSettings = audioSettings,
        contacts = contacts,
        contactNotes = contactNotes,
        history = callHistory,
        contactDatabaseState = contactDatabaseState,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        appVersion = appVersion,
        availableUpdateVersion = availableUpdateVersion,
        onCheckForUpdates = onCheckForUpdates,
        onCall = viewModel::call,
        onAccept = viewModel::acceptCall,
        onDecline = viewModel::declineCall,
        onHangUp = viewModel::hangUp,
        onDtmf = viewModel::sendDtmf,
        onTransferEmployee = viewModel::transferToEmployee,
        onMicrophoneMutedChange = viewModel::setMicrophoneMuted,
        onSpeakerMutedChange = viewModel::setSpeakerMuted,
        callOverlayCollapsed = callOverlayCollapsed,
        onCollapseCall = onCollapseCall,
        externalDialRequest = externalDialRequest,
        onExternalDialRequestConsumed = onExternalDialRequestConsumed,
        onLogout = viewModel::logout,
        onRefreshAudioDevices = viewModel::refreshAudioDevices,
        onSelectInputAudioDevice = viewModel::selectInputAudioDevice,
        onSelectOutputAudioDevice = viewModel::selectOutputAudioDevice,
        onMicrophoneSensitivityChange = viewModel::setMicrophoneSensitivity,
        onSpeakerVolumeChange = viewModel::setSpeakerVolume,
        onRingtoneVolumeChange = viewModel::setRingtoneVolume,
        onPreviewRingtone = viewModel::previewRingtone,
        onSelectRingtone = viewModel::selectRingtone,
        onAddContact = viewModel::addContact,
        onUpdateContact = viewModel::updateContact,
        onDeleteContact = viewModel::deleteContact,
        onMissedCallsSeen = viewModel::markMissedCallsSeen,
        onAddContactNote = viewModel::addContactNote,
        onUpdateContactNote = viewModel::updateContactNote,
        onDeleteContactNote = viewModel::deleteContactNote,
        onSaveContactDatabaseUrl = viewModel::saveContactDatabaseUrl,
        onSyncContactDatabase = viewModel::syncContactDatabase,
        onDisconnectContactDatabase = viewModel::disconnectContactDatabase
    )
}

@Composable
private fun SipLoginScreen(
    draft: SipAccount?,
    registration: RegistrationState,
    registrationMessage: String,
    error: String?,
    onLogin: (SipAccount) -> Unit
) {
    var username by rememberSaveable(draft) { mutableStateOf(draft?.username.orEmpty()) }
    var domain by rememberSaveable(draft) { mutableStateOf(draft?.domain.orEmpty()) }
    var port by rememberSaveable(draft) { mutableStateOf((draft?.port ?: 5060).toString()) }
    var realm by rememberSaveable(draft) { mutableStateOf(draft?.realm ?: "asterisk") }
    var expires by rememberSaveable(draft) { mutableStateOf((draft?.registrationExpires ?: 3600).toString()) }
    var transport by rememberSaveable(draft) { mutableStateOf(draft?.transport ?: SipTransport.UDP) }
    var useHa1 by rememberSaveable(draft) { mutableStateOf(!draft?.ha1.isNullOrBlank()) }
    var secret by rememberSaveable(draft) { mutableStateOf(draft?.ha1.orEmpty()) }
    val connecting = registration == RegistrationState.CONNECTING
    val canLogin = username.isNotBlank() && domain.isNotBlank() && secret.isNotBlank() &&
        port.toIntOrNull() in 1..65535 && (expires.toIntOrNull() ?: 0) >= 60

    Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).background(ControlBackground, RoundedCornerShape(22.dp)).padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(if (IsLightTheme) Res.drawable.logo_light_transparent else Res.drawable.logo_white_transparent),
                contentDescription = "QTalk",
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Вход в QTalk", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(
                "Введите данные вашей внутренней SIP-линии",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            LoginField(username, { username = it.filter(Char::isDigit) }, "SIP-логин", connecting)
            Spacer(Modifier.height(12.dp))
            LoginField(domain, { domain = it.trim() }, "SIP-сервер", connecting)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoginField(port, { port = it.filter(Char::isDigit) }, "Порт", connecting, Modifier.weight(1f))
                LoginField(realm, { realm = it }, "Realm", connecting, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SipTransport.entries.forEach { option ->
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (transport == option) Primary else PanelBackground)
                            .clickable(enabled = !connecting) { transport = option },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option.name, color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LoginField(
                secret,
                { secret = it.trim() },
                if (useHa1) "HA1 (MD5)" else "Пароль",
                connecting,
                password = !useHa1
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !connecting) {
                    useHa1 = !useHa1
                    secret = ""
                }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(18.dp).border(1.dp, Primary, RoundedCornerShape(4.dp))
                        .background(if (useHa1) Primary else Color.Transparent, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (useHa1) Text("✓", color = TextPrimary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(9.dp))
                Text("Использовать готовый HA1 вместо пароля", color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            LoginField(expires, { expires = it.filter(Char::isDigit) }, "Срок регистрации, секунд", connecting)
            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    onLogin(
                        SipAccount(
                            username = username.trim(),
                            password = secret.takeUnless { useHa1 },
                            ha1 = secret.takeIf { useHa1 },
                            realm = realm.trim().ifBlank { null },
                            domain = domain.trim(),
                            port = port.toInt(),
                            transport = transport,
                            registrationExpires = expires.toInt()
                        )
                    )
                },
                enabled = canLogin && !connecting,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(if (connecting) "Подключение..." else "Войти")
            }

            if (registrationMessage.isNotBlank() || error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error ?: registrationMessage,
                    color = if (registration == RegistrationState.FAILED || error != null) Danger else TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    locked: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        enabled = !locked,
        visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = darkTextFieldColors()
    )
}

@Composable
private fun CallsContent(
    registration: RegistrationState,
    call: CallStatus,
    error: String?,
    accountNumber: String,
    account: SipAccount?,
    audioSettings: AudioSettings,
    contacts: List<Contact>,
    contactNotes: List<ContactNote>,
    history: List<HistoryItem>,
    contactDatabaseState: ContactDatabaseState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appVersion: String,
    availableUpdateVersion: String?,
    onCheckForUpdates: suspend ((String, Float?) -> Unit) -> Unit,
    onCall: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onDtmf: (Char) -> Unit,
    onTransferEmployee: (Contact) -> Unit,
    onMicrophoneMutedChange: (Boolean) -> Unit,
    onSpeakerMutedChange: (Boolean) -> Unit,
    callOverlayCollapsed: Boolean,
    onCollapseCall: () -> Unit,
    externalDialRequest: ExternalDialRequest?,
    onExternalDialRequestConsumed: (Long) -> Unit,
    onLogout: () -> Unit,
    onRefreshAudioDevices: () -> Unit,
    onSelectInputAudioDevice: (String) -> Unit,
    onSelectOutputAudioDevice: (String) -> Unit,
    onMicrophoneSensitivityChange: (Float) -> Unit,
    onSpeakerVolumeChange: (Float) -> Unit,
    onRingtoneVolumeChange: (Float) -> Unit,
    onPreviewRingtone: (String) -> Unit,
    onSelectRingtone: (String) -> Unit,
    onAddContact: (Contact) -> Unit,
    onUpdateContact: (Contact, Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onMissedCallsSeen: () -> Unit,
    onAddContactNote: (Contact, String) -> Unit,
    onUpdateContactNote: (ContactNote, String) -> Unit,
    onDeleteContactNote: (ContactNote) -> Unit,
    onSaveContactDatabaseUrl: (String) -> Unit,
    onSyncContactDatabase: () -> Unit,
    onDisconnectContactDatabase: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(AppSection.CALLS) }
    var number by rememberSaveable { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showAddContact by rememberSaveable { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var dialerTextInputFocused by remember { mutableStateOf(false) }
    var seenUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboardDialFocus = remember { FocusRequester() }
    val hasUnreadUpdate = availableUpdateVersion != null && availableUpdateVersion != seenUpdateVersion

    LaunchedEffect(Unit) { keyboardDialFocus.requestFocus() }
    LaunchedEffect(section, availableUpdateVersion) {
        if (section == AppSection.NOTIFICATIONS) {
            seenUpdateVersion = availableUpdateVersion
        }
    }
    LaunchedEffect(externalDialRequest?.id) {
        val request = externalDialRequest ?: return@LaunchedEffect
        section = AppSection.CALLS
        number = formatPhoneNumber(request.number)
        selectedContact = contacts.firstOrNull { contact ->
            contact.number.normalizedContactNumber() == request.number.normalizedContactNumber()
        }
        showAddContact = false
        editingContact = null
        onExternalDialRequestConsumed(request.id)
    }
    fun startCall(target: String) {
        val cleanNumber = target.trim()
        if (cleanNumber.isBlank()) return
        onCall(cleanNumber)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(AppBackground)
            .focusRequester(keyboardDialFocus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || searchFocused || dialerTextInputFocused ||
                    call.state.isInProgress ||
                    showAddContact || editingContact != null
                ) return@onKeyEvent false

                when {
                    event.key == Key.Backspace && number.isNotEmpty() -> {
                        number = number.dropLast(1)
                        selectedContact = contacts.firstOrNull { contact ->
                            contact.number.normalizedContactNumber() == number.normalizedContactNumber()
                        }
                        true
                    }
                    event.utf16CodePoint > 0 -> {
                        val character = event.utf16CodePoint.toChar()
                        if (character.isDigit() || character == '*' || character == '#' || character == '+') {
                            number += character
                            selectedContact = contacts.firstOrNull { contact ->
                                contact.number.normalizedContactNumber() == number.normalizedContactNumber()
                            }
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        // На половине экрана освобождаем место центральной колонке.
        // Windows пересчитывает половину экрана с учётом системного DPI.
        val compact = maxWidth < 1100.dp
        // При любом звонке основной интерфейс остаётся виден через размытие.
        val showCallOverlay = (call.state == CallState.INCOMING && !callOverlayCollapsed) ||
            (call.state == CallState.ACTIVE && !callOverlayCollapsed)
        Row(Modifier.fillMaxSize().then(if (showCallOverlay) Modifier.blur(8.dp) else Modifier)) {
            Sidebar(
                selected = section,
                registration = registration,
                compact = compact,
                accountContact = contacts.firstOrNull { it.number == accountNumber },
                hasNotifications = hasUnreadUpdate,
                onSelect = {
                    searchFocused = false
                    section = it
                }
            )

            // Центральная часть зависит от выбранного раздела.
            when (section) {
                AppSection.CALLS -> CallsHome(
                    modifier = Modifier.weight(1f),
                    compact = compact,
                    contacts = contacts,
                    history = history,
                    onAddContact = {
                        editingContact = null
                        showAddContact = true
                    },
                    onShowAllContacts = { section = AppSection.CONTACTS },
                    onSelectContact = {
                        selectedContact = it
                        number = formatPhoneNumber(it.number)
                    },
                    onSelectHistory = { item ->
                        val normalizedNumber = item.number.normalizedContactNumber()
                        selectedContact = contacts.firstOrNull { contact ->
                            normalizedNumber.isNotEmpty() &&
                                contact.number.normalizedContactNumber() == normalizedNumber
                        }
                        number = formatPhoneNumber(item.number)
                        showAddContact = false
                        editingContact = null
                    },
                    onQuickCall = {
                        number = formatPhoneNumber(it)
                        if (registration == RegistrationState.REGISTERED && !call.state.isInProgress) {
                            startCall(it)
                        }
                    },
                    onMissedCallsSeen = onMissedCallsSeen,
                    canTransfer = call.state == CallState.ACTIVE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it }
                )
                AppSection.CONTACTS -> ContactsSection(
                    modifier = Modifier.weight(1f),
                    compact = compact,
                    contacts = contacts,
                    onAddContact = {
                        editingContact = null
                        showAddContact = true
                    },
                    onSelectContact = {
                        selectedContact = it
                        number = formatPhoneNumber(it.number)
                    },
                    onCallContact = {
                        number = formatPhoneNumber(it.number)
                        selectedContact = it
                        if (registration == RegistrationState.REGISTERED && !call.state.isInProgress) {
                            startCall(it.number)
                        }
                    },
                    onEditContact = {
                        showAddContact = false
                        editingContact = it
                    },
                    canTransfer = call.state == CallState.ACTIVE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it }
                )
                AppSection.SETTINGS -> SipSettings(
                    modifier = Modifier.weight(1f),
                    compact = compact,
                    contacts = contacts,
                    account = account,
                    audioSettings = audioSettings,
                    registration = registration,
                    onLogout = onLogout,
                    onRefreshAudioDevices = onRefreshAudioDevices,
                    onSelectInputAudioDevice = onSelectInputAudioDevice,
                    onSelectOutputAudioDevice = onSelectOutputAudioDevice,
                    onMicrophoneSensitivityChange = onMicrophoneSensitivityChange,
                    onSpeakerVolumeChange = onSpeakerVolumeChange,
                    onRingtoneVolumeChange = onRingtoneVolumeChange,
                    onPreviewRingtone = onPreviewRingtone,
                    onSelectRingtone = onSelectRingtone,
                    contactDatabaseState = contactDatabaseState,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    appVersion = appVersion,
                    onCheckForUpdates = onCheckForUpdates,
                    onSaveContactDatabaseUrl = onSaveContactDatabaseUrl,
                    onSyncContactDatabase = onSyncContactDatabase,
                    onDisconnectContactDatabase = onDisconnectContactDatabase,
                    onAddContact = {
                        section = AppSection.CONTACTS
                        editingContact = null
                        showAddContact = true
                    },
                    onShowAllContacts = { section = AppSection.CONTACTS },
                    onSelectContact = {
                        selectedContact = it
                        number = formatPhoneNumber(it.number)
                    },
                    canTransfer = call.state == CallState.ACTIVE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it }
                )
                AppSection.NOTIFICATIONS -> NotificationsSection(
                    modifier = Modifier.weight(1f),
                    compact = compact,
                    contacts = contacts,
                    onAddContact = {
                        section = AppSection.CONTACTS
                        editingContact = null
                        showAddContact = true
                    },
                    onShowAllContacts = { section = AppSection.CONTACTS },
                    onSelectContact = {
                        selectedContact = it
                        number = formatPhoneNumber(it.number)
                    },
                    canTransfer = call.state == CallState.ACTIVE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it },
                    availableUpdateVersion = availableUpdateVersion,
                    onOpenUpdate = { section = AppSection.SETTINGS }
                )
            }

            run {
                val contactBeingEdited = editingContact
                val deletableContact = if (showAddContact) null else contactBeingEdited ?: selectedContact
                DialerPanel(
                    number = number,
                    contact = selectedContact,
                    contactBeingEdited = contactBeingEdited,
                    creatingContact = showAddContact,
                    accountNumber = accountNumber,
                    registration = registration,
                    call = call,
                    error = error,
                    compact = compact,
                    contactHistory = selectedContact?.let { selected ->
                        history.filter { item ->
                            item.number.normalizedContactNumber() == selected.number.normalizedContactNumber()
                        }
                    }.orEmpty(),
                    contactNotes = selectedContact?.let { selected ->
                        contactNotes.filter { it.belongsTo(selected) }
                            .sortedByDescending { it.id.toLongOrNull() ?: 0L }
                    }.orEmpty(),
                    onNumberChange = {
                        number = it.filter { char ->
                            char.isDigit() || char == '*' || char == '#' || char == '+' || char == ' '
                        }
                        selectedContact = contacts.firstOrNull { contact ->
                            contact.number.normalizedContactNumber() == number.normalizedContactNumber()
                        }
                    },
                    onEditContact = {
                        showAddContact = false
                        editingContact = it
                    },
                    onToggleFavorite = { original ->
                        val updated = original.copy(favorite = !original.favorite)
                        onUpdateContact(original, updated)
                        selectedContact = updated
                    },
                    onCancelContactEdit = {
                        showAddContact = false
                        editingContact = null
                    },
                    onSaveContact = { updated ->
                        if (contactBeingEdited == null) onAddContact(updated)
                        else onUpdateContact(contactBeingEdited, updated)
                        selectedContact = updated
                        number = formatPhoneNumber(updated.number)
                        showAddContact = false
                        editingContact = null
                    },
                    onDeleteContact = deletableContact?.let { original ->
                        {
                            onDeleteContact(original)
                            if (selectedContact == original) {
                                selectedContact = null
                                number = ""
                            }
                            editingContact = null
                        }
                    },
                    onAddNote = { text -> selectedContact?.let { onAddContactNote(it, text) } },
                    onUpdateNote = onUpdateContactNote,
                    onDeleteNote = onDeleteContactNote,
                    onTextInputFocusChange = { dialerTextInputFocused = it },
                    onCall = { startCall(number) },
                    onHangUp = onHangUp,
                    onTransferEmployee = onTransferEmployee
                )
            }
        }

        if (call.state == CallState.INCOMING && !callOverlayCollapsed) {
            val caller = contacts.firstOrNull { it.matchesCallNumber(call.number) }
            CallsTheme(ThemeMode.DARK) {
                IncomingCallOverlay(
                    number = call.number.ifBlank { "Неизвестный номер" },
                    callerName = caller?.name,
                    callerTelegram = caller?.telegram.orEmpty(),
                    onAccept = onAccept,
                    onDecline = onDecline
                )
            }
        }


        if (call.state == CallState.ACTIVE && !callOverlayCollapsed) {
            val activeContact = contacts.firstOrNull { it.matchesCallNumber(call.number) }
            CallsTheme(ThemeMode.DARK) {
                ActiveCallOverlay(
                    number = call.number,
                    callerName = activeContact?.name,
                    callerTelegram = activeContact?.telegram.orEmpty(),
                    connectedAtEpochMillis = call.connectedAtEpochMillis,
                    onHangUp = onHangUp,
                    onCollapse = onCollapseCall,
                    onDtmf = onDtmf,
                    onMicrophoneMutedChange = onMicrophoneMutedChange,
                    onSpeakerMutedChange = onSpeakerMutedChange
                )
            }
        }

    }
}

@Composable
private fun NotificationsSection(
    modifier: Modifier,
    compact: Boolean,
    contacts: List<Contact>,
    onAddContact: () -> Unit,
    onShowAllContacts: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    availableUpdateVersion: String?,
    onOpenUpdate: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }

    Column(
        modifier.fillMaxHeight().padding(
            start = if (compact) 40.dp else 38.dp,
            top = 16.dp,
            end = if (compact) 13.dp else 18.dp,
            bottom = 20.dp
        )
    ) {
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange)
        Spacer(Modifier.height(35.dp))
        FavoritesSection(
            contacts = contacts,
            compact = compact,
            trailingText = "Все  ›",
            onTrailingClick = onShowAllContacts,
            onSelectContact = onSelectContact,
            onAddContact = onAddContact,
            canTransfer = canTransfer,
            onTransferEmployee = onTransferEmployee
        )
        Spacer(Modifier.height(if (compact) 28.dp else 32.dp))
        Text("Уведомления", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        if (availableUpdateVersion == null) {
            EmptyMessage("Новых уведомлений нет")
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(ControlBackground)
                    .clickable(onClick = onOpenUpdate)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).background(Primary.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(Res.drawable.icon_notifications),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(Primary)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Доступно обновление", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("QTalk $availableUpdateVersion готов к установке", color = TextSecondary, fontSize = 11.sp)
                }
                Text("Открыть  ›", color = Primary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CallsHome(
    modifier: Modifier,
    compact: Boolean,
    contacts: List<Contact>,
    history: List<HistoryItem>,
    onAddContact: () -> Unit,
    onShowAllContacts: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    onSelectHistory: (HistoryItem) -> Unit,
    onQuickCall: (String) -> Unit,
    onMissedCallsSeen: () -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CallFilter.ALL) }
    val unreadMissedCount = history.count { it.missed && !it.missedAcknowledged }
    LaunchedEffect(filter, unreadMissedCount) {
        if (filter == CallFilter.MISSED && unreadMissedCount > 0) {
            onMissedCallsSeen()
        }
    }
    val visibleHistory = history.filter {
        val matchesSearch = search.isBlank() || it.name.contains(search, ignoreCase = true) ||
            it.number.contains(search) || telegramMatchesQuery(it.telegram, search)
        val matchesFilter = when (filter) {
            CallFilter.ALL -> true
            CallFilter.MISSED -> it.missed
            CallFilter.OUTGOING -> it.direction == "Исходящий"
            CallFilter.INCOMING -> it.direction == "Входящий"
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier.fillMaxHeight().padding(
            start = if (compact) 40.dp else 38.dp,
            top = 16.dp,
            end = if (compact) 13.dp else 18.dp,
            bottom = 20.dp
        )
    ) {
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange)
        Spacer(Modifier.height(35.dp))
        FavoritesSection(
            contacts = contacts,
            compact = compact,
            trailingText = "Все  ›",
            onTrailingClick = onShowAllContacts,
            onSelectContact = onSelectContact,
            onAddContact = onAddContact,
            canTransfer = canTransfer,
            onTransferEmployee = onTransferEmployee
        )

        Spacer(Modifier.height(if (compact) 28.dp else 32.dp))
        FilterBar(filter, false, unreadMissedCount) { filter = it }
        Spacer(Modifier.height(18.dp))

        if (visibleHistory.isEmpty()) {
            EmptyMessage(if (history.isEmpty()) "История звонков пока пуста" else "Ничего не найдено")
        } else {
            val now = Clock.System.now().toEpochMilliseconds()
            val groups = visibleHistory.groupBy { historyDateHeading(it.startedAtEpochMillis, now) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                groups.forEach { (heading, calls) ->
                    item {
                        Text(
                            heading,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    items(calls, key = { it.id }) { item ->
                        HistoryRow(item, onQuickCall, onSelectHistory)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(41.dp)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .background(ControlBackground, RoundedCornerShape(35.dp)),
        singleLine = true,
        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
        cursorBrush = SolidColor(Primary),
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(Res.drawable.icon_search),
                    contentDescription = "Поиск",
                    modifier = Modifier.size(19.dp),
                    colorFilter = if (IsLightTheme) ColorFilter.tint(TextSecondary) else null
                )
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Поиск контакта, номера или компании...",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun SearchAndAddBar(
    search: String,
    onSearchChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SearchField(search, onSearchChange, onSearchFocusChange) }
        Spacer(Modifier.width(14.dp))
        CircleResourceAction(Res.drawable.icon_add_contact, 40, 20, 16, ControlBackground, onAddContact)
    }
}

@Composable
private fun FavoriteItem(
    contact: Contact,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit,
    onClick: () -> Unit
) {
    val displayName = contact.name.trim().split(Regex("\\s+")).getOrNull(1)
        ?: contact.name.trim().substringBefore(' ')
    Column(
        modifier = Modifier.width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .transferOnRightClick(canTransfer && transferCodeFor(contact) != null) { onTransferEmployee(contact) }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContactAvatarWithStatus(contact.name, contact.telegram, 54)
        Spacer(Modifier.height(2.dp))
        Text(
            displayName,
            color = TextPrimary,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formatPhoneNumber(contact.number),
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun FavoritesSection(
    contacts: List<Contact>,
    compact: Boolean,
    trailingText: String,
    onTrailingClick: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    onAddContact: () -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Избранное", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            trailingText,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onTrailingClick)
        )
    }
    Spacer(Modifier.height(12.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = if (compact) 4.dp else 6.dp
        val visibleSlots = ((maxWidth + gap) / (104.dp + gap)).toInt().coerceAtLeast(1)
        val visibleFavorites = (visibleSlots - 1).coerceAtLeast(0)
        Row(
            modifier = Modifier.offset(x = (-25).dp),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            contacts.filter { it.favorite }.take(visibleFavorites).forEach { contact ->
                FavoriteItem(contact, canTransfer, onTransferEmployee) { onSelectContact(contact) }
            }
            Column(
                modifier = Modifier.width(104.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircleResourceAction(Res.drawable.icon_plus, 54, 14, 14, ControlBackground, onAddContact)
                Spacer(Modifier.height(2.dp))
                Text("Добавить", color = TextSecondary, fontSize = 12.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun FilterBar(selected: CallFilter, compact: Boolean, missedCount: Int, onSelect: (CallFilter) -> Unit) {
    SegmentedBar(
        options = CallFilter.entries.map {
            if (it == CallFilter.MISSED && missedCount > 0) "${it.title}  $missedCount" else it.title
        },
        selectedIndex = CallFilter.entries.indexOf(selected),
        onSelect = { onSelect(CallFilter.entries[it]) },
        fillWidth = compact,
        indicatorIndex = if (missedCount > 0) CallFilter.entries.indexOf(CallFilter.MISSED) else null
    )
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onCall: (String) -> Unit,
    onSelect: (HistoryItem) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val directionIcon = when {
        item.missed -> Res.drawable.icon_history_missed
        item.direction == "Исходящий" -> Res.drawable.icon_history_outgoing
        else -> Res.drawable.icon_history_incoming
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            Modifier.offset(x = (-8).dp).requiredWidth(maxWidth + 16.dp).fillMaxHeight()
                .clip(RoundedCornerShape(12.dp)).clickable { onSelect(item) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(item.name, item.telegram, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = TextPrimary, fontSize = 13.sp, lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(directionIcon), contentDescription = null, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (item.missed) "Пропущенный" else item.direction,
                        color = if (item.missed) Danger else TextSecondary,
                        fontSize = 11.sp, lineHeight = 13.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatHistoryTimestamp(item.startedAtEpochMillis, Clock.System.now().toEpochMilliseconds()).takeLast(5),
                    color = TextSecondary, fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.End)
                Text(formatCallDuration(item.durationSeconds).ifBlank { "—" },
                    color = TextSecondary, fontSize = 10.sp, lineHeight = 12.sp,
                    textAlign = TextAlign.End)
            }
            Spacer(Modifier.width(14.dp))
            CircleIconAction(Res.drawable.icon_calls, 34, ControlBackground) { onCall(item.number) }
            Spacer(Modifier.width(10.dp))
            Box {
                Text("⋮", color = TextSecondary, fontSize = 20.sp,
                    modifier = Modifier.clickable { menuExpanded = true }.padding(8.dp))
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Позвонить") }, onClick = {
                        menuExpanded = false
                        onCall(item.number)
                    })
                    DropdownMenuItem(text = { Text("Копировать номер") }, onClick = {
                        menuExpanded = false
                        clipboard.setText(AnnotatedString(item.number))
                    })
                }
            }
        }
    }
}

@Composable
private fun SipSettings(
    modifier: Modifier,
    compact: Boolean,
    contacts: List<Contact>,
    account: SipAccount?,
    audioSettings: AudioSettings,
    registration: RegistrationState,
    onLogout: () -> Unit,
    onRefreshAudioDevices: () -> Unit,
    onSelectInputAudioDevice: (String) -> Unit,
    onSelectOutputAudioDevice: (String) -> Unit,
    onMicrophoneSensitivityChange: (Float) -> Unit,
    onSpeakerVolumeChange: (Float) -> Unit,
    onRingtoneVolumeChange: (Float) -> Unit,
    onPreviewRingtone: (String) -> Unit,
    onSelectRingtone: (String) -> Unit,
    contactDatabaseState: ContactDatabaseState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appVersion: String,
    onCheckForUpdates: suspend ((String, Float?) -> Unit) -> Unit,
    onSaveContactDatabaseUrl: (String) -> Unit,
    onSyncContactDatabase: () -> Unit,
    onDisconnectContactDatabase: () -> Unit,
    onAddContact: () -> Unit,
    onShowAllContacts: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit
) {
    var showAccount by remember { mutableStateOf(false) }
    var showInputDevices by remember { mutableStateOf(false) }
    var showOutputDevices by remember { mutableStateOf(false) }
    var showRingtones by remember { mutableStateOf(false) }
    var showDatabase by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableStateOf<Float?>(null) }
    var updating by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var databaseUrl by remember(contactDatabaseState.sourceUrl) {
        mutableStateOf(contactDatabaseState.sourceUrl)
    }
    var microphoneSensitivity by remember(audioSettings.microphoneSensitivity) {
        mutableStateOf(audioSettings.microphoneSensitivity)
    }
    var speakerVolume by remember(audioSettings.speakerVolume) {
        mutableStateOf(audioSettings.speakerVolume)
    }
    var ringtoneVolume by remember(audioSettings.ringtoneVolume) {
        mutableStateOf(audioSettings.ringtoneVolume)
    }
    val inputName = audioSettings.inputDevices
        .firstOrNull { it.id == audioSettings.selectedInputDeviceId }?.name ?: "Системный"
    val outputName = audioSettings.outputDevices
        .firstOrNull { it.id == audioSettings.selectedOutputDeviceId }?.name ?: "Системный"
    val ringtoneName = audioSettings.ringtones
        .firstOrNull { it.id == audioSettings.selectedRingtoneId }?.name ?: "Вариант - 1"

    LaunchedEffect(Unit) { onRefreshAudioDevices() }

    Column(
        modifier.fillMaxHeight().padding(
            start = if (compact) 40.dp else 38.dp,
            top = 16.dp,
            end = if (compact) 13.dp else 18.dp,
            bottom = 20.dp
        )
    ) {
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange)
        Spacer(Modifier.height(35.dp))
        FavoritesSection(
            contacts = contacts,
            compact = compact,
            trailingText = "Все  ›",
            onTrailingClick = onShowAllContacts,
            onSelectContact = onSelectContact,
            onAddContact = onAddContact,
            canTransfer = canTransfer,
            onTransferEmployee = onTransferEmployee
        )
        Spacer(Modifier.height(if (compact) 28.dp else 32.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
        ) {
        item {
            SettingsSectionTitle("Аккаунт", "Профиль и безопасность")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showAccount = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).background(Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        account?.username?.take(2).orEmpty().ifBlank { "QT" },
                        color = OnPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Аккаунт ${account?.username.orEmpty()}", color = TextPrimary, fontSize = 13.sp)
                    Text(
                        buildString {
                            append(account?.domain ?: "SIP-аккаунт")
                            append("  •  ")
                            append(registrationText(registration))
                        },
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Text("›", color = TextSecondary, fontSize = 24.sp)
            }

            Spacer(Modifier.height(30.dp))
            SettingsSectionTitle("Звонки", "Звук и устройства")
            Spacer(Modifier.height(12.dp))
            SettingsActionRow(
                icon = Res.drawable.icon_settings_microphone,
                title = "Микрофон",
                subtitle = "Выбор устройства и настройка",
                value = inputName,
                onClick = { showInputDevices = true }
            )
            SettingsSliderRow(
                label = "Громкость микрофона",
                value = microphoneSensitivity,
                onValueChange = { microphoneSensitivity = it },
                onValueChangeFinished = { onMicrophoneSensitivityChange(microphoneSensitivity) }
            )
            Spacer(Modifier.height(20.dp))
            SettingsActionRow(
                icon = Res.drawable.icon_settings_headphones,
                title = "Наушники",
                subtitle = "Выбор устройства и настройка",
                value = outputName,
                onClick = { showOutputDevices = true }
            )
            SettingsSliderRow(
                label = "Громкость динамика",
                value = speakerVolume,
                onValueChange = { speakerVolume = it },
                onValueChangeFinished = { onSpeakerVolumeChange(speakerVolume) }
            )
            Spacer(Modifier.height(20.dp))
            SettingsSliderRow(
                label = "Громкость рингтона",
                value = ringtoneVolume,
                onValueChange = { ringtoneVolume = it },
                onValueChangeFinished = { onRingtoneVolumeChange(ringtoneVolume) }
            )
            SettingsActionRow(
                icon = Res.drawable.icon_settings_ringtone,
                title = "Рингтон",
                subtitle = "Мелодия входящего звонка",
                value = ringtoneName,
                onClick = { showRingtones = true }
            )

            Spacer(Modifier.height(30.dp))
            SettingsSectionTitle("Система", "Параметры приложения")
            Spacer(Modifier.height(10.dp))
            SettingsActionRow(
                icon = Res.drawable.icon_settings_theme,
                title = "Тема оформления",
                subtitle = "Цвет интерфейса",
                value = if (themeMode == ThemeMode.LIGHT) "Светлая" else "Тёмная",
                onClick = { onThemeModeChange(if (themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT) }
            )
            SettingsActionRow(
                icon = Res.drawable.icon_contacts,
                title = "База данных",
                subtitle = contactDatabaseState.message,
                onClick = { showDatabase = true }
            )
            SettingsActionRow(
                icon = Res.drawable.icon_settings_about,
                title = "О приложении",
                subtitle = "Версия, лицензия, правовая информация",
                onClick = { showAbout = true }
            )
            SettingsActionRow(
                icon = Res.drawable.icon_settings_logout,
                title = "Выйти из аккаунта",
                titleColor = Danger,
                onClick = onLogout
            )
        }
    }
    }

    if (showAccount) {
        AlertDialog(
            onDismissRequest = { showAccount = false },
            containerColor = ControlBackground,
            title = { Text("Настройка аккаунта", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsDetail("SIP-логин", account?.username.orEmpty())
                    SettingsDetail("SIP-сервер", account?.domain.orEmpty())
                    SettingsDetail("Порт", account?.port?.toString().orEmpty())
                    SettingsDetail("Транспорт", account?.transport?.name.orEmpty())
                    SettingsDetail("Realm", account?.realm ?: "—")
                }
            },
            confirmButton = {
                TextButton(onClick = onLogout) { Text("Сменить аккаунт", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showAccount = false }) { Text("Закрыть", color = TextPrimary) }
            }
        )
    }
    if (showInputDevices) {
        AudioDeviceDialog(
            title = "Выберите микрофон",
            devices = audioSettings.inputDevices,
            selectedDeviceId = audioSettings.selectedInputDeviceId,
            onSelect = {
                onSelectInputAudioDevice(it)
                showInputDevices = false
            },
            onDismiss = { showInputDevices = false }
        )
    }
    if (showOutputDevices) {
        AudioDeviceDialog(
            title = "Выберите динамики",
            devices = audioSettings.outputDevices,
            selectedDeviceId = audioSettings.selectedOutputDeviceId,
            onSelect = {
                onSelectOutputAudioDevice(it)
                showOutputDevices = false
            },
            onDismiss = { showOutputDevices = false }
        )
    }
    if (showRingtones) {
        RingtoneDialog(
            ringtones = audioSettings.ringtones,
            selectedRingtoneId = audioSettings.selectedRingtoneId,
            onPreview = onPreviewRingtone,
            onSave = {
                onSelectRingtone(it)
                showRingtones = false
            },
            onDismiss = { showRingtones = false }
        )
    }
    if (showDatabase) {
        AlertDialog(
            onDismissRequest = { showDatabase = false },
            containerColor = ControlBackground,
            title = { Text("База данных", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Подключите Google Таблицу с контактами. Таблица должна быть доступна всем, у кого есть ссылка.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = databaseUrl,
                        onValueChange = { databaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ссылка на Google Таблицу") },
                        placeholder = { Text("https://docs.google.com/spreadsheets/d/…") },
                        singleLine = true,
                        enabled = !contactDatabaseState.syncing,
                        colors = darkTextFieldColors()
                    )
                    Text(
                        "Контакты обновляются при запуске и каждые 15 минут. E-mail из таблицы не импортируется.",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        contactDatabaseState.message,
                        color = when (contactDatabaseState.lastSyncSuccessful) {
                            true -> Online
                            false -> Danger
                            null -> TextSecondary
                        },
                        fontSize = 11.sp
                    )
                    if (contactDatabaseState.sourceUrl.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onSyncContactDatabase,
                                enabled = !contactDatabaseState.syncing
                            ) {
                                Text("Обновить сейчас", color = Primary)
                            }
                            TextButton(
                                onClick = {
                                    onDisconnectContactDatabase()
                                    databaseUrl = ""
                                },
                                enabled = !contactDatabaseState.syncing
                            ) {
                                Text("Отключить", color = Danger)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSaveContactDatabaseUrl(databaseUrl) },
                    enabled = databaseUrl.isNotBlank() && !contactDatabaseState.syncing
                ) {
                    Text("Сохранить и загрузить", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatabase = false }) {
                    Text("Закрыть", color = TextPrimary)
                }
            }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = ControlBackground,
            icon = {
                Image(
                    painterResource(if (IsLightTheme) Res.drawable.logo_light_transparent else Res.drawable.logo_white_transparent),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("QTalk", color = TextPrimary) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Версия $appVersion\nКорпоративный SIP-клиент для звонков и работы с контактами.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    updateMessage?.let { message ->
                        Spacer(Modifier.height(12.dp))
                        Text(message, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                    updateProgress?.let { progress ->
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = Primary,
                            trackColor = TextSecondary.copy(alpha = 0.18f)
                        )
                        Spacer(Modifier.height(5.dp))
                        Text("${(progress * 100).toInt()}%", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("Готово", color = TextPrimary) }
            },
            dismissButton = {
                TextButton(
                    enabled = !updating,
                    onClick = {
                        updateScope.launch {
                            updating = true
                            try {
                                onCheckForUpdates { message, progress ->
                                    updateMessage = message
                                    updateProgress = progress
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                updateMessage = "Ошибка обновления: ${error.message ?: "проверьте подключение"}"
                                updateProgress = null
                            } finally {
                                updating = false
                            }
                        }
                    }
                ) { Text("Обновить", color = Primary) }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Text(title, color = TextPrimary, fontSize = 17.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
    Text(subtitle, color = TextSecondary, fontSize = 11.sp, lineHeight = 11.sp)
}

@Composable
private fun SettingsActionRow(
    icon: DrawableResource,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(
            Modifier.offset(x = (-10).dp).requiredWidth(maxWidth + 20.dp)
                .clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp),
                    colorFilter = if (IsLightTheme) ColorFilter.tint(TextSecondary) else null)
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, color = TextSecondary, fontSize = 10.sp, lineHeight = 10.sp) }
            }
            value?.let {
                Text(
                    it,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            if (subtitle != null) Text("›", color = TextSecondary, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val activeTrackColor = Primary
    val inactiveTrackColor = ControlBackground
    Column(Modifier.widthIn(max = 410.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
                Image(
                    painterResource(
                        if (value < 0.5f) Res.drawable.icon_settings_volume_low
                        else Res.drawable.icon_settings_ringtone
                    ),
                    contentDescription = null,
                    modifier = Modifier.width(if (value < 0.5f) 18.dp else 22.dp).height(17.dp),
                    colorFilter = ColorFilter.tint(TextPrimary)
                )
            }
            Spacer(Modifier.width(9.dp))
            Box(Modifier.width(330.dp).height(24.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    val centerY = size.height / 2f
                    val progress = value.coerceIn(0f, 1f)
                    drawLine(
                        color = inactiveTrackColor,
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = activeTrackColor,
                        start = Offset(0f, centerY),
                        end = Offset(size.width * progress, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = activeTrackColor,
                        radius = 4.dp.toPx(),
                        center = Offset(size.width * progress, centerY)
                    )
                }
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.fillMaxSize().alpha(0f),
                    thumb = { Box(Modifier.size(8.dp)) },
                    track = { Box(Modifier.fillMaxWidth().height(2.dp)) }
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${(value * 100).toInt()}%",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.width(28.dp)
            )
        }
        Text(label, color = TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(start = 31.dp))
    }
}

@Composable
private fun SettingsDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "—" }, color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun AudioDeviceDialog(
    title: String,
    devices: List<AudioDeviceInfo>,
    selectedDeviceId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ControlBackground,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (devices.isEmpty()) {
                    Text("Доступные устройства не найдены", color = TextSecondary, fontSize = 12.sp)
                } else {
                    devices.forEach { device ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (device.id == selectedDeviceId) Primary.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { onSelect(device.id) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(device.name, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (device.id == selectedDeviceId) Text("✓", color = Primary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextPrimary) }
        }
    )
}

@Composable
private fun RingtoneDialog(
    ringtones: List<RingtoneInfo>,
    selectedRingtoneId: String,
    onPreview: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingRingtoneId by remember(selectedRingtoneId) { mutableStateOf(selectedRingtoneId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ControlBackground,
        title = { Text("Выберите рингтон", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ringtones.forEach { ringtone ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(
                                if (ringtone.id == pendingRingtoneId) Primary.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .clickable {
                                pendingRingtoneId = ringtone.id
                                onPreview(ringtone.id)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ringtone.name, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (ringtone.id == pendingRingtoneId) Text("✓", color = Primary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pendingRingtoneId) }) { Text("Сохранить", color = Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextPrimary) }
        }
    )
}

@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(430.dp),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = darkTextFieldColors()
    )
}

@Composable
private fun ContactsSection(
    modifier: Modifier,
    compact: Boolean,
    contacts: List<Contact>,
    onAddContact: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    onCallContact: (Contact) -> Unit,
    onEditContact: (Contact) -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ContactFilter.ALL) }
    val visibleContacts = contacts.filter {
        val matchesSearch = search.isBlank() || it.matchesSearch(search)
        val matchesType = when (filter) {
            ContactFilter.ALL -> true
            ContactFilter.CLIENTS -> !it.isEmployee
            ContactFilter.EMPLOYEES -> it.isEmployee
        }
        matchesSearch && matchesType
    }.sortedBy { it.name.lowercase() }
    val groups = visibleContacts.groupBy { it.name.trim().firstOrNull()?.uppercase() ?: "#" }

    Column(
        modifier.fillMaxHeight().padding(
            start = if (compact) 40.dp else 38.dp,
            top = 16.dp,
            end = if (compact) 13.dp else 18.dp,
            bottom = 20.dp
        )
    ) {
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange)

        Spacer(Modifier.height(35.dp))
        FavoritesSection(
            contacts = contacts,
            compact = compact,
            trailingText = "${contacts.count { it.favorite }} контактов",
            onTrailingClick = {},
            onSelectContact = onSelectContact,
            onAddContact = onAddContact,
            canTransfer = canTransfer,
            onTransferEmployee = onTransferEmployee
        )

        Spacer(Modifier.height(32.dp))
        ContactFilterBar(
            selected = filter,
            allCount = contacts.size,
            clientsCount = contacts.count { !it.isEmployee },
            employeesCount = contacts.count { it.isEmployee },
            onSelect = { filter = it }
        )
        Spacer(Modifier.height(18.dp))

        if (visibleContacts.isEmpty()) {
            EmptyMessage(if (contacts.isEmpty()) "Контактов пока нет" else "Ничего не найдено")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                groups.forEach { (letter, group) ->
                    item {
                        Text(
                            letter,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    items(group) { contact ->
                        ContactRow(contact, onSelectContact, onCallContact, onEditContact,
                            canTransfer, onTransferEmployee)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactFilterBar(
    selected: ContactFilter,
    allCount: Int,
    clientsCount: Int,
    employeesCount: Int,
    onSelect: (ContactFilter) -> Unit
) {
    val options = ContactFilter.entries.map { filter ->
        val count = when (filter) {
            ContactFilter.ALL -> allCount
            ContactFilter.CLIENTS -> clientsCount
            ContactFilter.EMPLOYEES -> employeesCount
        }
        "${filter.title}  $count"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        val filterLineColor = TextSecondary
        val filterBackground = ControlBackground
        SegmentedBar(
            options = options,
            selectedIndex = ContactFilter.entries.indexOf(selected),
            onSelect = { onSelect(ContactFilter.entries[it]) }
        )
        Box(
            Modifier.size(40.dp).background(ControlBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(18.dp)) {
                val lineColor = filterLineColor
                val stroke = 1.5.dp.toPx()
                drawLine(lineColor, Offset(1.dp.toPx(), 4.dp.toPx()), Offset(17.dp.toPx(), 4.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(lineColor, Offset(1.dp.toPx(), 9.dp.toPx()), Offset(17.dp.toPx(), 9.dp.toPx()), stroke, StrokeCap.Round)
                drawLine(lineColor, Offset(1.dp.toPx(), 14.dp.toPx()), Offset(17.dp.toPx(), 14.dp.toPx()), stroke, StrokeCap.Round)
                drawCircle(filterBackground, 2.5.dp.toPx(), Offset(6.dp.toPx(), 4.dp.toPx()))
                drawCircle(lineColor, 1.5.dp.toPx(), Offset(6.dp.toPx(), 4.dp.toPx()))
                drawCircle(filterBackground, 2.5.dp.toPx(), Offset(12.dp.toPx(), 9.dp.toPx()))
                drawCircle(lineColor, 1.5.dp.toPx(), Offset(12.dp.toPx(), 9.dp.toPx()))
                drawCircle(filterBackground, 2.5.dp.toPx(), Offset(8.dp.toPx(), 14.dp.toPx()))
                drawCircle(lineColor, 1.5.dp.toPx(), Offset(8.dp.toPx(), 14.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SegmentedBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fillWidth: Boolean = false,
    indicatorIndex: Int? = null
) {
    Row(
        Modifier.then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .background(ControlBackground, RoundedCornerShape(22.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            val segmentColor by animateColorAsState(
                if (selected) Primary else Color.Transparent,
                tween(160)
            )
            Box(
                Modifier.then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(18.dp))
                    .background(segmentColor)
                    .clickable { onSelect(index) }
                    .padding(horizontal = if (fillWidth) 5.dp else 22.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (indicatorIndex == index) {
                        Box(Modifier.size(5.dp).background(Danger, CircleShape))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        title,
                    color = if (selected) OnPrimary else TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onSelect: (Contact) -> Unit,
    onCall: (Contact) -> Unit,
    onEdit: (Contact) -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp)) {
        Row(
            Modifier.offset(x = (-8).dp).requiredWidth(maxWidth + 16.dp).fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .transferOnRightClick(canTransfer && transferCodeFor(contact) != null) { onTransferEmployee(contact) }
                .clickable { onSelect(contact) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(contact.name, contact.telegram, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    contact.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${if (contact.isEmployee) "Сотрудник" else "Клиент"}  •  " +
                        contact.position.ifBlank { contact.company.ifBlank { formatPhoneNumber(contact.number) } },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 12.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            CircleIconAction(Res.drawable.icon_calls, 34, ControlBackground) { onCall(contact) }
            Spacer(Modifier.width(10.dp))
            Text(
                "⋮",
                color = TextSecondary,
                fontSize = 20.sp,
                modifier = Modifier.clickable { onEdit(contact) }.padding(8.dp)
            )
        }
    }
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextSecondary,
    focusedBorderColor = Primary,
    unfocusedBorderColor = DividerColor,
    disabledBorderColor = DividerColor,
    focusedContainerColor = ControlBackground,
    unfocusedContainerColor = ControlBackground,
    disabledContainerColor = ControlBackground,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextSecondary
)

internal fun sectionIcon(section: AppSection): DrawableResource = when (section) {
    AppSection.CALLS -> Res.drawable.icon_calls
    AppSection.CONTACTS -> Res.drawable.icon_contacts
    AppSection.SETTINGS -> Res.drawable.icon_settings
    AppSection.NOTIFICATIONS -> Res.drawable.icon_notifications
}

internal fun registrationText(state: RegistrationState) = when (state) {
    RegistrationState.DISCONNECTED -> "Не подключено"
    RegistrationState.CONNECTING -> "Подключение"
    RegistrationState.REGISTERED -> "В сети"
    RegistrationState.FAILED -> "Ошибка подключения"
}

private fun callStateText(call: CallStatus, registration: RegistrationState) = when (call.state) {
    CallState.IDLE -> if (registration == RegistrationState.REGISTERED) "Готов к звонку" else "Подключите SIP в настройках"
    CallState.INCOMING -> "Входящий звонок"
    CallState.DIALING -> "Набор ${formatPhoneNumber(call.number)}..."
    CallState.RINGING -> "Ожидание ответа ${formatPhoneNumber(call.number)}..."
    CallState.ACTIVE -> "Разговор с ${formatPhoneNumber(call.number)}"
    CallState.ENDING -> "Завершение звонка..."
    CallState.ENDED -> "Звонок завершён"
    CallState.FAILED -> "Не удалось позвонить: ${call.message}"
}
