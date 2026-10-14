package com.qvoste.qtalk

import com.qvoste.qtalk.voip.LinphoneResourceInstaller
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class LinphoneResourceInstallerTest {
    @Test
    fun installsResourcesFromJarWhosePathContainsSpaces() {
        val root = createTempDirectory("QTalk Linphone test ")
        val sourceJar = root.resolve("SDK with spaces.jar")
        val destination = root.resolve("destination with spaces")
        val grammar = "test-vcard-grammar".encodeToByteArray()
        val mediaPlugin = "test-plugin".encodeToByteArray()

        JarOutputStream(Files.newOutputStream(sourceJar)).use { jar ->
            jar.putNextEntry(
                JarEntry("assets/org.linphone.core/share/belr/grammars/vcard_grammar.belr")
            )
            jar.write(grammar)
            jar.closeEntry()
            jar.putNextEntry(JarEntry("mediastreamer/plugins/libmswasapi.dll"))
            jar.write(mediaPlugin)
            jar.closeEntry()
        }

        val paths = LinphoneResourceInstaller.installFromJar(sourceJar, destination)

        assertContentEquals(
            grammar,
            Files.readAllBytes(paths.share.resolve("belr/grammars/vcard_grammar.belr"))
        )
        assertContentEquals(
            mediaPlugin,
            Files.readAllBytes(paths.mediastreamerPlugins.resolve("libmswasapi.dll"))
        )
        assertTrue(Files.isDirectory(paths.liblinphonePlugins))
    }
}
