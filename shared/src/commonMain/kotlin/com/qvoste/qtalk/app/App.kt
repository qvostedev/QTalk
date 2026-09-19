package com.qvoste.qtalk.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsScreen
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.voip.FakeVoipEngine
import kotlinx.coroutines.MainScope

private val voipEngine = FakeVoipEngine()
private val connectSipUseCase = ConnectSipUseCase(voipEngine = voipEngine)
private val callsViewModel = CallsViewModel(
    voipEngine = voipEngine,
    connectSip = connectSipUseCase
)

@Composable
fun App() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CallsScreen(viewModel = callsViewModel)
        }
    }
}
