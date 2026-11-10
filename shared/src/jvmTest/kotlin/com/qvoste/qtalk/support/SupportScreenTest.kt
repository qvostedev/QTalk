package com.qvoste.qtalk.support

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Image
import java.nio.file.Files
import java.nio.file.Path
import com.qvoste.qtalk.ui.calls.theme.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class SupportScreenTest {
    @Test fun receiptShowsOnlyReadableTicketNumber() = runComposeUiTest {
        val reportId = "1d8063d0-7e11-4cd8-a1c0-7990f772bdd8"
        val session = SupportSession(object : SupportService {
            override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit) =
                SupportReceipt(reportId, "QTK-00001")
            override suspend fun chooseScreenshot(): SupportAttachment? = null
        })
        session.edit { SupportDraft("Synthetic title", "Synthetic description") }
        setContent { CallsTheme(ThemeMode.DARK) { SupportScreen(session, {}) } }
        onNodeWithTag("support-send").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Обращение принято: №00001").performScrollTo().assertExists()
        onNodeWithText("QTK-00001", substring = true).assertDoesNotExist()
        onNodeWithText(reportId, substring = true).assertDoesNotExist()
    }
    @Test fun formNeedsValidTextOnlyAndSurvivesLeavingPanel() = runComposeUiTest {
        val session = SupportSession(UnsupportedSupportService)
        val shown = mutableStateOf(true)
        setContent { CallsTheme(ThemeMode.DARK) {
            if (shown.value) SupportScreen(session, { shown.value = false })
        } }
        val image = onRoot().captureToImage()
        val preview = Path.of("build", "support-local", "support-form.png")
        Files.createDirectories(preview.parent)
        Image.makeFromBitmap(image.asSkiaBitmap()).use { rendered -> Files.write(preview, rendered.encodeToData()!!.bytes) }
        onNodeWithTag("support-send").assertExists().assertIsNotEnabled()
        onNodeWithTag("support-title").performScrollTo().performTextInput("Synthetic title")
        onNodeWithTag("support-description").performScrollTo().performTextInput("Synthetic description")
        onNodeWithTag("support-send").assertIsEnabled()
        onNodeWithText("Назад к настройкам", substring = true).performClick()
        runOnIdle { assertFalse(shown.value); shown.value = true }
        onNodeWithTag("support-title").performScrollTo().assertTextContains("Synthetic title")
        onNodeWithTag("support-save").assertDoesNotExist()
        onNodeWithTag("support-consent").assertDoesNotExist()
    }
    @Test fun improvementSuggestionNeverCollectsDiagnostics() = runComposeUiTest {
        var delivered: SupportDraft? = null
        val session = SupportSession(object : SupportService {
            override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt {
                delivered = draft
                return SupportReceipt("report", "QTK-00002")
            }
            override suspend fun chooseScreenshot(): SupportAttachment? = null
        })
        setContent { CallsTheme(ThemeMode.DARK) { SupportScreen(session, {}) } }
        onNodeWithTag("support-kind-improvement").performScrollTo().performClick()
        onNodeWithTag("support-title").performScrollTo().performTextInput("Synthetic suggestion")
        onNodeWithTag("support-description").performScrollTo().performTextInput("A synthetic improvement idea")
        onNodeWithTag("support-send").performScrollTo().performClick()
        waitForIdle()
        runOnIdle { assertEquals(SupportKind.IMPROVEMENT, delivered?.kind) }
        onNodeWithTag("support-send").assertIsNotEnabled()
        onNodeWithTag("support-kind-problem").assertIsNotEnabled()
    }
}
