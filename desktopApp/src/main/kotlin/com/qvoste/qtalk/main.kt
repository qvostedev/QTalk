package com.qvoste.qtalk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import com.qvoste.qtalk.ui.calls.i18n.LanguageSettings
import com.qvoste.qtalk.ui.calls.i18n.LocalAppLanguage
import com.qvoste.qtalk.ui.calls.i18n.translateUiText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.qvoste.qtalk.ui.calls.theme.InterfaceScale
import com.qvoste.qtalk.ui.calls.theme.LocalInterfaceScale
import com.qvoste.qtalk.ui.calls.theme.LocalUnscaledDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.qvoste.qtalk.app.App
import com.qvoste.qtalk.ui.calls.ExternalDialRequest
import com.qvoste.qtalk.ui.calls.components.CompactCallWidget
import com.qvoste.qtalk.ui.calls.model.GoogleSheetsContactDatabaseService
import com.qvoste.qtalk.ui.calls.model.GoogleSheetsEmployeePresenceService
import com.qvoste.qtalk.ui.calls.model.EmployeeStatus
import com.qvoste.qtalk.ui.calls.model.PreferencesContactStore
import com.qvoste.qtalk.ui.calls.model.PreferencesContactNoteStore
import com.qvoste.qtalk.ui.calls.model.PreferencesCallHistoryStore
import com.qvoste.qtalk.ui.calls.model.matchesCallNumber
import com.qvoste.qtalk.ui.calls.theme.qTalkWindowIcon
import com.qvoste.qtalk.ui.calls.theme.qTalkTrayIcon
import com.qvoste.qtalk.ui.calls.theme.PreferencesThemeStore
import com.qvoste.qtalk.ui.calls.theme.CallsTheme
import com.qvoste.qtalk.ui.calls.theme.ThemeMode
import com.qvoste.qtalk.voip.CallState
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.LinphoneVoipEngine
import com.qvoste.qtalk.voip.PreferencesSipAccountStore
import java.awt.BorderLayout
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val widgetSmokeIncoming = "--widget-smoke-incoming" in args
    val widgetSmokeTest = "--widget-smoke-test" in args || widgetSmokeIncoming
    // Telegram может быть доступен только через системный прокси Windows.
    System.setProperty("java.net.useSystemProxies", "true")

    var externalDialRequest by mutableStateOf<ExternalDialRequest?>(null)
    var externalRequestId = 0L
    var windowActivationRequest by mutableStateOf(0L)
    val dialBridge = ExternalDialBridge.start(
        args,
        onDial = { number ->
            EventQueue.invokeLater {
                externalDialRequest = ExternalDialRequest(++externalRequestId, number)
                windowActivationRequest++
            }
        },
        onShow = { EventQueue.invokeLater { windowActivationRequest++ } }
    ) ?: return
    dialBridge.initialNumber?.let { number ->
        externalDialRequest = ExternalDialRequest(++externalRequestId, number)
    }

    try {
    application {
        val voipEngine = remember { LinphoneVoipEngine() }
        val accountStore = remember { PreferencesSipAccountStore() }
        val contactStore = remember { PreferencesContactStore() }
        val contactNoteStore = remember { PreferencesContactNoteStore() }
        val callHistoryStore = remember { PreferencesCallHistoryStore() }
        val contactDatabaseService = remember { GoogleSheetsContactDatabaseService() }
        val employeePresenceService = remember { GoogleSheetsEmployeePresenceService() }
        val themeStore = remember { PreferencesThemeStore() }
        val appVersion = remember {
            GitHubReleaseUpdater::class.java.`package`?.implementationVersion ?: "1.0.8"
        }
        val updater = remember(appVersion) { GitHubReleaseUpdater(appVersion) }
        var availableUpdateVersion by remember { mutableStateOf<String?>(null) }
        var availableUpdateDescription by remember { mutableStateOf<String?>(null) }
        var themeMode by remember { mutableStateOf(themeStore.load()) }
        var interfaceScale by remember { mutableStateOf(themeStore.loadScale()) }
        var appLanguage by remember { mutableStateOf(themeStore.loadLanguage()) }
        val scope = rememberCoroutineScope()
        val screenBounds = remember {
            val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
            Dimension(config.bounds.width - insets.left - insets.right, config.bounds.height - insets.top - insets.bottom)
        }
        val windowState = rememberWindowState(
            width = minOf(1440, screenBounds.width).dp,
            height = minOf(1024, screenBounds.height - 40).dp
        )
        val call by voipEngine.callStatus.collectAsState()
        val traySupported = remember { SystemTray.isSupported() }
        var mainWindowVisible by remember { mutableStateOf(!widgetSmokeTest) }
        var callOverlayCollapsed by remember { mutableStateOf(false) }

        LaunchedEffect(updater) {
            while (true) {
                runCatching { updater.availableRelease() }
                    .onSuccess { release ->
                        availableUpdateVersion = release?.version
                        availableUpdateDescription = release?.description
                    }
                delay(10L * 60L * 1_000L)
            }
        }

        val widgetCall = if (widgetSmokeTest) {
            CallStatus(
                if (widgetSmokeIncoming) CallState.INCOMING else CallState.ACTIVE,
                "+7 900 000-00-00",
                startedAtEpochMillis = System.currentTimeMillis(),
                connectedAtEpochMillis = if (widgetSmokeIncoming) 0L else System.currentTimeMillis()
            )
        } else {
            call
        }

        val caller = remember(call.number, call.state) {
            contactStore.load().firstOrNull { it.matchesCallNumber(call.number) }
        }
        val callCanUseWidget = widgetCall.state.isInProgress
        val compactCallVisible = if (widgetSmokeTest) {
            !mainWindowVisible
        } else {
            callCanUseWidget && (!mainWindowVisible || callOverlayCollapsed)
        }

        fun openMainWindow() {
            mainWindowVisible = true
            callOverlayCollapsed = false
        }

        LaunchedEffect(windowActivationRequest) {
            if (windowActivationRequest > 0L) openMainWindow()
        }

        fun closeApplication() {
            scope.launch {
                try {
                    val extension = accountStore.load()?.username.orEmpty().filter(Char::isDigit)
                    if (extension.isNotBlank()) {
                        runCatching {
                            employeePresenceService.publish(extension, EmployeeStatus.OFFLINE)
                        }
                    }
                    voipEngine.stop()
                } finally {
                    exitApplication()
                }
            }
        }

        val widgetCallState = remember { mutableStateOf(widgetCall) }
        val widgetCallerState = remember { mutableStateOf(caller) }
        val widgetThemeState = remember { mutableStateOf(themeMode) }
        val widgetLanguageState = remember { mutableStateOf(appLanguage) }
        SideEffect {
            widgetCallState.value = widgetCall
            widgetCallerState.value = if (widgetSmokeTest) null else caller
            widgetThemeState.value = themeMode
            widgetLanguageState.value = appLanguage
        }
        val compactWindow = remember {
            lateinit var floatingWindow: JWindow
            val initialBackground = if (themeMode == ThemeMode.LIGHT) java.awt.Color.WHITE else java.awt.Color(9, 11, 13)
            val composePanel = ComposePanel().apply {
                isOpaque = true
                background = initialBackground
                preferredSize = Dimension(280, 68)
                setContent {
                    val widgetCall by widgetCallState
                    val widgetCaller by widgetCallerState
                    val widgetTheme by widgetThemeState
                    val widgetLanguage by widgetLanguageState
                    CompositionLocalProvider(LocalAppLanguage provides LanguageSettings(widgetLanguage)) {
                        CallsTheme(widgetTheme) {
                            CompactCallWidget(
                                state = widgetCall.state,
                                number = widgetCall.number,
                                callerName = widgetCaller?.name,
                                callerTelegram = widgetCaller?.telegram.orEmpty(),
                                callerAvatarUrl = widgetCaller?.avatarUrl.orEmpty(),
                                connectedAtEpochMillis = widgetCall.connectedAtEpochMillis,
                                onOpen = ::openMainWindow,
                                onAccept = { scope.launch { voipEngine.acceptCall() } },
                                onDecline = { scope.launch { voipEngine.declineCall() } },
                                onHangUp = { scope.launch { voipEngine.hangUp() } },
                                onMicrophoneMutedChange = { muted ->
                                    scope.launch { voipEngine.setMicrophoneMuted(muted) }
                                },
                                onSpeakerMutedChange = { muted ->
                                    scope.launch { voipEngine.setSpeakerMuted(muted) }
                                }
                            )
                        }
                    }
                }
            }
            val dragHandler = AWTEventListener { awtEvent ->
                val event = awtEvent as? MouseEvent ?: return@AWTEventListener
                val component = event.component as? Component ?: return@AWTEventListener
                if (component !== composePanel && !SwingUtilities.isDescendingFrom(component, composePanel)) {
                    return@AWTEventListener
                }

                when (event.id) {
                    MouseEvent.MOUSE_PRESSED -> {
                        composePanel.putClientProperty("qtalk.pointerStartY", event.yOnScreen)
                        composePanel.putClientProperty("qtalk.windowStartY", floatingWindow.y)
                    }
                    MouseEvent.MOUSE_DRAGGED -> {
                        val pointerStartY = composePanel.getClientProperty("qtalk.pointerStartY") as? Int
                            ?: return@AWTEventListener
                        val windowStartY = composePanel.getClientProperty("qtalk.windowStartY") as? Int
                            ?: return@AWTEventListener
                        moveCompactWindowToY(
                            floatingWindow,
                            windowStartY + event.yOnScreen - pointerStartY
                        )
                    }
                }
            }
            Toolkit.getDefaultToolkit().addAWTEventListener(
                dragHandler,
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
            )
            floatingWindow = JWindow().apply {
                type = java.awt.Window.Type.UTILITY
                isAlwaysOnTop = true
                setAutoRequestFocus(false)
                focusableWindowState = false
                background = initialBackground
                contentPane.layout = BorderLayout()
                contentPane.add(composePanel, BorderLayout.CENTER)
                pack()
                runCatching { opacity = 0.94f }
                applyQTalkCompactWindowStyle(this)
            }
            floatingWindow.rootPane.putClientProperty("qtalk.dragHandler", dragHandler)
            floatingWindow
        }
        DisposableEffect(compactWindow) {
            onDispose {
                val dragHandler = compactWindow.rootPane
                    .getClientProperty("qtalk.dragHandler") as? AWTEventListener
                if (dragHandler != null) {
                    Toolkit.getDefaultToolkit().removeAWTEventListener(dragHandler)
                }
                compactWindow.dispose()
            }
        }
        LaunchedEffect(themeMode, compactWindow) {
            EventQueue.invokeLater {
                val background = if (themeMode == ThemeMode.LIGHT) java.awt.Color.WHITE else java.awt.Color(9, 11, 13)
                compactWindow.background = background
                compactWindow.contentPane.background = background
                (compactWindow.contentPane.getComponent(0) as? ComposePanel)?.background = background
                compactWindow.repaint()
            }
        }
        LaunchedEffect(compactCallVisible, widgetCall.number) {
            EventQueue.invokeLater {
                if (compactCallVisible) {
                    if (!compactWindow.isVisible) positionCompactWindow(compactWindow)
                    compactWindow.isVisible = true
                    compactWindow.isAlwaysOnTop = true
                    compactWindow.toFront()
                } else {
                    compactWindow.isVisible = false
                }
            }
        }

        LaunchedEffect(call.state) {
            when {
                call.state == CallState.INCOMING -> callOverlayCollapsed = true
                !call.state.isInProgress -> callOverlayCollapsed = false
            }
        }
        if (traySupported) {
            Tray(
                icon = qTalkTrayIcon(),
                tooltip = "QTalk",
                onAction = ::openMainWindow,
                menu = {
                    Item(translateUiText("Открыть QTalk", appLanguage), onClick = ::openMainWindow)
                    Separator()
                    Item(translateUiText("Выход", appLanguage), onClick = ::closeApplication)
                }
            )
        }

        Window(
            onCloseRequest = {
                if (traySupported) mainWindowVisible = false else closeApplication()
            },
            visible = mainWindowVisible,
            state = windowState,
            title = qTalkNativeWindowTitle(),
            icon = qTalkWindowIcon(),
            // Нативная рамка Windows сохраняет resize, snap layouts и системные кнопки.
            undecorated = false,
            resizable = true
        ) {
            val callOverlayVisible = !callOverlayCollapsed &&
                (call.state == CallState.INCOMING || call.state == CallState.ACTIVE)
            DisposableEffect(window) {
                window.minimumSize = Dimension(minOf(760, screenBounds.width), minOf(520, screenBounds.height - 40))
                onDispose { }
            }
            DisposableEffect(window, themeMode) {
                val restoreWindowsTitleBar = applyQTalkWindowsTitleBar(window, themeMode == ThemeMode.LIGHT)
                onDispose(restoreWindowsTitleBar)
            }
            LaunchedEffect(window, themeMode, callOverlayVisible) {
                updateQTalkWindowsCaptionColors(window, themeMode == ThemeMode.LIGHT && !callOverlayVisible)
            }
            LaunchedEffect(mainWindowVisible, windowActivationRequest) {
                if (mainWindowVisible) {
                    window.toFront()
                    window.requestFocus()
                }
            }

            BoxWithConstraints(Modifier.fillMaxSize().background(if (themeMode == ThemeMode.LIGHT) Color.White else Color(0xFF090B0D))) {
                val baseDensity = LocalDensity.current
                val scale = if (interfaceScale == 0f) {
                    minOf(1f, maxWidth.value / 1100f, maxHeight.value / 960f).coerceAtLeast(0.65f)
                } else interfaceScale
                CompositionLocalProvider(
                    LocalAppLanguage provides LanguageSettings(appLanguage) {
                        themeStore.saveLanguage(it)
                        appLanguage = it
                    },
                    LocalUnscaledDensity provides baseDensity,
                    LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale),
                    LocalInterfaceScale provides InterfaceScale(interfaceScale) {
                        themeStore.saveScale(it)
                        interfaceScale = it
                    }
                ) {
                if (!widgetSmokeTest) {
                    App(
                        voipEngine = voipEngine,
                        accountStore = accountStore,
                        contactStore = contactStore,
                        contactDatabaseService = contactDatabaseService,
                        contactNoteStore = contactNoteStore,
                        callHistoryStore = callHistoryStore,
                        employeePresenceService = employeePresenceService,
                        themeMode = themeMode,
                        onThemeModeChange = { selected ->
                            themeStore.save(selected)
                            themeMode = selected
                        },
                        appVersion = appVersion,
                        availableUpdateVersion = availableUpdateVersion,
                        availableUpdateDescription = availableUpdateDescription,
                        onCheckForUpdates = { onStatus ->
                            if (updater.checkAndInstall(onStatus)) closeApplication()
                        },
                        callOverlayCollapsed = callOverlayCollapsed,
                        onCollapseCall = { callOverlayCollapsed = true },
                        externalDialRequest = externalDialRequest,
                        onExternalDialRequestConsumed = { id ->
                            if (externalDialRequest?.id == id) externalDialRequest = null
                        }
                    )
                }
                }
            }
        }

    }
    } finally {
        dialBridge.close()
    }
}

private fun positionCompactWindow(window: JWindow) {
    val configuration = window.graphicsConfiguration
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val screen = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    window.setLocation(
        screen.x + insets.left + 16,
        screen.y + screen.height - insets.bottom - window.height - 150
    )
}

private fun moveCompactWindowToY(window: JWindow, requestedY: Int) {
    val configuration = window.graphicsConfiguration
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val screen = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    val minimumY = screen.y + insets.top + 12
    val maximumY = screen.y + screen.height - insets.bottom - window.height - 12
    window.setLocation(window.x, requestedY.coerceIn(minimumY, maximumY))
}
