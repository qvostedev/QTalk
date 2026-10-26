package com.qvoste.qtalk.ui.calls
import com.qvoste.qtalk.ui.calls.components.customRingtones
import com.qvoste.qtalk.ui.calls.components.importLocalMedia

import com.qvoste.qtalk.ui.calls.i18n.AppLanguage
import com.qvoste.qtalk.ui.calls.i18n.LocalAppLanguage
import com.qvoste.qtalk.ui.calls.i18n.uiText
import qtalk.shared.generated.resources.icon_settings_language

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import com.qvoste.qtalk.ui.calls.components.QTalkAlertDialog as AlertDialog
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
import androidx.compose.ui.input.key.isCtrlPressed
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
import com.qvoste.qtalk.voip.TelephonyConnection
import com.qvoste.qtalk.voip.ConferenceStatus
import com.qvoste.qtalk.voip.ConferencePhase
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
import qtalk.shared.generated.resources.icon_settings_database
import qtalk.shared.generated.resources.icon_settings_headphones
import qtalk.shared.generated.resources.icon_settings_logout
import qtalk.shared.generated.resources.icon_settings_microphone
import qtalk.shared.generated.resources.icon_settings_ringtone
import qtalk.shared.generated.resources.icon_settings_volume_high
import qtalk.shared.generated.resources.icon_settings_volume_low
import qtalk.shared.generated.resources.icon_settings_theme
import qtalk.shared.generated.resources.icon_settings_scale
import qtalk.shared.generated.resources.icon_telegram
import qtalk.shared.generated.resources.icon_add_contact
import qtalk.shared.generated.resources.icon_plus
import qtalk.shared.generated.resources.logo_white_transparent
import qtalk.shared.generated.resources.logo_light_transparent
import qtalk.shared.generated.resources.logo_black
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.components.onSecondaryClick
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.ui.calls.components.Sidebar
import com.qvoste.qtalk.ui.calls.components.Avatar
import com.qvoste.qtalk.ui.calls.components.AvatarWithStatus
import com.qvoste.qtalk.ui.calls.components.ContactAvatar
import com.qvoste.qtalk.ui.calls.components.ContactAvatarWithStatus
import com.qvoste.qtalk.ui.calls.components.CircleIconAction
import com.qvoste.qtalk.ui.calls.components.CircleResourceAction
import com.qvoste.qtalk.ui.calls.components.EmptyMessage
import com.qvoste.qtalk.ui.calls.components.DialerPanel
import com.qvoste.qtalk.ui.calls.components.IncomingCallOverlay
import com.qvoste.qtalk.ui.calls.components.ActiveCallOverlay
import com.qvoste.qtalk.ui.calls.components.CallOverlayDialog
import com.qvoste.qtalk.ui.calls.components.CallOverlayBlur


