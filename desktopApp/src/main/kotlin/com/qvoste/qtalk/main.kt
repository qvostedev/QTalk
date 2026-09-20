package com.qvoste.qtalk

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.qvoste.qtalk.app.App
import com.qvoste.qtalk.voip.LinphoneVoipEngine

fun main() = application {
    val voipEngine = LinphoneVoipEngine()

    Window(
        onCloseRequest = ::exitApplication,
        title = "QTalk",
    ) {
        App(voipEngine)
    }
}