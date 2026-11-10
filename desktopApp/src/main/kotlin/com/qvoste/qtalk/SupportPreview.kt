package com.qvoste.qtalk

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.qvoste.qtalk.support.*
import com.qvoste.qtalk.ui.calls.i18n.*
import com.qvoste.qtalk.ui.calls.theme.*

/** Explicit isolated entrypoint: no dial bridge, SIP, account stores, preferences, updates or real logs. */
internal fun supportPreview() = application {
    val session = remember { SupportSession(DesktopSupportService(null,
        DiagnosticsCollector(emptyList(), DiagnosticSanitizer(), QTalkBuildInfo.VERSION, installMethod = "test"))) }
    var language by remember { mutableStateOf(AppLanguage.RUSSIAN) }
    Window(onCloseRequest = ::exitApplication, title = "QTalk — Support local preview",
        state = rememberWindowState(width = 860.dp, height = 840.dp)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            CallsTheme(ThemeMode.DARK) {
                CompositionLocalProvider(LocalAppLanguage provides LanguageSettings(language) { language = it }) {
                    SupportScreen(session, ::exitApplication, Modifier.fillMaxSize())
                }
            }
        }
    }
}
