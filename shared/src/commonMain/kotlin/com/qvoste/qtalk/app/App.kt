package com.qvoste.qtalk.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsScreen
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.voip.VoipEngine
import androidx.compose.runtime.remember

@Composable
fun App(voipEngine: VoipEngine) {
    LaunchedEffect(voipEngine) {
        voipEngine.start()
    }

    val connectSipUseCase = remember(voipEngine) {
        ConnectSipUseCase(voipEngine)
    }

    val callsViewModel = remember(voipEngine) {
        CallsViewModel(voipEngine, connectSipUseCase
        )
    }

    DisposableEffect(callsViewModel) {
        onDispose { callsViewModel.close() }
    }

    MaterialTheme {
        CallsScreen(callsViewModel)
    }
}