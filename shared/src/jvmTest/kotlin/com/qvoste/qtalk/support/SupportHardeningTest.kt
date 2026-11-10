package com.qvoste.qtalk.support

import java.nio.file.Files
import kotlinx.coroutines.*
import kotlin.test.*

class SupportHardeningTest {
    @Test fun improvementSuggestionShipsNoDiagnostics() {
        val root = Files.createTempDirectory("qtalk-support-synthetic-logs-")
        try {
            Files.writeString(root.resolve("qtalk-0.log"), "2026-01-01 QTalk event=private-detail\n")
            val collector = DiagnosticsCollector(listOf(root), DiagnosticSanitizer(), "test")
            val problem = unzip(collector.prepare(SupportDraft("Title", "Synthetic description")).zip)
            val suggestion = unzip(collector.prepare(SupportDraft(kind = SupportKind.IMPROVEMENT,
                title = "Title", description = "Synthetic suggestion")).zip)
            assertTrue(problem.keys.containsAll(listOf("report.json", "system.json", "manifest.json")))
            assertTrue(problem.keys.any { it.startsWith("logs/") })
            assertEquals(setOf("report.json", "manifest.json"), suggestion.keys)
            assertFalse(suggestion.values.any { it.decodeToString().contains("private-detail") })
            assertTrue(suggestion.getValue("manifest.json").decodeToString().contains("no QTalk logs"))
            assertTrue(suggestion.getValue("report.json").decodeToString().contains("Предложение: Title"))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun cancellationAfterUploadLeavesDraftAndUncertaintyVisible() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val service = object : SupportService {
            override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt {
                stage(SupportStage.WAITING); entered.complete(Unit); awaitCancellation()
            }
            override suspend fun chooseScreenshot(): SupportAttachment? = null
        }
        val session = SupportSession(service)
        session.edit { SupportDraft("Title", "Synthetic description") }
        val job = launch { session.send() }; entered.await(); job.cancelAndJoin()
        assertEquals(SupportError.DELIVERY_UNCERTAIN, session.state.value.error)
        assertEquals("Title", session.state.value.draft.title); assertFalse(session.state.value.busy)
    }
    @Test fun collectorRejectsOversizedUserFieldsBeforeAllocatingArchive() {
        assertFailsWith<SupportFailure> {
            DiagnosticsCollector(emptyList(), DiagnosticSanitizer(), "test").prepare(SupportDraft("x".repeat(100000), "desc"))
        }
    }
    @Test fun screenshotNameCannotInjectUiLines() {
        // Report text is user authored; filenames displayed next to controls must stay one line.
        assertEquals("filename.png", safeScreenshotFilename("filename.png\r\n"))
    }
}
