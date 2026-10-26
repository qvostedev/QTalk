package com.qvoste.qtalk

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GitHubReleaseUpdaterTest {
    @Test
    fun verifiesPayloadSizeWhileCopyingAndReportsDigest() {
        val bytes = "installer".toByteArray()
        val output = java.io.ByteArrayOutputStream()
        val digest = copyInstallerPayload(bytes.inputStream(), output, bytes.size.toLong()) { _, _ -> }
        assertTrue(bytes.contentEquals(output.toByteArray()))
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, digest)
        val oversized = java.io.ByteArrayOutputStream()
        assertFailsWith<IllegalStateException> {
            copyInstallerPayload(bytes.inputStream(), oversized, 3) { _, _ -> }
        }
        assertTrue(oversized.size() <= 3)
        assertFailsWith<IllegalStateException> {
            copyInstallerPayload(bytes.inputStream(), java.io.ByteArrayOutputStream(), bytes.size.toLong() + 1) { _, _ -> }
        }
    }

    @Test
    fun comparesNumericVersions() {
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(isNewerVersion("unknown", "1.0.0"))
    }

    @Test
    fun readsReleaseAssetsAndSelectsInstallerForPlatform() {
        val release = parseQTalkRelease(
            """{
                "tag_name":"v1.0.1",
                "body":"Changes:\n\n- Added update notifications",
                "assets":[
                    {"name":"QTalk-Setup-1.0.1.exe","browser_download_url":"https://github.com/qvostedev/QTalk/releases/download/v1.0.1/QTalk-Setup-1.0.1.exe","size":90000000,"digest":"sha256:abcd"},
                    {"name":"QTalk_1.0.1-1_amd64.deb","browser_download_url":"https://github.com/qvostedev/QTalk/releases/download/v1.0.1/QTalk_1.0.1-1_amd64.deb","size":80000000,"digest":"sha256:abcd"}
                ]
            }""".trimIndent()
        )
        assertEquals("1.0.1", release.version)
        assertEquals("Changes:\n\n- Added update notifications", release.description)
        assertEquals("QTalk-Setup-1.0.1.exe", assertNotNull(selectQTalkAsset(release, true)).name)
        assertEquals("QTalk_1.0.1-1_amd64.deb", assertNotNull(selectQTalkAsset(release, false)).name)
        assertNull(selectQTalkAsset(release.copy(assets = emptyList()), true))
    }

    @Test
    fun windowsUpdaterWaitsForApplicationAndInstallerBeforeRestarting() {
        val script = windowsUpdateScript(
            installer = Path.of("C:/Temp/QTalk update's installer.exe"),
            applicationPid = 4242,
            applicationExecutable = Path.of("C:/Program Files/QTalk/QTalk.exe"),
            logFile = Path.of("C:/Temp/QTalk-update.log")
        )

        assertTrue(script.contains("Wait-Process -Id 4242"))
        assertTrue(script.contains("'/FORCECLOSEAPPLICATIONS'"))
        assertTrue(script.contains("-Wait -PassThru"))
        assertTrue(script.contains("Start-Process -FilePath ${'$'}applicationPath"))
        assertTrue(script.contains("QTalk update''s installer.exe"))
    }
}
