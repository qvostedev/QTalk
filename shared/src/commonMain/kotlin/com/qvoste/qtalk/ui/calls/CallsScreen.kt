package com.qvoste.qtalk.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.qvoste.qtalk.voip.CallState
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.RegistrationState
import com.qvoste.qtalk.voip.SipAccount
import com.qvoste.qtalk.voip.SipTransport
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.icon_calls
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
import qtalk.shared.generated.resources.icon_telegram
import qtalk.shared.generated.resources.logo_white_transparent

// Основные цвета макета собраны здесь, чтобы их было легко заменить.
private val AppBackground = Color(0xFF0B0D10)
private val PanelBackground = AppBackground
private val ControlBackground = Color(0xFF191C20)
private val DividerColor = Color(0xFF23262D)
private val Primary = Color(0xFF4B6DC4)
private val TextPrimary = Color(0xFFE8E9EC)
private val TextSecondary = Color(0x80FFFFFF)
private val Danger = Color(0xFF971818)
private val Online = Color(0xFF58A800)

private enum class AppSection(val title: String) {
    CALLS("Звонки"),
    CONTACTS("Контакты"),
    SETTINGS("Настройки"),
    NOTIFICATIONS("Уведомления")
}

private enum class CallFilter(val title: String) {
    ALL("Все"), MISSED("Пропущенные"), OUTGOING("Исходящие"), INCOMING("Входящие")
}

private data class Contact(
    val name: String,
    val number: String,
    val favorite: Boolean,
    val position: String = "",
    val department: String = "",
    val company: String = "",
    val telegram: String = ""
) {
    // Компания ПОРТ автоматически относит контакт к сотрудникам.
    val isEmployee: Boolean get() = company.trim().equals("ПОРТ", ignoreCase = true)
}

private enum class ContactFilter(val title: String) {
    ALL("Все"), CLIENTS("Клиенты"), EMPLOYEES("Сотрудники")
}

private data class HistoryItem(
    val name: String,
    val number: String,
    val direction: String,
    val time: String,
    val missed: Boolean = false
)

