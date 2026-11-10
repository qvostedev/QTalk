package com.qvoste.qtalk.support

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.logging.*

/** Only QTalk JUL records. No stderr interception, native SIP logging or third-party DEBUG. */
class SupportAppLogger private constructor(private val handler: FileHandler, private val logger: Logger,
    private val parentHandlers: Boolean) : AutoCloseable {
    override fun close() = synchronized(Companion) {
        logger.removeHandler(handler); handler.close()
        logger.useParentHandlers = parentHandlers
        if (active === this) active = null
    }
    companion object {
        private var active: SupportAppLogger? = null
        @Synchronized fun install(directory: Path, sanitizer: DiagnosticSanitizer, version: String): SupportAppLogger {
            active?.let { return it }
            check(DiagnosticsCollector.safePath(directory))
            Files.createDirectories(directory)
            for (index in 0..3) check(DiagnosticsCollector.safePath(directory.resolve("qtalk-app-$index.log")))
            val logger = Logger.getLogger("com.qvoste.qtalk")
            val parentHandlers = logger.useParentHandlers
            val handler = FileHandler(directory.resolve("qtalk-app-%g.log").toString(), 256 * 1024, 4, true).apply {
                encoding = "UTF-8"; level = Level.INFO
                formatter = object : Formatter() {
                    override fun format(record: LogRecord): String =
                        "${Instant.ofEpochMilli(record.millis)} QTalk $version ${record.level.name} ${sanitizer.clean(record.message.orEmpty().takeIf { it.length <= 16384 } ?: "[oversized record omitted]")}\n"
                }
            }
            logger.addHandler(handler)
            logger.useParentHandlers = false
            return SupportAppLogger(handler, logger, parentHandlers).also { active = it }
        }
    }
}
