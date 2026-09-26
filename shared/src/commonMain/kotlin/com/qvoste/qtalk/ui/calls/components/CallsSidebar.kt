package com.qvoste.qtalk.ui.calls.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.registrationText
import com.qvoste.qtalk.ui.calls.sectionIcon
import com.qvoste.qtalk.ui.calls.model.AppSection
import com.qvoste.qtalk.ui.calls.model.colorFor
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.RegistrationState
import org.jetbrains.compose.resources.painterResource
import qtalk.shared.generated.resources.Res
import qtalk.shared.generated.resources.logo_white_transparent

@Composable
internal fun Sidebar(
    selected: AppSection,
    registration: RegistrationState,
    compact: Boolean,
    onSelect: (AppSection) -> Unit
) {
    Column(
        Modifier.width(if (compact) 70.dp else 214.dp).fillMaxHeight().padding(
            start = if (compact) 14.dp else 39.dp,
            top = 39.dp,
            end = if (compact) 9.dp else 18.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Image(
                    painterResource(Res.drawable.logo_white_transparent),
                    "QTalk",
                    Modifier.size(41.dp)
                )
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
            NavItem(AppSection.CALLS, selected, compact, onSelect)
            NavItem(AppSection.CONTACTS, selected, compact, onSelect)
            Spacer(Modifier.height(if (compact) 8.dp else 22.dp))
            if (!compact) SectionLabel("СИСТЕМА")
            NavItem(AppSection.SETTINGS, selected, compact, onSelect)
            NavItem(AppSection.NOTIFICATIONS, selected, compact, onSelect)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar("QT", colorFor("QTalk"), 42)
            if (!compact) {
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
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 9.dp))
}

@Composable
private fun NavItem(
    section: AppSection,
    selected: AppSection,
    compact: Boolean,
    onSelect: (AppSection) -> Unit
) {
    val active = section == selected
    val itemColor by animateColorAsState(if (active) TextPrimary else TextSecondary, tween(160))
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable { onSelect(section) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painterResource(sectionIcon(section)),
            section.title,
            Modifier.size(if (compact) 32.dp else 21.dp),
            colorFilter = ColorFilter.tint(itemColor)
        )
        if (!compact) {
            Spacer(Modifier.width(16.dp))
            Text(
                section.title,
                color = itemColor,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Spacer(Modifier.weight(1f))
        if (active) Box(Modifier.width(3.dp).height(28.dp).background(Primary, RoundedCornerShape(2.dp)))
    }
}