@Composable
fun CallsScreen(viewModel: CallsViewModel) {
    val registration by viewModel.registrationState.collectAsState()
    val registrationMessage by viewModel.registrationMessage.collectAsState()
    val call by viewModel.callStatus.collectAsState()
    val error by viewModel.error.collectAsState()
    val accountNumber by viewModel.accountNumber.collectAsState()
    val authenticationRequired by viewModel.authenticationRequired.collectAsState()
    val accountDraft by viewModel.accountDraft.collectAsState()

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
        registrationMessage = registrationMessage,
        call = call,
        error = error,
        accountNumber = accountNumber,
        onConnect = viewModel::connect,
        onCall = viewModel::call,
        onAccept = viewModel::acceptCall,
        onDecline = viewModel::declineCall,
        onHangUp = viewModel::hangUp,
        onLogout = viewModel::logout
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
                painter = painterResource(Res.drawable.logo_white_transparent),
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
    registrationMessage: String,
    call: CallStatus,
    error: String?,
    accountNumber: String,
    onConnect: (String, String, String) -> Unit,
    onCall: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onLogout: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(AppSection.CALLS) }
    var number by rememberSaveable { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var showAddContact by rememberSaveable { mutableStateOf(false) }
    val contacts = remember { mutableStateListOf<Contact>() }
    val history = remember { mutableStateListOf<HistoryItem>() }

    // В историю попадают только настоящие входящие вызовы.
    LaunchedEffect(call.state, call.number) {
        if (call.state == CallState.INCOMING) {
            val contact = contacts.firstOrNull { it.number == call.number }
            history.add(
                0,
                HistoryItem(
                    name = contact?.name ?: call.number.ifBlank { "Неизвестный номер" },
                    number = call.number,
                    direction = "Входящий",
                    time = "Только что"
                )
            )
        }
    }

    fun startCall(target: String) {
        val cleanNumber = target.trim()
        if (cleanNumber.isBlank()) return
        val contact = contacts.firstOrNull { it.number == cleanNumber }
        history.add(
            0,
            HistoryItem(
                name = contact?.name ?: cleanNumber,
                number = cleanNumber,
                direction = "Исходящий",
                time = "Только что"
            )
        )
        onCall(cleanNumber)
    }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        // При любом звонке основной интерфейс остаётся виден через размытие.
        val showCallOverlay = call.state == CallState.INCOMING || call.state == CallState.ACTIVE
        Row(Modifier.fillMaxSize().then(if (showCallOverlay) Modifier.blur(8.dp) else Modifier)) {
            Sidebar(
                selected = section,
                registration = registration,
                onSelect = { section = it }
            )

            // Центральная часть зависит от выбранного раздела.
            when (section) {
                AppSection.CALLS -> CallsHome(
                    modifier = Modifier.weight(1f),
                    contacts = contacts,
                    history = history,
                    onAddContact = { showAddContact = true },
                    onShowAllContacts = { section = AppSection.CONTACTS },
                    onSelectContact = {
                        selectedContact = it
                        number = it.number
                    },
                    onQuickCall = {
                        number = it
                        if (registration == RegistrationState.REGISTERED && !call.state.isInProgress) {
                            startCall(it)
                        }
                    }
                )
                AppSection.CONTACTS -> ContactsSection(
                    modifier = Modifier.weight(1f),
                    contacts = contacts,
                    onAddContact = { showAddContact = true },
                    onSelectContact = {
                        selectedContact = it
                        number = it.number
                    },
                    onCallContact = {
                        number = it.number
                        selectedContact = it
                        if (registration == RegistrationState.REGISTERED && !call.state.isInProgress) {
                            startCall(it.number)
                        }
                    },
                    onDeleteContact = {
                        contacts.remove(it)
                        if (selectedContact == it) selectedContact = null
                    }
                )
                AppSection.SETTINGS -> SipSettings(
                    modifier = Modifier.weight(1f),
                    registration = registration,
                    registrationMessage = registrationMessage,
                    error = error,
                    onConnect = onConnect,
                    onLogout = onLogout
                )
                AppSection.NOTIFICATIONS -> EmptySection(
                    modifier = Modifier.weight(1f),
                    title = "Уведомления",
                    text = "Новых уведомлений нет"
                )
            }

            if (section == AppSection.CALLS || section == AppSection.CONTACTS) {
                DialerPanel(
                    number = number,
                    contact = selectedContact,
                    accountNumber = accountNumber,
                    registration = registration,
                    call = call,
                    error = error,
                    onNumberChange = {
                        number = it.filter { char -> char.isDigit() || char == '*' || char == '#' }
                        selectedContact = contacts.firstOrNull { contact -> contact.number == number }
                    },
                    onCall = { startCall(number) },
                    onHangUp = onHangUp
                )
            }
        }

        if (call.state == CallState.INCOMING) {
            val caller = contacts.firstOrNull { it.number == call.number }
            IncomingCallOverlay(
                number = call.number.ifBlank { "Неизвестный номер" },
                callerName = caller?.name,
                onAccept = onAccept,
                onDecline = onDecline
            )
        }


        if (call.state == CallState.ACTIVE) {
            val activeContact = contacts.firstOrNull { it.number == call.number }
            ActiveCallOverlay(
                number = call.number,
                callerName = activeContact?.name,
                onHangUp = onHangUp
            )
        }

        if (showAddContact) {
            AddContactDialog(
                existingNumbers = contacts.map { it.number }.toSet(),
                onDismiss = { showAddContact = false },
                onAdd = { contact ->
                    contacts.add(contact)
                    selectedContact = contact
                    number = contact.number
                    showAddContact = false
                }
            )
        }
    }
}