@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    appVersion: String = "1.1.0",
    availableUpdateVersion: String? = null,
    availableUpdateDescription: String? = null,
    onCheckForUpdates: suspend ((String, Float?) -> Unit) -> Unit = { it("Проверка обновлений недоступна", null) },
    callOverlayCollapsed: Boolean = false,
    onCollapseCall: () -> Unit = {},
    externalDialRequest: ExternalDialRequest? = null,
    onExternalDialRequestConsumed: (Long) -> Unit = {}
) {
    val registration by viewModel.registrationState.collectAsState()
    val registrationMessage by viewModel.registrationMessage.collectAsState()
    val connection by viewModel.connectionStatus.collectAsState()
    val call by viewModel.callStatus.collectAsState()
    val conference by viewModel.conferenceStatus.collectAsState()
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
        conference = conference,
        onRemoveParticipant = viewModel::removeConferenceParticipant,
        onInviteConference = viewModel::inviteConferenceParticipants,
        onStartConference = viewModel::startConference,
        onMergeConference = viewModel::mergeConference,
        onCancelConference = viewModel::cancelConferenceInvite,
        connection = connection,
        onRetryConnection = viewModel::retryRegistration,
        onEditSipAccount = viewModel::editSipAccount,
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
        availableUpdateDescription = availableUpdateDescription,
        onCheckForUpdates = onCheckForUpdates,
        onCall = viewModel::call,
        onAccept = viewModel::acceptCall,
        onDecline = viewModel::declineCall,
        onHangUp = viewModel::hangUp,
        onDtmf = viewModel::sendDtmf,
        onTransferEmployee = viewModel::transferToEmployee,
        onMicrophoneMutedChange = viewModel::setMicrophoneMuted,
        onConferenceHeldChange = viewModel::setConferenceHeld,
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
            Text(uiText("Вход в QTalk"), color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Text(
                uiText("Введите данные вашей внутренней SIP-линии"),
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
                Text(uiText("Использовать готовый HA1 вместо пароля"), color = TextSecondary, fontSize = 11.sp)
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
                Text(uiText(if (connecting) "Подключение..." else "Войти"))
            }

            if (registrationMessage.isNotBlank() || error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    uiText(error ?: registrationMessage),
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
        label = { Text(uiText(label)) },
        singleLine = true,
        enabled = !locked,
        visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = darkTextFieldColors()
    )
}

@Composable
private fun CallsContent(
    conference: ConferenceStatus,
    onRemoveParticipant: (String) -> Unit,
    onInviteConference: (List<String>) -> Unit,
    onStartConference: (List<String>) -> Unit,
    onMergeConference: () -> Unit,
    onCancelConference: () -> Unit,
    connection: TelephonyConnection,
    onRetryConnection: () -> Unit,
    onEditSipAccount: () -> Unit,
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
    availableUpdateDescription: String?,
    onCheckForUpdates: suspend ((String, Float?) -> Unit) -> Unit,
    onCall: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onDtmf: (Char) -> Unit,
    onTransferEmployee: (Contact) -> Unit,
    onMicrophoneMutedChange: (Boolean) -> Unit,
    onConferenceHeldChange: (Boolean) -> Unit,
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
    var sidebarAccountOpen by remember { mutableStateOf(false) }
    if (sidebarAccountOpen) {
        AccountSettingsDialog(account, onLogout, onDismiss = { sidebarAccountOpen = false })
    }
    var number by rememberSaveable { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showAddContact by rememberSaveable { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<Contact?>(null) }
    var showConferencePicker by remember { mutableStateOf(false) }
    var creatingRoom by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var dialerTextInputFocused by remember { mutableStateOf(false) }
    val notificationStore = remember { runCatching { notificationHistoryStore() } }
    val loadedNotifications = remember { notificationStore.mapCatching { it.load() } }
    var notifications by remember { mutableStateOf(loadedNotifications.getOrDefault(emptyList())) }
    var notificationStorageError by remember {
        mutableStateOf(if (loadedNotifications.isFailure) "Не удалось загрузить историю уведомлений" else null)
    }
    var notificationOutageActive by remember { mutableStateOf(false) }
    LaunchedEffect(notifications) {
        if (loadedNotifications.isFailure) return@LaunchedEffect
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                notificationStore.getOrThrow().save(notifications)
            }
            notificationStorageError = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            notificationStorageError = "Не удалось сохранить историю уведомлений"
        }
    }
    LaunchedEffect(availableUpdateVersion, availableUpdateDescription) {
        availableUpdateVersion?.let { version ->
            notifications = notifications.withUpdate(
                version, availableUpdateDescription.orEmpty(), Clock.System.now().toEpochMilliseconds()
            )
        }
    }
    LaunchedEffect(connection) {
        if (connection == TelephonyConnection.CONNECTED || connection == TelephonyConnection.DISCONNECTED) {
            notificationOutageActive = false
        }
        if (connection.retryable && !notificationOutageActive) {
            notificationOutageActive = true
            val now = Clock.System.now().toEpochMilliseconds()
            if (notifications.firstOrNull { it.category == NotificationCategory.SYSTEM }?.let { now - it.timestamp > 60_000 } != false) {
                notifications = (listOf(SavedNotification("connection:$now", now, NotificationCategory.SYSTEM,
                    "Нет связи с телефонией", "Проверьте подключение к сети. QTalk автоматически повторит подключение.")) + notifications).take(200)
            }
        }
    }
    val keyboardDialFocus = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val hasUnreadUpdate = notifications.any { !it.read && !it.deleted }
    val showCallOverlay = (call.state == CallState.INCOMING && !callOverlayCollapsed) ||
        (call.state == CallState.ACTIVE && !callOverlayCollapsed)
    LaunchedEffect(call.state) {
        if (call.state != CallState.ACTIVE) showConferencePicker = false
    }

    LaunchedEffect(Unit) { keyboardDialFocus.requestFocus() }
    LaunchedEffect(contacts) {
        selectedContact = selectedContact?.latestFrom(contacts)
    }
    LaunchedEffect(section, notifications) {
        if (section == AppSection.NOTIFICATIONS) {
            if (notifications.any { !it.read }) notifications = notifications.map { it.copy(read = true) }
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
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                if (event.isCtrlPressed && event.key == Key.F) {
                    searchFocusRequester.requestFocus()
                    return@onKeyEvent true
                }
                if (event.key == Key.Escape && showCallOverlay) {
                    onCollapseCall()
                    return@onKeyEvent true
                }
                if (searchFocused || dialerTextInputFocused || call.state.isInProgress ||
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
        val unscaledDensity = LocalUnscaledDensity.current ?: LocalDensity.current
        val scaledDensity = LocalDensity.current
        val appliedScale = scaledDensity.density / unscaledDensity.density
        Row(Modifier.fillMaxSize().padding(top = if (connection.showWarning) 60.dp else 0.dp)
            .then(if (showCallOverlay || showConferencePicker) Modifier.blur(CallOverlayBlur) else Modifier)) {
            CompositionLocalProvider(LocalDensity provides unscaledDensity) {
              Sidebar(
                selected = section,
                registration = registration,
                compact = compact,
                accountContact = contacts.firstOrNull { it.number == accountNumber },
                hasNotifications = hasUnreadUpdate,
                onAccountClick = { sidebarAccountOpen = true },
                onSelect = {
                    searchFocused = false
                    section = it
                }
              )
            }

            when (section) {
                AppSection.CALLS -> CallsHome(
                    onCreateConference = if (registration == RegistrationState.REGISTERED && !call.state.isInProgress)
                        ({ creatingRoom = true; showConferencePicker = true }) else null,
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
                    canTransfer = call.state == CallState.ACTIVE && conference.phase == ConferencePhase.NONE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it },
                    searchFocusRequester = searchFocusRequester
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
                    canTransfer = call.state == CallState.ACTIVE && conference.phase == ConferencePhase.NONE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it },
                    searchFocusRequester = searchFocusRequester
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
                    canTransfer = call.state == CallState.ACTIVE && conference.phase == ConferencePhase.NONE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it },
                    searchFocusRequester = searchFocusRequester
                )
                AppSection.NOTIFICATIONS -> NotificationsSection(
                    notifications = notifications,
                    onDeleteNotifications = { ids ->
                        notifications = notifications.map { notification ->
                            if (notification.id in ids) {
                                notification.copy(deleted = true, read = true, title = "", description = "", version = "")
                            } else {
                                notification
                            }
                        }
                    },
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
                    canTransfer = call.state == CallState.ACTIVE && conference.phase == ConferencePhase.NONE,
                    onTransferEmployee = onTransferEmployee,
                    onSearchFocusChange = { searchFocused = it },
                    searchFocusRequester = searchFocusRequester,
                    availableUpdateVersion = availableUpdateVersion,
                    onOpenUpdate = { section = AppSection.SETTINGS },
                    storageError = notificationStorageError
                )
            }

            CompositionLocalProvider(LocalDensity provides Density(
                unscaledDensity.density * minOf(appliedScale, 1f),
                unscaledDensity.fontScale * maxOf(appliedScale, 1f)
            )) {
                val contactBeingEdited = editingContact
                val deletableContact = if (showAddContact) null else contactBeingEdited ?: selectedContact
                DialerPanel(
                    number = number,
                    contact = selectedContact?.latestFrom(contacts),
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

        if (call.state == CallState.INCOMING && !callOverlayCollapsed && !showConferencePicker) {
            val caller = contacts.firstOrNull { it.matchesCallNumber(call.number) }
            CompositionLocalProvider(LocalCallLightControls provides (themeMode == ThemeMode.LIGHT)) {
            CallsTheme(ThemeMode.DARK) {
                IncomingCallOverlay(
                    roomParticipants = if (conference.roomId.isBlank()) emptyList() else conference.participants.map { participant ->
                        contacts.firstOrNull { it.matchesCallNumber(participant) }?.name ?: formatPhoneNumber(participant) },
                    number = call.number.ifBlank { "Неизвестный номер" },
                    callerName = caller?.name,
                    callerTelegram = caller?.telegram.orEmpty(),
                    callerAvatarUrl = caller?.avatarUrl.orEmpty(),
                    onAccept = onAccept,
                    onDecline = onDecline
                )
            }
            }
        }


        if (call.state == CallState.ACTIVE && !callOverlayCollapsed && !showConferencePicker) {
            val activeContact = contacts.firstOrNull { it.matchesCallNumber(call.number) }
            CompositionLocalProvider(LocalCallLightControls provides (themeMode == ThemeMode.LIGHT)) {
            CallsTheme(ThemeMode.DARK) {
                ActiveCallOverlay(
                    conference = conference,
                    conferenceError = error,
                    ownNumber = accountNumber,
                    contactForNumber = { participant -> contacts.firstOrNull { it.matchesCallNumber(participant) } },
                    onRemoveParticipant = onRemoveParticipant,
                    onAddParticipant = { creatingRoom = false; showConferencePicker = true },
                    onMergeConference = onMergeConference,
                    onCancelConference = onCancelConference,
                    number = call.number,
                    callerName = activeContact?.name,
                    callerTelegram = activeContact?.telegram.orEmpty(),
                    callerAvatarUrl = activeContact?.avatarUrl.orEmpty(),
                    connectedAtEpochMillis = call.connectedAtEpochMillis,
                    onHangUp = onHangUp,
                    onCollapse = onCollapseCall,
                    onDtmf = onDtmf,
                    onMicrophoneMutedChange = onMicrophoneMutedChange,
                    onConferenceHeldChange = onConferenceHeldChange,
                    onSpeakerMutedChange = onSpeakerMutedChange
                )
            }
            }
        }

        if (showConferencePicker) {
            ConferenceParticipantDialog(contacts, accountNumber, if (creatingRoom) "" else call.number,
                existingNumbers = if (creatingRoom) emptyList() else conference.participants + conference.pendingNumbers,
                creatingRoom = creatingRoom,
                onInvite = { targets -> showConferencePicker = false
                    if (creatingRoom) onStartConference(targets) else onInviteConference(targets) },
                onDismiss = { showConferencePicker = false })
        }
        if (connection.showWarning) {
            TelephonyConnectionBanner(connection, onRetryConnection, onEditSipAccount,
                Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ConferenceParticipantDialog(
    contacts: List<Contact>, ownNumber: String, currentNumber: String,
    existingNumbers: List<String>, creatingRoom: Boolean,
    onInvite: (List<String>) -> Unit, onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val excluded = (existingNumbers + ownNumber + currentNumber).map { it.normalizedContactNumber() }.filter(String::isNotBlank).toSet()
    val capacity = com.qvoste.qtalk.voip.MAX_CONFERENCE_PARTICIPANTS - excluded.size
    CallOverlayDialog(
        onDismissRequest = onDismiss,
        containerColor = ControlBackground,
        title = { Text(uiText(if (creatingRoom) "Создать конференцию" else "Добавить участников"), color = TextPrimary, fontSize = 24.sp) },
        text = {
            Column(Modifier.widthIn(min = 280.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(search, { search = it }, label = { Text(uiText("Имя или номер")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = darkTextFieldColors())
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(contacts.filter {
                        val number = it.number.ifBlank { it.phone }.normalizedContactNumber()
                        number.isNotBlank() && number !in excluded && it.matchesSearch(search)
                    }) { contact ->
                        val target = contact.number.ifBlank { contact.phone }
                        val checked = target in selected
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (checked) Primary.copy(alpha = .12f) else Color.Transparent)
                            .clickable { selected = if (checked) selected - target else if (selected.size < capacity) selected + target else selected }
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ContactAvatarWithStatus(contact.name, contact.telegram, 38, contact.status.color, contact.avatarUrl)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                Text(listOf(target, contact.position).filter(String::isNotBlank).joinToString(" • "), color = TextSecondary, fontSize = 11.sp)
                            }
                            androidx.compose.material3.Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onInvite(selected.toList()) },
            enabled = selected.size >= if (creatingRoom) 2 else 1) { Text(uiText("Пригласить") + " (${selected.size})", color = Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Отмена"), color = TextPrimary) } }
    )
}

@Composable
private fun TelephonyConnectionBanner(
    connection: TelephonyConnection,
    onRetry: () -> Unit,
    onEditAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = when (connection) {
        TelephonyConnection.NETWORK_UNAVAILABLE -> "Нет сетевого подключения. Проверьте подключение к сети."
        TelephonyConnection.AUTHENTICATION_FAILED -> "Сервер отклонил авторизацию. Проверьте логин и пароль."
        TelephonyConnection.REGISTRATION_REJECTED -> "Сервер отклонил регистрацию. Проверьте настройки SIP."
        TelephonyConnection.CONNECTING -> "Подключение к серверу телефонии…"
        else -> "Нет связи с сервером телефонии. Переподключаемся…"
    }
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).background(ControlBackground)
        .padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(uiText(message), color = if (connection == TelephonyConnection.CONNECTING) TextSecondary else Danger,
            modifier = Modifier.weight(1f), fontSize = 12.sp)
        TextButton(onClick = if (connection in setOf(TelephonyConnection.AUTHENTICATION_FAILED,
                TelephonyConnection.REGISTRATION_REJECTED)) onEditAccount else onRetry,
            enabled = connection != TelephonyConnection.CONNECTING) {
            Text(uiText(if (connection in setOf(TelephonyConnection.AUTHENTICATION_FAILED,
                    TelephonyConnection.REGISTRATION_REJECTED)) "Настроить аккаунт" else "Повторить"), color = Primary)
        }
    }
}


@Composable
private fun CallsHome(
    onCreateConference: (() -> Unit)?,
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
    onSearchFocusChange: (Boolean) -> Unit,
    searchFocusRequester: FocusRequester
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
        val matchesSearch = it.matchesSearch(search)
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
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange, searchFocusRequester)
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            FilterBar(filter, false, unreadMissedCount) { filter = it }
            onCreateConference?.let { create -> TextButton(onClick = create) {
                Text(uiText("Создать конференцию"), color = Primary, fontSize = 11.sp)
            } }
        }
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
                            uiText(heading),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    items(calls, key = { it.id }) { item ->
                        val currentAvatarUrl = contacts.firstOrNull {
                            it.telegram.isNotBlank() && it.telegram.equals(item.telegram, ignoreCase = true)
                        }?.avatarUrl?.takeIf(String::isNotBlank) ?: item.avatarUrl
                        HistoryRow(item, onQuickCall, onSelectHistory, currentAvatarUrl)
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
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(41.dp)
            .focusRequester(focusRequester)
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
                            uiText("Поиск контакта, номера или компании..."),
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
internal fun SearchAndAddBar(
    search: String,
    onSearchChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    searchFocusRequester: FocusRequester
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            SearchField(search, onSearchChange, onSearchFocusChange, searchFocusRequester)
        }
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
            .onSecondaryClick(canTransfer && transferCodeFor(contact) != null) { onTransferEmployee(contact) }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContactAvatarWithStatus(contact.name, contact.telegram, 54, contact.status.color, contact.avatarUrl)
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
internal fun FavoritesSection(
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
        Text(uiText("Избранное"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            uiText(trailingText),
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onTrailingClick)
        )
    }
    Spacer(Modifier.height(12.dp))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = if (compact) 4.dp else 6.dp
        Row(
            // Symmetric overflow places the viewport 25 dp left of the content edge.
            // This cancels the centered avatar's 25 dp inset without a second offset.
            modifier = Modifier.requiredWidth(maxWidth + 50.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            contacts.filter { it.favorite }.forEach { contact ->
                FavoriteItem(contact, canTransfer, onTransferEmployee) { onSelectContact(contact) }
            }
            Column(
                modifier = Modifier.width(104.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircleResourceAction(Res.drawable.icon_plus, 54, 14, 14, ControlBackground, onAddContact)
                Spacer(Modifier.height(2.dp))
                Text(uiText("Добавить"), color = TextSecondary, fontSize = 12.sp, lineHeight = 12.sp)
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
    onSelect: (HistoryItem) -> Unit,
    avatarUrl: String = item.avatarUrl
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
            Modifier.requiredWidth(maxWidth + 16.dp).fillMaxHeight()
                .clip(RoundedCornerShape(12.dp)).clickable { onSelect(item) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(item.name, item.telegram, 46, avatarUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, color = TextPrimary, fontSize = 13.sp, lineHeight = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(directionIcon), contentDescription = null, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(uiText(if (item.missed) "Пропущенный" else item.direction),
                        color = if (item.missed) Danger else TextSecondary,
                        fontSize = 11.sp, lineHeight = 13.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatHistoryTimestamp(item.startedAtEpochMillis, Clock.System.now().toEpochMilliseconds()).takeLast(5),
                    color = TextSecondary, fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.End)
                Text(uiText(formatCallDuration(item.durationSeconds).ifBlank { "—" }),
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
                    DropdownMenuItem(text = { Text(uiText("Позвонить")) }, onClick = {
                        menuExpanded = false
                        onCall(item.number)
                    })
                    DropdownMenuItem(text = { Text(uiText("Копировать номер")) }, onClick = {
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
    onSearchFocusChange: (Boolean) -> Unit,
    searchFocusRequester: FocusRequester
) {
    var showAccount by remember { mutableStateOf(false) }
    var showInputDevices by remember { mutableStateOf(false) }
    var showOutputDevices by remember { mutableStateOf(false) }
    var showRingtones by remember { mutableStateOf(false) }
    var showDatabase by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    val languageSettings = LocalAppLanguage.current
    val interfaceScale = LocalInterfaceScale.current
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
    val accountNumber = account?.username.orEmpty().trim()
    val accountContact = contacts.firstOrNull { contact ->
        contact.number.normalizedContactNumber() == accountNumber.normalizedContactNumber()
    }
    val accountName = accountContact?.name ?: accountNumber.takeIf(String::isNotBlank)?.let { uiText("Внутренний $it") }
        ?: uiText("SIP-аккаунт")
    val accountPosition = accountContact?.position.orEmpty().ifBlank { uiText("Должность не указана") }
    val accountStatusColor = if (registration == RegistrationState.REGISTERED) {
        accountContact?.status?.color ?: Online
    } else TextSecondary

    LaunchedEffect(Unit) { onRefreshAudioDevices() }

    Column(
        modifier.fillMaxHeight().padding(
            start = if (compact) 40.dp else 38.dp,
            top = 16.dp,
            end = if (compact) 13.dp else 18.dp,
            bottom = 20.dp
        )
    ) {
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange, searchFocusRequester)
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
                if (accountContact == null) {
                    AvatarWithStatus(
                        initials = accountNumber.takeLast(2).ifBlank { "QT" },
                        seed = accountNumber.ifBlank { "QTalk" },
                        size = 44,
                        statusColor = accountStatusColor
                    )
                } else {
                    ContactAvatarWithStatus(
                        name = accountContact.name,
                        telegram = accountContact.telegram,
                        avatarUrl = accountContact.avatarUrl,
                        size = 44,
                        statusColor = accountStatusColor
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(accountName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    val extensionLabel = uiText("Внутренний")
                    Text(
                        buildString {
                            append(accountPosition)
                            if (accountNumber.isNotBlank()) append("  •  ").append(extensionLabel).append(" ").append(accountNumber)
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
                icon = Res.drawable.icon_settings_scale,
                title = "Масштаб интерфейса",
                subtitle = "Размер текста и элементов приложения",
                value = interfaceScale.label,
                onClick = { showScale = true }
            )
            SettingsActionRow(
                icon = Res.drawable.icon_settings_language,
                title = "Язык интерфейса",
                subtitle = "Русский или английский",
                value = languageSettings.language.displayName,
                onClick = { showLanguage = true }
            )
            SettingsActionRow(
                icon = Res.drawable.icon_settings_database,
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

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            containerColor = ControlBackground,
            title = { Text(uiText("Выберите язык"), color = TextPrimary) },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        TextButton(onClick = {
                            languageSettings.onChange(language)
                            showLanguage = false
                        }) {
                            Text(language.displayName + if (language == languageSettings.language) "  ✓" else "", color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(uiText("Закрыть"), color = Primary) } }
        )
    }
    if (showScale) {
        AlertDialog(
            onDismissRequest = { showScale = false },
            containerColor = ControlBackground,
            title = { Text(uiText("Масштаб интерфейса"), color = TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(uiText("Автоматический масштаб подстраивается под размер окна."), color = TextSecondary, fontSize = 12.sp)
                    listOf(0f, 0.65f, 0.75f, 0.8f, 0.9f, 1f, 1.1f, 1.25f).forEach { scale ->
                        TextButton(onClick = { interfaceScale.onChange(scale) }) {
                            Text(
                                (if (interfaceScale.value == scale) "✓  " else "") + uiText(InterfaceScale(scale).label),
                                color = if (interfaceScale.value == scale) Primary else TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showScale = false }) { Text(uiText("Готово"), color = Primary) } }
        )
    }
    if (showAccount) {
        AccountSettingsDialog(account, onLogout, onDismiss = { showAccount = false })
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
            title = { Text(uiText("База данных"), color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        uiText("Подключите Google Таблицу с контактами. Таблица должна быть доступна всем, у кого есть ссылка."),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = databaseUrl,
                        onValueChange = { databaseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(uiText("Ссылка на Google Таблицу")) },
                        placeholder = { Text("https://docs.google.com/spreadsheets/d/…") },
                        singleLine = true,
                        enabled = !contactDatabaseState.syncing,
                        colors = darkTextFieldColors()
                    )
                    Text(
                        uiText("Контакты обновляются при запуске и каждые 15 минут. E-mail из таблицы не импортируется."),
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        uiText(contactDatabaseState.message),
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
                                Text(uiText("Обновить сейчас"), color = Primary)
                            }
                            TextButton(
                                onClick = {
                                    onDisconnectContactDatabase()
                                    databaseUrl = ""
                                },
                                enabled = !contactDatabaseState.syncing
                            ) {
                                Text(uiText("Отключить"), color = Danger)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSaveContactDatabaseUrl(databaseUrl) },
                    enabled = !contactDatabaseState.syncing
                ) {
                    Text(uiText("Сохранить"), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatabase = false }) {
                    Text(uiText("Закрыть"), color = TextPrimary)
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
                        uiText("Версия $appVersion\nКорпоративный SIP-клиент для звонков и работы с контактами."),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    updateMessage?.let { message ->
                        Spacer(Modifier.height(12.dp))
                        Text(uiText(message), color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
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
                        Text(uiText("${(progress * 100).toInt()}%"), color = TextSecondary, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text(uiText("Готово"), color = TextPrimary) }
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
                ) { Text(uiText("Обновить"), color = Primary) }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String, subtitle: String) {
    Text(uiText(title), color = TextPrimary, fontSize = 17.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
    Text(uiText(subtitle), color = TextSecondary, fontSize = 11.sp, lineHeight = 11.sp)
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
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxValueWidth = minOf(260.dp, maxWidth * 0.4f)
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).clickable(interactionSource = interactions, indication = null, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).background(ControlBackground, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(if (titleColor == Danger) Danger else TextPrimary)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText(title), color = titleColor.onHover(hovered), fontSize = 12.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(uiText(it), color = TextSecondary.onHover(hovered), fontSize = 10.sp, lineHeight = 10.sp) }
            }
            value?.let {
                Text(
                    uiText(it),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = maxValueWidth)
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
    Column(
        Modifier.padding(start = 60.dp)
            .widthIn(max = 410.dp)
            .fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
                Image(
                    painterResource(
                        if (value < 0.5f) Res.drawable.icon_settings_volume_low
                        else Res.drawable.icon_settings_volume_high
                    ),
                    contentDescription = null,
                    modifier = Modifier.width(if (value < 0.5f) 18.dp else 22.dp).height(17.dp),
                    colorFilter = ColorFilter.tint(TextPrimary)
                )
            }
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.Center) {
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
                uiText("${(value * 100).toInt()}%"),
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.width(28.dp)
            )
        }
        Text(uiText(label), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.padding(start = 31.dp))
    }
}

@Composable
private fun AccountSettingsDialog(account: SipAccount?, onLogout: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Настройка аккаунта"), color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsDetail("SIP-логин", account?.username.orEmpty())
                SettingsDetail("SIP-сервер", account?.domain.orEmpty())
                SettingsDetail("Порт", account?.port?.toString().orEmpty())
                SettingsDetail("Транспорт", account?.transport?.name.orEmpty())
                SettingsDetail("Realm", account?.realm ?: "—")
            }
        },
        confirmButton = { TextButton(onClick = onLogout) { Text(uiText("Сменить аккаунт"), color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Закрыть"), color = TextPrimary) } }
    )
}

@Composable
private fun SettingsDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(uiText(label), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
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
        title = { Text(uiText(title), color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (devices.isEmpty()) {
                    Text(uiText("Доступные устройства не найдены"), color = TextSecondary, fontSize = 12.sp)
                } else {
                    devices.forEach { device ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (device.id == selectedDeviceId) Primary.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { onSelect(device.id) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(uiText(device.name), color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (device.id == selectedDeviceId) Text("✓", color = Primary, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(uiText("Отмена"), color = TextPrimary) }
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
    var imported by remember { mutableStateOf(customRingtones()) }
    var importError by remember { mutableStateOf<String?>(null) }
    val importScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ControlBackground,
        title = { Text(uiText("Выберите рингтон"), color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (ringtones + imported).distinctBy { it.id }.forEach { ringtone ->
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
                        Text(uiText(ringtone.name), color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (ringtone.id == pendingRingtoneId) Text("✓", color = Primary, fontSize = 14.sp)
                    }
                }
                TextButton(onClick = {
                    importScope.launch {
                        try {
                            importLocalMedia(false)?.let { pendingRingtoneId = it; imported = customRingtones(); onPreview(it) }
                            importError = null
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { importError = e.message }
                    }
                }) { Text(uiText("Загрузить рингтон"), color = Primary) }
                importError?.let { Text(it, color = Danger, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pendingRingtoneId) }) { Text(uiText("Сохранить"), color = Primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(uiText("Отмена"), color = TextPrimary) }
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
        label = { Text(uiText(label)) },
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
    onSearchFocusChange: (Boolean) -> Unit,
    searchFocusRequester: FocusRequester
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
        SearchAndAddBar(search, { search = it }, onAddContact, onSearchFocusChange, searchFocusRequester)

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
        "${uiText(filter.title)}  $count"
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
internal fun SegmentedBar(
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
                        uiText(title),
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
            Modifier.requiredWidth(maxWidth + 16.dp).fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .onSecondaryClick(canTransfer && transferCodeFor(contact) != null) { onTransferEmployee(contact) }
                .clickable { onSelect(contact) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatarWithStatus(contact.name, contact.telegram, 46, contact.status.color, contact.avatarUrl)
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
                    "${uiText(if (contact.isEmployee) "Сотрудник" else "Клиент")}  •  " +
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
