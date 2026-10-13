package com.qvoste.qtalk.ui.calls.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.theme.TextPrimary
import com.qvoste.qtalk.ui.calls.theme.TextSecondary
import com.qvoste.qtalk.ui.calls.theme.ControlBackground
import com.qvoste.qtalk.ui.calls.theme.AppBackground
import com.qvoste.qtalk.ui.calls.theme.IsLightTheme
import com.qvoste.qtalk.ui.calls.theme.Online
import com.qvoste.qtalk.ui.calls.model.avatarGradientFor
import com.qvoste.qtalk.ui.calls.model.initialsForName
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun EmptySection(modifier: Modifier, title: String, text: String) {
    Column(modifier.padding(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 48.dp)) {
        Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
internal fun Avatar(initials: String, seed: String?, size: Int) {
    val colors = seed?.takeIf(String::isNotBlank)?.let(::avatarGradientFor)
        ?: listOf(ControlBackground, ControlBackground)
    Box(
        Modifier.size(size.dp).background(Brush.verticalGradient(colors), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = if (seed.isNullOrBlank()) TextPrimary else Color.White,
            fontSize = (size * 0.3).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ContactAvatar(name: String, telegram: String, size: Int) {
    val telegramAvatar = rememberTelegramAvatar(telegram)
    if (telegramAvatar == null) {
        Avatar(initialsForName(name), name, size)
    } else {
        Image(
            bitmap = telegramAvatar,
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High
        )
    }
}

@Composable
internal fun ContactAvatarWithStatus(
    name: String,
    telegram: String,
    size: Int,
    statusColor: Color? = null
) {
    Box(Modifier.size(size.dp)) {
        ContactAvatar(name, telegram, size)
        AvatarStatusBadge(size, statusColor)
    }
}

@Composable
internal fun AvatarWithStatus(
    initials: String,
    seed: String?,
    size: Int,
    statusColor: Color? = null
) {
    Box(Modifier.size(size.dp)) {
        Avatar(initials, seed, size)
        AvatarStatusBadge(size, statusColor)
    }
}

@Composable
private fun BoxScope.AvatarStatusBadge(size: Int, statusColor: Color?) {
    val centerX = size * (56.2592f / 62f)
    val centerY = size * (49.3699f / 62f)
    val cutoutRadius = size * (6.8889f / 62f)
    val dotRadius = size * (4.59259f / 62f)
    Box(
        Modifier.offset((centerX - cutoutRadius).dp, (centerY - cutoutRadius).dp)
            .size((cutoutRadius * 2).dp)
            .background(AppBackground, CircleShape)
    )
    Box(
        Modifier.offset((centerX - dotRadius).dp, (centerY - dotRadius).dp)
            .size((dotRadius * 2).dp)
            .background(statusColor ?: Online, CircleShape)
    )
}

@Composable
internal fun CircleIconAction(
    icon: DrawableResource,
    size: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(modifier.size(size.dp).background(color, CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Image(painterResource(icon), null, Modifier.size((size * 0.42).dp),
            colorFilter = if (IsLightTheme && color == ControlBackground) ColorFilter.tint(TextPrimary) else null)
    }
}

@Composable
internal fun CircleResourceAction(
    icon: DrawableResource,
    buttonSize: Int,
    iconWidth: Int,
    iconHeight: Int,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier.size(buttonSize.dp).background(color, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(icon), null, Modifier.size(iconWidth.dp, iconHeight.dp),
            colorFilter = if (IsLightTheme) ColorFilter.tint(TextSecondary) else null)
    }
}
