package com.qvoste.qtalk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleaseUpdaterTest {
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
                "assets":[
                    {"name":"QTalk-Setup-1.0.1.exe","browser_download_url":"https://github.com/qvostedev/QTalk/releases/download/v1.0.1/QTalk-Setup-1.0.1.exe","size":90000000,"digest":"sha256:abcd"},
                    {"name":"QTalk_1.0.1-1_amd64.deb","browser_download_url":"https://github.com/qvostedev/QTalk/releases/download/v1.0.1/QTalk_1.0.1-1_amd64.deb","size":80000000,"digest":"sha256:abcd"}
                ]
            }""".trimIndent()
        )
        assertEquals("1.0.1", release.version)
        assertEquals("QTalk-Setup-1.0.1.exe", assertNotNull(selectQTalkAsset(release, true)).name)
        assertEquals("QTalk_1.0.1-1_amd64.deb", assertNotNull(selectQTalkAsset(release, false)).name)
        assertNull(selectQTalkAsset(release.copy(assets = emptyList()), true))
    }
}
