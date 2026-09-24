package com.qvoste.qtalk.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsScreen
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.voip.VoipEngine
import com.qvoste.qtalk.voip.InMemorySipAccountStore
import com.qvoste.qtalk.voip.SipAccountStore

@Composable
fun App(
    voipEngine: VoipEngine,
    accountStore: SipAccountStore = InMemorySipAccountStore()
) {
    // Запускаем VoIP-движок один раз на всё время жизни приложения.
    LaunchedEffect(voipEngine) {
        voipEngine.start()
    }

    val connectSipUseCase = remember(voipEngine) {
        ConnectSipUseCase(voipEngine)
    }

    val callsViewModel = remember(voipEngine, accountStore) {
        CallsViewModel(voipEngine, connectSipUseCase, accountStore)
    }

    DisposableEffect(callsViewModel) {
        onDispose { callsViewModel.close() }
    }

    MaterialTheme {
        CallsScreen(callsViewModel)
    }
}
