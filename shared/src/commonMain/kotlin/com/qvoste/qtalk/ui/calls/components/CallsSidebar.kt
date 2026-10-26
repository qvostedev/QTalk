package com.qvoste.qtalk.ui.calls.components

import com.qvoste.qtalk.ui.calls.i18n.uiText

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.registrationText
import com.qvoste.qtalk.ui.calls.sectionIcon
import com.qvoste.qtalk.ui.calls.model.AppSection
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.RegistrationState
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.logo_white_transparent
import qtalk.shared.generated.resources.logo_light_transparent

@Composable
internal fun Sidebar(
    selected: AppSection,
    registration: RegistrationState,
    compact: Boolean,
    accountContact: Contact?,
    hasNotifications: Boolean = false,
    onAccountClick: () -> Unit = {},
    onSelect: (AppSection) -> Unit
) {
    val accountInteractions = remember { MutableInteractionSource() }
    val navCenters = remember(compact) { mutableStateMapOf<AppSection, Float>() }
    val primary = Primary
    fun navPosition(section: AppSection) = Modifier.onGloballyPositioned {
        navCenters[section] = it.positionInParent().y + it.size.height / 2f
    }
    Column(
        Modifier.width(if (compact) 80.dp else 214.dp).fillMaxHeight().padding(
            start = if (compact) 21.dp else 39.dp,
            top = 16.dp,
            end = if (compact) 5.dp else 18.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (compact) {
                        Box(Modifier.width(46.dp).height(41.dp), contentAlignment = Alignment.Center) {
                            Image(
                                painterResource(if (IsLightTheme) Res.drawable.logo_light_transparent else Res.drawable.logo_white_transparent),
                                "QTalk",
                                Modifier.size(39.dp)
                            )
                        }
                    } else {
                        Image(
                            painterResource(if (IsLightTheme) Res.drawable.logo_light_transparent else Res.drawable.logo_white_transparent),
                            "QTalk",
                            Modifier.size(41.dp)
                        )
                    }
                    if (!compact) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "QTalk",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(if (compact) 18.dp else 38.dp))
                if (!compact) SectionLabel("ОСНОВНОЕ")
                NavItem(AppSection.CALLS, selected, compact, onSelect, modifier = navPosition(AppSection.CALLS))
                NavItem(AppSection.CONTACTS, selected, compact, onSelect, modifier = navPosition(AppSection.CONTACTS))
                Spacer(Modifier.height(if (compact) 8.dp else 22.dp))
                if (!compact) SectionLabel("СИСТЕМА")
                NavItem(AppSection.SETTINGS, selected, compact, onSelect, modifier = navPosition(AppSection.SETTINGS))
                NavItem(AppSection.NOTIFICATIONS, selected, compact, onSelect, hasNotifications, navPosition(AppSection.NOTIFICATIONS))
            }
            navCenters[selected]?.let { target ->
                val center by animateFloatAsState(target, tween(260), label = "sidebarIndicator")
                Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(primary,
                        topLeft = Offset(size.width - 3.dp.toPx(), center - 14.dp.toPx()),
                        size = Size(3.dp.toPx(), 28.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
        }

        if (compact) {
            Box(Modifier.clickable(interactionSource = accountInteractions, indication = null, onClick = onAccountClick)) {
            AccountAvatar(contact = null, registration = registration)
            }
        } else {
            Row(
                Modifier.requiredWidth(177.dp).height(46.dp).clickable(interactionSource = accountInteractions, indication = null, onClick = onAccountClick),
                verticalAlignment = Alignment.Top
            ) {
                AccountAvatar(contact = accountContact, registration = registration)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        accountContact?.name ?: "QTalk",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        uiText(registrationText(registration)),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        maxLines = 1
                    )
                }
                val chevronColor = TextSecondary
                Canvas(Modifier.padding(top = 15.dp).size(4.dp, 8.dp)) {
                    drawLine(
                        color = chevronColor,
                        start = Offset(0f, 0f),
                        end = Offset(3.dp.toPx(), 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = chevronColor,
                        start = Offset(3.dp.toPx(), 3.dp.toPx()),
                        end = Offset(0f, 6.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountAvatar(contact: Contact?, registration: RegistrationState) {
    Box(Modifier.size(46.dp)) {
        if (contact == null) {
            AvatarWithStatus(
                "QT", "QTalk", 46,
                if (registration == RegistrationState.REGISTERED) Online else TextSecondary
            )
        } else {
            ContactAvatarWithStatus(
                contact.name, contact.telegram, 46,
                if (registration == RegistrationState.REGISTERED) contact.status.color else TextSecondary,
                contact.avatarUrl
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(uiText(text), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun NavItem(
    section: AppSection,
    selected: AppSection,
    compact: Boolean,
    onSelect: (AppSection) -> Unit,
    showBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val active = section == selected
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val baseColor = if (active) TextPrimary else TextSecondary
    val itemColor by animateColorAsState(
        if (hovered && !active) lerp(baseColor, TextPrimary, 0.12f) else baseColor,
        tween(260), label = "sidebarItemColor"
    )
    val weight by animateFloatAsState(
        if (active) 600f else 400f, tween(260), label = "sidebarItemWeight"
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp)) {
        Row(
            Modifier.offset(x = (-8).dp).requiredWidth(maxWidth + 16.dp).fillMaxHeight()
                .clip(RoundedCornerShape(10.dp)).clickable(interactionSource = interactions, indication = null) { onSelect(section) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compact) {
                Box(Modifier.width(46.dp).height(44.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(sectionIcon(section)),
                        section.title,
                        Modifier.size(26.dp),
                        colorFilter = ColorFilter.tint(itemColor)
                    )
                }
            } else {
                Image(
                    painterResource(sectionIcon(section)),
                    section.title,
                    Modifier.size(21.dp),
                    colorFilter = ColorFilter.tint(itemColor)
                )
            }
            if (!compact) {
                Spacer(Modifier.width(16.dp))
                Text(
                    uiText(section.title),
                    color = itemColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight(weight.toInt())
                )
            }
            if (showBadge) {
                Spacer(Modifier.width(if (compact) 2.dp else 8.dp))
                Box(Modifier.size(7.dp).background(Danger, CircleShape))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