@Composable
private fun Sidebar(
    selected: AppSection,
    registration: RegistrationState,
    onSelect: (AppSection) -> Unit
) {
    Column(
        modifier = Modifier.width(214.dp).fillMaxHeight().padding(39.dp, 39.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Image(
                    painter = painterResource(Res.drawable.logo_white_transparent),
                    contentDescription = "QTalk",
                    modifier = Modifier.size(41.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "QTalk",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(Modifier.height(38.dp))
            SectionLabel("ОСНОВНОЕ")
            NavItem(AppSection.CALLS, selected, onSelect)
            NavItem(AppSection.CONTACTS, selected, onSelect)

            Spacer(Modifier.height(22.dp))
            SectionLabel("СИСТЕМА")
            NavItem(AppSection.SETTINGS, selected, onSelect)
            NavItem(AppSection.NOTIFICATIONS, selected, onSelect)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar("QT", colorFor("QTalk"), 42)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("QTalk", color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                Text(
                    registrationText(registration),
                    color = if (registration == RegistrationState.REGISTERED) Online else TextSecondary,
                    fontSize = 11.sp
                )
            }
            Text("›", color = TextSecondary, fontSize = 22.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun NavItem(section: AppSection, selected: AppSection, onSelect: (AppSection) -> Unit) {
    val active = section == selected
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).clickable { onSelect(section) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(sectionIcon(section)),
            contentDescription = section.title,
            modifier = Modifier.size(21.dp),
            colorFilter = ColorFilter.tint(if (active) TextPrimary else TextSecondary)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            section.title,
            color = if (active) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.weight(1f))
        if (active) Box(Modifier.width(3.dp).height(28.dp).background(Primary, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun CallsHome(
    modifier: Modifier,
    contacts: List<Contact>,
    history: List<HistoryItem>,
    onAddContact: () -> Unit,
    onShowAllContacts: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    onQuickCall: (String) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CallFilter.ALL) }
    val visibleHistory = history.filter {
        val matchesSearch = search.isBlank() || it.name.contains(search, ignoreCase = true) ||
            it.number.contains(search)
        val matchesFilter = when (filter) {
            CallFilter.ALL -> true
            CallFilter.MISSED -> it.missed
            CallFilter.OUTGOING -> it.direction == "Исходящий"
            CallFilter.INCOMING -> it.direction == "Входящий"
        }
        matchesSearch && matchesFilter
    }

    Column(modifier.fillMaxHeight().padding(29.dp, 39.dp, 29.dp, 20.dp)) {
        SearchAndAddBar(search, { search = it }, onAddContact)
        Spacer(Modifier.height(35.dp))
        FavoritesSection(
            contacts = contacts,
            trailingText = "Все  ›",
            onTrailingClick = onShowAllContacts,
            onSelectContact = onSelectContact,
            onAddContact = onAddContact
        )

        Spacer(Modifier.height(32.dp))
        FilterBar(filter) { filter = it }
        Spacer(Modifier.height(24.dp))
        Text("История", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        if (visibleHistory.isEmpty()) {
            EmptyMessage(if (history.isEmpty()) "История звонков пока пуста" else "Ничего не найдено")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visibleHistory) { item -> HistoryRow(item, onQuickCall) }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(41.dp)
            .background(ControlBackground, RoundedCornerShape(35.dp))
            .border(
                width = 1.dp,
                color = if (focused) Primary else DividerColor,
                shape = RoundedCornerShape(35.dp)
            ),
        singleLine = true,
        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
        cursorBrush = SolidColor(Primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(Res.drawable.icon_search),
                    contentDescription = "Поиск",
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Поиск контакта, номера или компании...",
                            color = TextSecondary,
                            fontSize = 10.sp
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
    onAddContact: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SearchField(search, onSearchChange) }
        Spacer(Modifier.width(14.dp))
        CircleAction("+", 40, ControlBackground, onAddContact)
    }
}

@Composable
private fun FavoriteItem(contact: Contact, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(58.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Avatar(initialsForName(contact.name), colorFor(contact.name), 48)
            Box(
                Modifier.align(Alignment.BottomEnd).size(10.dp)
                    .background(Online, CircleShape).border(2.dp, AppBackground, CircleShape)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(contact.name, color = TextPrimary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(contact.number, color = TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun FavoritesSection(
    contacts: List<Contact>,
    trailingText: String,
    onTrailingClick: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    onAddContact: () -> Unit
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
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        contacts.filter { it.favorite }.take(7).forEach { contact ->
            FavoriteItem(contact) { onSelectContact(contact) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircleAction("+", 48, ControlBackground, onAddContact)
            Spacer(Modifier.height(7.dp))
            Text("Добавить", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FilterBar(selected: CallFilter, onSelect: (CallFilter) -> Unit) {
    SegmentedBar(
        options = CallFilter.entries.map { it.title },
        selectedIndex = CallFilter.entries.indexOf(selected),
        onSelect = { onSelect(CallFilter.entries[it]) }
    )
}

@Composable
private fun HistoryRow(item: HistoryItem, onCall: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(initialsForName(item.name), colorFor(item.name), 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = TextPrimary, fontSize = 12.sp)
            Text(
                (if (item.missed) "×  " else if (item.direction == "Исходящий") "↗  " else "↙  ") + item.direction,
                color = if (item.missed) Danger else TextSecondary,
                fontSize = 10.sp
            )
        }
        Text(item.time, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.End)
        Spacer(Modifier.width(14.dp))
        CircleIconAction(Res.drawable.icon_calls, 34, ControlBackground) { onCall(item.number) }
    }
}

@Composable
private fun DialerPanel(
    number: String,
    contact: Contact?,
    accountNumber: String,
    registration: RegistrationState,
    call: CallStatus,
    error: String?,
    onNumberChange: (String) -> Unit,
    onCall: () -> Unit,
    onHangUp: () -> Unit
) {
    var infoTab by rememberSaveable { mutableStateOf("Информация") }
    val uriHandler = LocalUriHandler.current
    val shownNumber = if (call.state.isInProgress) call.number.ifBlank { number } else number

    Column(
        modifier = Modifier.width(416.dp).fillMaxHeight().background(PanelBackground).padding(37.dp, 39.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    contact?.let { "Внутренний  •  ${it.number}" }
                        ?: "Здесь появится информация о контакте.",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.width(285.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Информация", "История", "Заметки").forEach { tab ->
                InfoTab(tab, infoTab == tab) { infoTab = tab }
            }
        }
        Spacer(Modifier.height(29.dp))
        Box(Modifier.width(285.dp).height(246.dp)) {
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
                        icon = Res.drawable.icon_telegram,
                        label = "Telegram",
                        value = contact.telegram.ifBlank { "Не указан" },
                        isLink = contact.telegram.isNotBlank(),
                        onClick = {
                            val username = contact.telegram.removePrefix("@").trim()
                            if (username.isNotBlank()) uriHandler.openUri("https://t.me/$username")
                        }
                    )
                }
                else -> EmptyMessage(if (infoTab == "История") "Звонков пока нет" else "Заметок пока нет")
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.width(285.dp).height(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Личная линия сотрудника",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Text(
                "•  ${accountNumber.ifBlank { "—" }}",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.width(285.dp).height(29.dp), contentAlignment = Alignment.Center) {
            Text(
                shownNumber.ifBlank { "—" },
                color = TextPrimary,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Image(
                painter = painterResource(Res.drawable.icon_backspace),
                contentDescription = "Удалить цифру",
                modifier = Modifier.align(Alignment.CenterEnd).size(24.dp, 18.dp)
                    .clickable(enabled = number.isNotEmpty() && !call.state.isInProgress) {
                        onNumberChange(number.dropLast(1))
                    }
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
        if (call.state.isInProgress) {
            CircleIconAction(
                Res.drawable.icon_phone,
                54,
                Color(0xFFFF3545),
                Modifier.align(Alignment.CenterHorizontally),
                onHangUp
            )
        } else {
            CircleIconAction(
                Res.drawable.icon_phone,
                54,
                if (canCall) Primary else Color(0xFF293142),
                Modifier.align(Alignment.CenterHorizontally)
            ) { if (canCall) onCall() }
        }
        error?.let { Text(it, color = Danger, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
private fun InfoTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = if (selected) Primary else TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.width(58.dp).height(1.dp).background(if (selected) Primary else Color.Transparent))
    }
}

@Composable
private fun ContactInfo(
    icon: DrawableResource,
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).clickable(enabled = isLink, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = TextPrimary, fontSize = 10.sp, lineHeight = 12.sp)
            Text(value, color = if (isLink) Primary else TextSecondary, fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun KeypadButton(value: String, letters: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).background(ControlBackground, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = TextPrimary, fontSize = 19.sp, lineHeight = 19.sp)
            if (letters.isNotBlank()) Text(letters, color = TextSecondary, fontSize = 8.sp, lineHeight = 8.sp)
        }
    }
}

@Composable
private fun IncomingCallOverlay(
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
private fun ActiveCallOverlay(number: String, callerName: String?, onHangUp: () -> Unit) {
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

@Composable
private fun SipSettings(
    modifier: Modifier,
    registration: RegistrationState,
    registrationMessage: String,
    error: String?,
    onConnect: (String, String, String) -> Unit,
    onLogout: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("100") }
    var password by rememberSaveable { mutableStateOf("qtalk100") }
    var server by rememberSaveable { mutableStateOf("127.0.0.1") }
    val canEdit = registration == RegistrationState.DISCONNECTED || registration == RegistrationState.FAILED

    Column(modifier.fillMaxHeight().padding(48.dp), horizontalAlignment = Alignment.Start) {
        Text("Настройки SIP", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Укажите данные аккаунта и адрес сервера", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))

        DarkField(username, { username = it.filter(Char::isDigit) }, "Ваш номер", canEdit)
        Spacer(Modifier.height(14.dp))
        DarkField(password, { password = it }, "Пароль", canEdit, password = true)
        Spacer(Modifier.height(14.dp))
        DarkField(server, { server = it }, "IP SIP-сервера", canEdit)
        Spacer(Modifier.height(22.dp))

        Button(
            onClick = { onConnect(username, password, server) },
            enabled = canEdit && username.isNotBlank() && password.isNotBlank() && server.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier.width(180.dp).height(48.dp)
        ) {
            Text(if (registration == RegistrationState.CONNECTING) "Подключение..." else "Подключиться")
        }

        Spacer(Modifier.height(18.dp))
        Text("Статус: ${registrationText(registration)}", color = TextSecondary, fontSize = 12.sp)
        if (registrationMessage.isNotBlank()) Text(registrationMessage, color = Danger, fontSize = 11.sp)
        error?.let { Text(it, color = Danger, fontSize = 11.sp) }
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onLogout) {
            Text("Выйти из аккаунта", color = Color(0xFFFF5B64))
        }
    }
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
    contacts: List<Contact>,
    onAddContact: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    onCallContact: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ContactFilter.ALL) }
    val visibleContacts = contacts.filter {
        val matchesSearch = search.isBlank() || it.name.contains(search, ignoreCase = true) ||
            it.number.contains(search) || it.company.contains(search, ignoreCase = true) ||
            it.department.contains(search, ignoreCase = true)
        val matchesType = when (filter) {
            ContactFilter.ALL -> true
            ContactFilter.CLIENTS -> !it.isEmployee
            ContactFilter.EMPLOYEES -> it.isEmployee
        }
        matchesSearch && matchesType
    }.sortedBy { it.name.lowercase() }
    val groups = visibleContacts.groupBy { it.name.trim().firstOrNull()?.uppercase() ?: "#" }

    Column(modifier.fillMaxHeight().padding(29.dp, 39.dp, 29.dp, 20.dp)) {
        SearchAndAddBar(search, { search = it }, onAddContact)

        Spacer(Modifier.height(35.dp))
        FavoritesSection(
            contacts = contacts,
            trailingText = "${contacts.count { it.favorite }} контактов",
            onTrailingClick = {},
            onSelectContact = onSelectContact,
            onAddContact = onAddContact
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
                        ContactRow(contact, onSelectContact, onCallContact, onDeleteContact)
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
    SegmentedBar(
        options = options,
        selectedIndex = ContactFilter.entries.indexOf(selected),
        onSelect = { onSelect(ContactFilter.entries[it]) }
    )
}

@Composable
private fun SegmentedBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier.background(ControlBackground, RoundedCornerShape(22.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, title ->
            Box(
                Modifier.clip(RoundedCornerShape(18.dp))
                    .background(if (selectedIndex == index) Primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 22.dp, vertical = 9.dp)
            ) {
                Text(
                    title,
                    color = if (selectedIndex == index) TextPrimary else TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    onSelect: (Contact) -> Unit,
    onCall: (Contact) -> Unit,
    onDelete: (Contact) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))
            .background(ControlBackground).clickable { onSelect(contact) }.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(initialsForName(contact.name), colorFor(contact.name), 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${if (contact.isEmployee) "Сотрудник" else "Клиент"}  •  " +
                    contact.position.ifBlank { contact.company.ifBlank { contact.number } },
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
        if (contact.favorite) Text("★", color = Primary, fontSize = 18.sp)
        Spacer(Modifier.width(14.dp))
        CircleIconAction(Res.drawable.icon_calls, 34, ControlBackground) { onCall(contact) }
        Spacer(Modifier.width(10.dp))
        Text(
            "⋮",
            color = TextSecondary,
            fontSize = 20.sp,
            modifier = Modifier.clickable { onDelete(contact) }.padding(8.dp)
        )
    }
}

@Composable
private fun AddContactDialog(
    existingNumbers: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (Contact) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var company by rememberSaveable { mutableStateOf("") }
    var telegram by remember { mutableStateOf(TextFieldValue()) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    val duplicate = number in existingNumbers
    val canAdd = name.isNotBlank() && number.isNotBlank() && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBackground,
        title = { Text("Новый контакт", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DarkField(name, { name = it }, "ФИО", true)
                DarkField(number, { number = it.filter(Char::isDigit) }, "Внутренний номер", true)
                DarkField(position, { position = it }, "Должность", true)
                DarkField(department, { department = it }, "Отдел", true)
                DarkField(company, { company = it }, "Компания", true)
                OutlinedTextField(
                    value = telegram,
                    onValueChange = { entered ->
                        val normalized = normalizeTelegramInput(entered.text)
                        telegram = TextFieldValue(normalized, selection = TextRange(normalized.length))
                    },
                    modifier = Modifier.width(430.dp),
                    label = { Text("Telegram (@username)") },
                    singleLine = true,
                    colors = darkTextFieldColors()
                )
                Row(
                    Modifier.fillMaxWidth().clickable { favorite = !favorite }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(20.dp).border(1.dp, Primary, RoundedCornerShape(4.dp))
                            .background(if (favorite) Primary else Color.Transparent, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (favorite) Text("✓", color = TextPrimary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Добавить в избранное", color = TextPrimary, fontSize = 12.sp)
                }
                if (duplicate) Text("Контакт с таким номером уже существует", color = Danger, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        Contact(
                            name = name.trim(),
                            number = number.trim(),
                            favorite = favorite,
                            position = position.trim(),
                            department = department.trim(),
                            company = company.trim(),
                            telegram = telegram.text.trim()
                        )
                    )
                },
                enabled = canAdd
            ) {
                Text("Добавить", color = if (canAdd) Primary else TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        }
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptySection(modifier: Modifier, title: String, text: String) {
    Column(modifier.fillMaxHeight().padding(48.dp)) {
        Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun Avatar(initials: String, color: Color, size: Int) {
    Box(Modifier.size(size.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = TextPrimary, fontSize = (size * 0.3).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CircleAction(text: String, size: Int, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).background(color, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, fontSize = (size * 0.4).sp)
    }
}

@Composable
private fun CircleIconAction(
    icon: DrawableResource,
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.size(size.dp).background(color, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size((size * 0.42).dp)
        )
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

private fun sectionIcon(section: AppSection): DrawableResource = when (section) {
    AppSection.CALLS -> Res.drawable.icon_calls
    AppSection.CONTACTS -> Res.drawable.icon_contacts
    AppSection.SETTINGS -> Res.drawable.icon_settings
    AppSection.NOTIFICATIONS -> Res.drawable.icon_notifications
}

private fun registrationText(state: RegistrationState) = when (state) {
    RegistrationState.DISCONNECTED -> "Не подключено"
    RegistrationState.CONNECTING -> "Подключение"
    RegistrationState.REGISTERED -> "В сети"
    RegistrationState.FAILED -> "Ошибка подключения"
}

private fun callStateText(call: CallStatus, registration: RegistrationState) = when (call.state) {
    CallState.IDLE -> if (registration == RegistrationState.REGISTERED) "Готов к звонку" else "Подключите SIP в настройках"
    CallState.INCOMING -> "Входящий звонок"
    CallState.DIALING -> "Набор ${call.number}..."
    CallState.RINGING -> "Ожидание ответа ${call.number}..."
    CallState.ACTIVE -> "Разговор с ${call.number}"
    CallState.ENDING -> "Завершение звонка..."
    CallState.ENDED -> "Звонок завершён"
    CallState.FAILED -> "Не удалось позвонить: ${call.message}"
}

private fun initialsForName(name: String): String = name.trim().split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifBlank { "?" }

// Цвет аватара вычисляется из имени контакта.
private fun colorFor(value: String): Color {
    val colors = listOf(
        Color(0xFF3659A2), Color(0xFF681466), Color(0xFF7A4A0D),
        Color(0xFF4C0084), Color(0xFF60472F), Color(0xFF8D1558)
    )
    val index = (value.hashCode() and Int.MAX_VALUE) % colors.size
    return colors[index]
}

// Храним Telegram в одном читаемом формате: @username.
private fun normalizeTelegramInput(value: String): String {
    val username = value.trim()
        .removePrefix("https://t.me/")
        .removePrefix("http://t.me/")
        .removePrefix("t.me/")
        .removePrefix("@")
        .filter { it.isLetterOrDigit() || it == '_' }
    return if (username.isBlank()) "" else "@$username"
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = (totalSeconds / 60).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

private val keypadRows = listOf(
    listOf("1" to "", "2" to "АБВГ", "3" to "ДЕЖЗ"),
    listOf("4" to "ИЙКЛ", "5" to "МНОП", "6" to "РСТУ"),
    listOf("7" to "ФХЦЧ", "8" to "ШЩЪЫ", "9" to "ЬЭЮЯ"),
    listOf("*" to "", "0" to "+", "#" to "")
)
