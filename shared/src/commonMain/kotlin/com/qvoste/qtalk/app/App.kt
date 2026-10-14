package com.qvoste.qtalk.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.qvoste.qtalk.domain.calls.ConnectSipUseCase
import com.qvoste.qtalk.ui.calls.CallsScreen
import com.qvoste.qtalk.ui.calls.CallsViewModel
import com.qvoste.qtalk.ui.calls.ExternalDialRequest
import com.qvoste.qtalk.ui.calls.theme.CallsTheme
import com.qvoste.qtalk.ui.calls.theme.ThemeMode
import com.qvoste.qtalk.ui.calls.model.ContactStore
import com.qvoste.qtalk.ui.calls.model.ContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.DisabledContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.InMemoryContactStore
import com.qvoste.qtalk.ui.calls.model.ContactNoteStore
import com.qvoste.qtalk.ui.calls.model.InMemoryContactNoteStore
import com.qvoste.qtalk.ui.calls.model.CallHistoryStore
import com.qvoste.qtalk.ui.calls.model.InMemoryCallHistoryStore
import com.qvoste.qtalk.voip.VoipEngine
import com.qvoste.qtalk.voip.InMemorySipAccountStore
import com.qvoste.qtalk.voip.SipAccountStore

@Composable
fun App(
    voipEngine: VoipEngine,
    accountStore: SipAccountStore = InMemorySipAccountStore(),
    contactStore: ContactStore = InMemoryContactStore(),
    contactDatabaseService: ContactDatabaseService = DisabledContactDatabaseService,
    contactNoteStore: ContactNoteStore = InMemoryContactNoteStore(),
    callHistoryStore: CallHistoryStore = InMemoryCallHistoryStore(),
    themeMode: ThemeMode = ThemeMode.DARK,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    appVersion: String = "1.0.1",
    onCheckForUpdates: suspend ((String) -> Unit) -> Unit = { it("Проверка обновлений недоступна") },
    callOverlayCollapsed: Boolean = false,
    onCollapseCall: () -> Unit = {},
    externalDialRequest: ExternalDialRequest? = null,
    onExternalDialRequestConsumed: (Long) -> Unit = {}
) {
    // Запускаем VoIP-движок один раз на всё время жизни приложения.
    LaunchedEffect(voipEngine) {
        voipEngine.start()
    }

    val connectSipUseCase = remember(voipEngine) {
        ConnectSipUseCase(voipEngine)
    }

    val callsViewModel = remember(
        voipEngine,
        accountStore,
        contactStore,
        contactDatabaseService,
        contactNoteStore,
        callHistoryStore
    ) {
        CallsViewModel(
            voipEngine,
            connectSipUseCase,
            accountStore,
            contactStore,
            contactDatabaseService,
            contactNoteStore,
            callHistoryStore
        )
    }

    DisposableEffect(callsViewModel) {
        onDispose { callsViewModel.close() }
    }

    CallsTheme(themeMode) {
        MaterialTheme(colorScheme = if (themeMode == ThemeMode.LIGHT) {
            lightColorScheme()
        } else darkColorScheme()) {
            CallsScreen(
                callsViewModel, themeMode, onThemeModeChange,
                appVersion, onCheckForUpdates, callOverlayCollapsed, onCollapseCall,
                externalDialRequest, onExternalDialRequestConsumed
            )
        }
    }
}
