package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.i18n.AppLanguage
import com.qvoste.qtalk.ui.calls.i18n.releaseNotesForLanguage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The update notification renders release notes as plain text with a 12-line limit,
 * so markdown headings/emphasis look broken and everything below the cut is invisible.
 */
class ReleaseNotesFormatTest {
    private fun notesFiles(): List<Path> = Files.list(Path.of("..", "docs", "releases")).use { paths ->
        paths.filter { it.fileName.toString().endsWith(".md") }.toList()
    }

    @Test fun releaseNotesArePlainTextForTheUpdateNotification() {
        // Releases before 1.1.3 used markdown headings; the guard applies to new notes only.
        for (file in notesFiles().filter { versionOf(it) >= 1_001_003L }) {
            val body = file.readText()
            assertFalse(body.contains("###"), "${file.fileName}: markdown headings are shown literally in the app")
            assertFalse(body.contains("**"), "${file.fileName}: markdown emphasis is shown literally in the app")
        }
    }

    private fun versionOf(file: Path): Long {
        val match = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(file.fileName.toString()) ?: return 0
        val (major, minor, patch) = match.destructured
        return major.toLong() * 1_000_000 + minor.toLong() * 1_000 + patch.toLong()
    }

    @Test fun currentReleaseFitsTheVisibleArea() {
        val body = Path.of("..", "docs", "releases", "1.1.3.md").readText()
        for (language in AppLanguage.entries) {
            val visible = releaseNotesForLanguage(body, language)
            assertTrue(visible.isNotBlank())
            val lines = visible.lines()
            assertTrue(lines.size <= 12, "$language notes use ${lines.size} lines; the notification hides the rest")
            assertTrue(lines.none { it.length > 110 }, "$language notes contain a line that wraps and consumes the visible area")
        }
    }
}
