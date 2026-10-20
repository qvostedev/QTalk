package com.qvoste.qtalk.ui.calls.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
internal expect fun rememberTelegramAvatar(telegram: String, avatarUrl: String = ""): ImageBitmap?
