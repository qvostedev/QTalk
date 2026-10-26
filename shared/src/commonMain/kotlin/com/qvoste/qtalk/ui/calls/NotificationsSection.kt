package com.qvoste.qtalk.ui.calls

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.components.*
import com.qvoste.qtalk.ui.calls.components.QTalkAlertDialog as AlertDialog
import com.qvoste.qtalk.ui.calls.i18n.LocalAppLanguage
import com.qvoste.qtalk.ui.calls.i18n.releaseNotesForLanguage
import com.qvoste.qtalk.ui.calls.i18n.uiText
import com.qvoste.qtalk.ui.calls.model.*
import com.qvoste.qtalk.ui.calls.theme.*
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.icon_notification_delete
import qtalk.shared.generated.resources.icon_notifications

@Composable
internal fun NotificationsSection(
    notifications: List<SavedNotification>,
    onDeleteNotifications: (Set<String>) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    contacts: List<Contact>,
    onAddContact: () -> Unit,
    onShowAllContacts: () -> Unit,
    onSelectContact: (Contact) -> Unit,
    canTransfer: Boolean,
    onTransferEmployee: (Contact) -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    searchFocusRequester: FocusRequester,
    availableUpdateVersion: String?,
    onOpenUpdate: () -> Unit,
    storageError: String? = null
) {
    var search by rememberSaveable { mutableStateOf("") }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(0) }
    var deletingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val category = when (selectedFilter) {
        1 -> NotificationCategory.UPDATE
        2 -> NotificationCategory.SYSTEM
        else -> null
    }
    val visible = notifications.filter { notification ->
        !notification.deleted &&
            (category == null || notification.category == category) &&
            (search.isBlank() || (notification.title + notification.description + notification.version)
                .contains(search, ignoreCase = true))
    }.sortedByDescending { it.timestamp }

    if (deletingIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deletingIds = emptySet() },
            title = { Text(uiText("Удалить уведомления?"), color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteNotifications(deletingIds)
                    deletingIds = emptySet()
                }) { Text(uiText("Да"), color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deletingIds = emptySet() }) { Text(uiText("Нет"), color = TextPrimary) }
            }
        )
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
            contacts, compact, "Все  ›", onShowAllContacts, onSelectContact,
            onAddContact, canTransfer, onTransferEmployee
        )
        Spacer(Modifier.height(if (compact) 28.dp else 32.dp))
        storageError?.let {
            Text(uiText(it), color = Danger, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedBar(
                options = listOf("Все", "Обновления", "Система"),
                selectedIndex = selectedFilter,
                onSelect = { selectedFilter = it }
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(40.dp).background(ControlBackground, CircleShape)
                    .clickable { deletingIds = visible.map { it.id }.toSet() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(Res.drawable.icon_notification_delete),
                    uiText("Удалить"),
                    Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(TextSecondary)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (visible.isEmpty()) {
            EmptyMessage("Новых уведомлений нет")
        } else {
            val now = Clock.System.now().toEpochMilliseconds()
            val groups = visible.groupBy { historyDateHeading(it.timestamp, now) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (heading, entries) ->
                    item(key = "day:$heading") {
                        Text(
                            uiText(heading), color = TextPrimary, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(entries, key = SavedNotification::id) { notification ->
                        NotificationCard(
                            notification = notification,
                            expanded = expandedId == notification.id,
                            canOpenUpdate = notification.category == NotificationCategory.UPDATE &&
                                notification.version == availableUpdateVersion,
                            onExpand = { expandedId = if (expandedId == notification.id) null else notification.id },
                            onDelete = { deletingIds = setOf(notification.id) },
                            onOpenUpdate = onOpenUpdate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: SavedNotification,
    expanded: Boolean,
    canOpenUpdate: Boolean,
    onExpand: () -> Unit,
    onDelete: () -> Unit,
    onOpenUpdate: () -> Unit
) {
    val isUpdate = notification.category == NotificationCategory.UPDATE
    val description = if (isUpdate) {
        releaseNotesForLanguage(notification.description, LocalAppLanguage.current.language)
    } else {
        uiText(notification.description)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ControlBackground)
            .onSecondaryClick(onClick = onDelete).animateContentSize()
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(38.dp).background(Primary.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painterResource(Res.drawable.icon_notifications), null, Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(Primary)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText(notification.title), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val subtitle = if (isUpdate) "QTalk ${notification.version} готов к установке" else "Система"
                Text(uiText(subtitle), color = TextSecondary, fontSize = 11.sp)
            }
            Text(uiText(if (expanded) "Свернуть  ⌃" else "Подробнее  ⌄"), color = Primary, fontSize = 11.sp)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha = 0.14f)))
            Column(Modifier.fillMaxWidth().padding(horizontal = 66.dp, vertical = 14.dp)) {
                Text(uiText(if (isUpdate) "Что нового" else "Система"),
                    color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    description.takeIf(String::isNotBlank) ?: uiText("Описание этого релиза пока не добавлено."),
                    color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp,
                    maxLines = 12, overflow = TextOverflow.Ellipsis
                )
                if (canOpenUpdate) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onOpenUpdate) {
                        Text(uiText("Перейти к обновлению  ›"), color = Primary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
