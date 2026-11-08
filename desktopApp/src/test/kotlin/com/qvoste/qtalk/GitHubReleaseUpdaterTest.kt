package com.qvoste.qtalk

import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import com.qvoste.qtalk.ui.calls.i18n.AppLanguage
import com.qvoste.qtalk.ui.calls.i18n.translateUiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GitHubReleaseUpdaterTest {
    private val debianTools = LinuxUpdateTools(Path.of("/usr/bin/pkexec"), Path.of("/usr/bin/apt"))

    @Test
    fun nixAndUnsupportedPlatformsStopBeforeReleaseDownloadOrInstallation() = runBlocking {
        val platforms = listOf(
            UpdatePlatform("Linux", "amd64", null, "ID=nixos\nID_LIKE=debian"),
            UpdatePlatform("Linux", "amd64", null, "# distribution\nID=\"nixos\"\n"),
            UpdatePlatform("Linux", "amd64", "nix", "ID=ubuntu"),
            UpdatePlatform("Linux", "aarch64", "nix", "ID=ubuntu"),
            UpdatePlatform("Linux", "amd64", null, "ID=fedora"),
            UpdatePlatform("Linux", "amd64", null, ""),
            UpdatePlatform("Linux", "aarch64", null, "ID=debian"),
            UpdatePlatform("Linux", "arm64", null, "ID=ubuntu")
        )
        for (platform in platforms) {
            val statuses = mutableListOf<String>()
            val updater = GitHubReleaseUpdater(
                "1.0.0",
                platform = { platform },
                linuxTools = { error("Unsupported platforms must not inspect apt/pkexec") },
                releaseSource = { error("Preflight must stop before requesting a release") },
                installerDownload = { _, _, _, _ -> error("Must not download") },
                linuxInstaller = { _, _ -> error("Must not install") }
            )
            assertNull(updater.availableRelease())
            assertFalse(updater.checkAndInstall { status, _ -> statuses += status })
            val status = statuses.last()
            assertFalse(status.contains("Обновление установлено"))
            assertTrue(translateUiText(status, AppLanguage.ENGLISH) != status)
            if (platform.installMethod == "nix" || platform.osRelease.contains("ID=nixos")) {
                assertTrue(status.contains("nix profile upgrade qtalk"))
                assertTrue(translateUiText(status, AppLanguage.ENGLISH).contains("NixOS configuration"))
            }
        }
    }

    @Test
    fun debianFamilyRequiresAmd64AndTrustedTools() {
        for (osRelease in listOf("ID=debian", "ID=ubuntu", "ID=mint\nID_LIKE=\"ubuntu debian\"")) {
            for (architecture in listOf("amd64", "x86_64")) {
                assertEquals(UpdatePlan.Debian(debianTools), updatePlan(
                    UpdatePlatform("Linux", architecture, null, osRelease)
                ) { debianTools })
            }
        }
        assertTrue(updatePlan(UpdatePlatform("Linux", "amd64", null, "ID=notdebian\nID_LIKE=notubuntu")) {
            error("Distribution matching must use complete tokens")
        } is UpdatePlan.Manual)
        assertTrue(updatePlan(UpdatePlatform("Linux", "amd64", null, "ID=debian")) { null } is UpdatePlan.Manual)
        assertEquals(UpdatePlan.Windows, updatePlan(UpdatePlatform("Windows 11", "arm64", "nix", "")) {
            error("Windows must not inspect Linux tools")
        })
    }

    @Test
    fun missingOrUnsafeToolsStopBeforeAnyDownload() = runBlocking {
        val updater = GitHubReleaseUpdater(
            "1.0.0",
            platform = { UpdatePlatform("Linux", "amd64", null, "ID=debian") },
            linuxTools = { null },
            releaseSource = { error("Tools must be checked before requesting a release") },
            installerDownload = { _, _, _, _ -> error("Must not download") },
            linuxInstaller = { _, _ -> error("Must not install") }
        )
        val statuses = mutableListOf<String>()
        assertFalse(updater.checkAndInstall { status, _ -> statuses += status })
        assertNull(updater.availableRelease())
        val english = translateUiText(statuses.last(), AppLanguage.ENGLISH)
        assertTrue(english.contains("setuid"))
        assertTrue(english.contains("manually"))
    }

    @Test
    fun executablePolicyRequiresRootOwnershipSafePermissionsAndPkexecSetuid() {
        assertTrue(isTrustedUpdateExecutable(0, 0b100111101101, true, true)) // 04755
        assertFalse(isTrustedUpdateExecutable(0, 0b111101101, true, true)) // 0755, no setuid
        assertFalse(isTrustedUpdateExecutable(1000, 0b100111101101, true, true))
        assertFalse(isTrustedUpdateExecutable(0, 0b100111111101, true, true)) // group writable
        assertFalse(isTrustedUpdateExecutable(0, 0b100111101111, true, true)) // other writable
        assertFalse(isTrustedUpdateExecutable(0, 0b100111101101, false, true))
        assertTrue(isTrustedUpdateExecutable(0, 0b111101101, true, false)) // apt needs no setuid
    }

    @Test
    fun validDebianDownloadsAndInstallsWithThePreflightTools() = runBlocking {
        val asset = ReleaseAsset("QTalk_1.0.1-1_amd64.deb",
            java.net.URI("https://github.com/qvostedev/QTalk/releases/download/v1.0.1/QTalk_1.0.1-1_amd64.deb"),
            10, "sha256:" + "a".repeat(64))
        val calls = mutableListOf<String>()
        val file = Path.of("test-installer.deb")
        val updater = GitHubReleaseUpdater(
            "1.0.0",
            platform = { UpdatePlatform("Linux", "amd64", null, "ID=debian") },
            linuxTools = { calls += "tools"; debianTools },
            releaseSource = { calls += "release"; QTalkRelease("1.0.1", listOf(asset)) },
            installerDownload = { selected, digest, extension, _ ->
                assertEquals(asset, selected)
                assertEquals("a".repeat(64), digest)
                assertEquals(".deb", extension)
                calls += "download"
                file
            },
            linuxInstaller = { installer, tools ->
                assertEquals(file, installer)
                assertEquals(debianTools, tools)
                calls += "install"
            }
        )
        assertFalse(updater.checkAndInstall { _, _ -> })
        assertEquals(listOf("tools", "release", "download", "install"), calls)
    }

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
    fun betaBuildsNeverOfferStableOnePointOnePointOneAsAnUpdate() {
        assertFalse(isNewerVersion("1.1.1", "1.1.1-beta"))
        assertFalse(isNewerVersion("1.1.1-beta", "1.1.1-beta"))
        assertTrue(isNewerVersion("1.1.2", "1.1.1-beta"))
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
    fun linuxAssetSelectionRequiresAmd64EvenWhenArmIsPublishedFirst() {
        val arm = ReleaseAsset("QTalk_1.0.1-1_arm64.deb", java.net.URI("https://github.com/arm"), 10, null)
        val amd64 = arm.copy(name = "QTalk_1.0.1-1_amd64.deb")
        assertEquals(amd64, selectQTalkAsset(QTalkRelease("1.0.1", listOf(arm, amd64)), false))
        assertNull(selectQTalkAsset(QTalkRelease("1.0.1", listOf(arm)), false))
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
