package com.qvoste.qtalk

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.qvoste.qtalk.app.App
import com.qvoste.qtalk.voip.LinphoneVoipEngine

fun main() = application {
    val voipEngine = remember { LinphoneVoipEngine() }
    val scope = rememberCoroutineScope()

    Window(
        onCloseRequest = {
            scope.launch {
                try { voipEngine.stop() } finally { exitApplication() }
            }
        },
        title = "QTalk",
    ) {
        App(voipEngine)
    }
}