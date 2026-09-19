package com.qvoste.qtalk

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.qvoste.qtalk.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "QTalk",
    ) {
        App()
    }
}