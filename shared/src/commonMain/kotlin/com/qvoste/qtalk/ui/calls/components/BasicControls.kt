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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qvoste.qtalk.ui.calls.theme.TextPrimary
import com.qvoste.qtalk.ui.calls.theme.TextSecondary
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
    Column(modifier.padding(48.dp)) {
        Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
internal fun Avatar(initials: String, color: Color, size: Int) {
    Box(Modifier.size(size.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = TextPrimary, fontSize = (size * 0.3).sp, fontWeight = FontWeight.Medium)
    }
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
        Image(painterResource(icon), null, Modifier.size((size * 0.42).dp))
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
        Image(painterResource(icon), null, Modifier.size(iconWidth.dp, iconHeight.dp))
    }
}
