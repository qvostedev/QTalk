package com.qvoste.qtalk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.qvoste.qtalk.ui.calls.theme.qTalkWindowIcon
import com.qvoste.qtalk.app.App
import com.qvoste.qtalk.voip.LinphoneVoipEngine
import com.qvoste.qtalk.voip.PreferencesSipAccountStore
import kotlinx.coroutines.launch

fun main() = application {
    val voipEngine = remember { LinphoneVoipEngine() }
    val accountStore = remember { PreferencesSipAccountStore() }
    val scope = rememberCoroutineScope()
    val windowState = rememberWindowState(width = 1440.dp, height = 1024.dp)
    fun closeApplication() {
        scope.launch {
            try { voipEngine.stop() } finally { exitApplication() }
        }
    }

    Window(
        onCloseRequest = ::closeApplication,
        state = windowState,
        title = qTalkNativeWindowTitle(),
        icon = qTalkWindowIcon(),
        // Используем нативный заголовок ОС вместо самодельной панели окна.
        undecorated = false,
        resizable = true
    ) {
        DisposableEffect(window) {
            val restoreWindowsTitleBar = applyQTalkWindowsTitleBar(window)
            onDispose(restoreWindowsTitleBar)
        }

        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D10))) {
            App(voipEngine, accountStore)
        }
    }
}
