package com.qvoste.qtalk.support

import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.*

class SupportLoggerTest {
    @Test fun boundedOwnLoggerSanitizesBeforePersistenceAndDoesNotDuplicate() {
        val root = Files.createTempDirectory("qtalk-support-synthetic-logger-")
        val logger = Logger.getLogger("com.qvoste.qtalk.support-test")
        try {
            val sink = SupportAppLogger.install(root, DiagnosticSanitizer { listOf("known-value") }, "test")
            assertSame(sink, SupportAppLogger.install(root, DiagnosticSanitizer(), "test"))
            repeat(100) { logger.info("event=test known-value Authorization: Bearer hidden") }
            sink.close()
            val logs = Files.list(root).use { paths -> paths.filter { it.toString().endsWith(".log") }.toList() }
            assertTrue(logs.isNotEmpty()); assertTrue(logs.size <= 4)
            val text = logs.joinToString { Files.readString(it) }
            assertFalse(text.contains("known-value")); assertFalse(text.contains("hidden"))
        } finally { root.toFile().deleteRecursively() }
    }
}
