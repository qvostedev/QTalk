package com.qvoste.qtalk.support

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportLogDirectoriesTest {
    private val fixture = Path.of(System.getProperty("java.io.tmpdir"), "support-layout-fixture").toAbsolutePath()
    private val home = fixture.resolve("home")
    private val windows = fixture.resolve("windows-local")
    private val state = fixture.resolve("state")

    @Test
    fun linuxUsesItsStateDirectoryEvenIfAWindowsVariableExists() {
        assertEquals(listOf(state.resolve("QTalk/logs")), DiagnosticsCollector.defaultLogDirectories(
            osName = "Linux", userHome = home.toString(), localAppData = windows.toString(), xdgStateHome = state.toString()))
    }

    @Test
    fun linuxFallsBackToPrivateHomeStateAndIgnoresRelativeXdgPaths() {
        for (xdg in listOf(null, "", "relative-state")) {
            assertEquals(listOf(home.resolve(".local/state/QTalk/logs")), DiagnosticsCollector.defaultLogDirectories(
                osName = "Linux", userHome = home.toString(), localAppData = null, xdgStateHome = xdg))
        }
    }

    @Test
    fun windowsKeepsTheExistingLogLocation() {
        assertEquals(listOf(windows.resolve("QTalk/logs")), DiagnosticsCollector.defaultLogDirectories(
            osName = "Windows 11", userHome = home.toString(), localAppData = windows.toString(), xdgStateHome = state.toString()))
    }

    @Test
    fun macUsesTheApplicationLogDirectory() {
        assertEquals(listOf(home.resolve("Library/Logs/QTalk")), DiagnosticsCollector.defaultLogDirectories(
            osName = "Mac OS X", userHome = home.toString(), localAppData = null, xdgStateHome = null))
    }
}
