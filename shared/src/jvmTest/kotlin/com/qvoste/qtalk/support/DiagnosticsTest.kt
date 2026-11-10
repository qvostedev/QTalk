package com.qvoste.qtalk.support

import java.nio.file.Files
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.test.*

class DiagnosticsTest {
    @Test fun sanitizerDropsProtocolsAndSecretsAndMasksIdentifiers() {
        val clean = DiagnosticSanitizer { listOf("literal-secret!") }
        val dirty = "2026-01-01 QTalk event=test literal-secret! sip:alice@example.com alice@example.com +79991234567 192.168.1.10 https://private.example/a C:\\Users\\Alice\\logs\nAuthorization: Bearer hidden\n folded-secret\n"
        val result = clean.clean(dirty)
        for (secret in listOf("literal-secret!", "alice", "79991234567", "192.168", "private.example", "Alice", "hidden", "folded-secret")) assertFalse(result.contains(secret), secret)
        for (raw in listOf("INVITE sip:alice@host SIP/2.0\nSecret-body", "SIP/2.0 200 OK\nSecret-body", "password=secret\n continued-secret", "{\"token\":\n\"secret\"}", "Cookie: secret", "Proxy-Authorization: secret")) {
            assertFalse(clean.clean(raw).contains("secret", true), raw)
        }
        val tokens = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturesecret AIza" + "a".repeat(35)
        assertFalse(clean.clean(tokens).contains("signaturesecret"))
        assertFalse(clean.clean(tokens).contains("AIza"))
    }
    @Test fun archiveOnlyReadsExactLogsAndBoundsTail() {
        val root = Files.createTempDirectory("qtalk-support-synthetic-")
        try {
            Files.writeString(root.resolve("qtalk-0.log"), "x".repeat(200000) + "\n2026-01-01 QTalk event=last\n")
            Files.writeString(root.resolve("contacts.db"), "DO_NOT_READ_CONTACTS")
            Files.writeString(root.resolve("other.log"), "DO_NOT_READ_OTHER")
            Files.createDirectory(root.resolve("nested"))
            Files.writeString(root.resolve("nested/qtalk-1.log"), "DO_NOT_RECURSE")
            val collector = DiagnosticsCollector(listOf(root), DiagnosticSanitizer(), "test", maxRawBytes = 1024)
            val snapshot = collector.prepare(SupportDraft("Title", "Description long"))
            val entries = unzip(snapshot.zip)
            assertTrue(snapshot.zip.size <= 8 * 1024 * 1024)
            assertTrue(entries.keys.containsAll(listOf("report.json", "system.json", "manifest.json")))
            val text = entries.values.joinToString { it.decodeToString() }
            assertFalse(text.contains("DO_NOT")); assertTrue(text.contains("event=last"))
            assertTrue(entries.getValue("manifest.json").decodeToString().contains("truncated"))
            val system = entries.getValue("system.json").decodeToString()
            for (bad in listOf("user.name", "hostname", "java.class.path", "user.home")) assertFalse(system.contains(bad))
            assertTrue(entries.getValue("report.json").decodeToString().contains("Description long"))
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun ignoresSymlinksWhenSupported() {
        val root = Files.createTempDirectory("qtalk-support-synthetic-link-")
        try {
            val target = Files.writeString(root.resolve("private.txt"), "NEVER_INCLUDE")
            try { Files.createSymbolicLink(root.resolve("qtalk-0.log"), target) } catch (error: Exception) {
                org.junit.Assume.assumeNoException("File symlink creation requires Windows privilege", error)
                return
            }
            assertFalse(unzip(DiagnosticsCollector(listOf(root), DiagnosticSanitizer(), "test").prepare(SupportDraft()).zip)
                .values.any { it.decodeToString().contains("NEVER_INCLUDE") })
        } finally { root.toFile().deleteRecursively() }
    }
    @Test fun refusesWindowsJunctionInAnyAncestor() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").contains("Windows"))
        val root = Files.createTempDirectory("qtalk-support-synthetic-junction-")
        val target = Files.createDirectories(root.resolve("target/logs"))
        val link = root.resolve("link")
        try {
            Files.writeString(target.resolve("qtalk-0.log"), "JUNCTION_PRIVATE")
            fun quote(path: java.nio.file.Path) = "'" + path.toAbsolutePath().toString().replace("'", "''") + "'"
            val process = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "New-Item -ItemType Junction -Path ${quote(link)} -Target ${quote(target.parent)} | Out-Null")
                .redirectErrorStream(true).start()
            process.inputStream.readBytes()
            org.junit.Assume.assumeTrue("Synthetic directory junction unavailable", process.waitFor() == 0)
            val zip = DiagnosticsCollector(listOf(link.resolve("logs")), DiagnosticSanitizer(), "test").prepare(SupportDraft()).zip
            assertFalse(unzip(zip).values.any { it.decodeToString().contains("JUNCTION_PRIVATE") })
        } finally {
            Files.deleteIfExists(link)
            root.toFile().deleteRecursively()
        }
    }
    @Test fun validatesImageBeforeDecodeAndNormalizesToPng() {
        assertFailsWith<SupportFailure> { normalizeScreenshot("bad.png", byteArrayOf(1,2,3)) }
        assertFailsWith<SupportFailure> { normalizeScreenshot("big.png", ByteArray(4 * 1024 * 1024 + 1)) }
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(12, 9, BufferedImage.TYPE_INT_RGB), "jpg", out)
        val attachment = normalizeScreenshot("selected.jpg", out.toByteArray())
        assertEquals("selected.jpg", attachment.filename)
        assertEquals(0x89, attachment.pngBytes()[0].toInt() and 255)
        val bomb = attachment.pngBytes()
        // PNG IHDR width 0x7fffffff is rejected before pixel allocation (CRC irrelevant).
        bomb[16] = 0x7f; bomb[17] = 0xff.toByte(); bomb[18] = 0xff.toByte(); bomb[19] = 0xff.toByte()
        assertFailsWith<SupportFailure> { normalizeScreenshot("bomb.png", bomb) }
    }
}
internal fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) }
    }
}
