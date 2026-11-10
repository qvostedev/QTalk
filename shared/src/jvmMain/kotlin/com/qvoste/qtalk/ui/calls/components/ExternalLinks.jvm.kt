package com.qvoste.qtalk.ui.calls.components

import java.awt.Desktop
import java.net.URI

internal actual fun openExternalUrl(url: String): Boolean = try {
    val uri = URI(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) false
    else if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) false
    else { Desktop.getDesktop().browse(uri); true }
} catch (_: Exception) { false }
