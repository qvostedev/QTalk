package com.qvoste.qtalk.voip

import org.linphone.core.Factory
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.JarFile

internal data class LinphoneResourcePaths(
    val share: Path,
    val liblinphonePlugins: Path,
    val mediastreamerPlugins: Path
)

internal object LinphoneResourceInstaller {
    private const val assetsPrefix = "assets/org.linphone.core/share/"
    private const val liblinphonePrefix = "liblinphone/"
    private const val mediastreamerPrefix = "mediastreamer/"
    private const val markerName = ".qtalk-resources"
    private const val lockName = ".qtalk-resources.lock"

    fun install(): LinphoneResourcePaths {
        // Linphone 5.5.x passes the URL-encoded SDK JAR path directly to File.
        // That fails for an installed path such as "Program%20Files", so install
        // the resources before Factory.instance() initializes native code.
        val sdkLocation = Factory::class.java.protectionDomain.codeSource?.location
            ?: error("Unable to locate the Linphone SDK JAR")
        val sdkJar = Path.of(sdkLocation.toURI())
        check(Files.isRegularFile(sdkJar)) {
            "Linphone SDK location is not a JAR: $sdkJar"
        }

        val destination = Path.of(
            System.getProperty("java.io.tmpdir"),
            "linphone-sdk-java-linux"
        )
        return installFromJar(sdkJar, destination)
    }

    internal fun installFromJar(sourceJar: Path, destination: Path): LinphoneResourcePaths {
        Files.createDirectories(destination)
        val paths = LinphoneResourcePaths(
            share = destination.resolve("share"),
            liblinphonePlugins = destination.resolve("liblinphone").resolve("plugins"),
            mediastreamerPlugins = destination.resolve("mediastreamer").resolve("plugins")
        )
        val fingerprint = buildString {
            append(Files.size(sourceJar))
            append(':')
            append(Files.getLastModifiedTime(sourceJar).toMillis())
        }
        val marker = destination.resolve(markerName)

        FileChannel.open(
            destination.resolve(lockName),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        ).use { channel ->
            val lock = channel.lock()
            try {
                val grammar = paths.share.resolve("belr/grammars/vcard_grammar.belr")
                if (Files.isRegularFile(grammar) &&
                    Files.isRegularFile(paths.mediastreamerPlugins.resolve("libmswasapi.dll")) &&
                    Files.isRegularFile(marker) &&
                    Files.readString(marker) == fingerprint
                ) {
                    return paths
                }

                Files.createDirectories(paths.share)
                Files.createDirectories(paths.liblinphonePlugins)
                Files.createDirectories(paths.mediastreamerPlugins)
                JarFile(sourceJar.toFile()).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue

                        val destinationPath = when {
                            entry.name.startsWith(assetsPrefix) ->
                                paths.share.resolve(entry.name.removePrefix(assetsPrefix))

                            entry.name.startsWith(liblinphonePrefix) ->
                                destination.resolve("liblinphone")
                                    .resolve(entry.name.removePrefix(liblinphonePrefix))

                            entry.name.startsWith(mediastreamerPrefix) ->
                                destination.resolve("mediastreamer")
                                    .resolve(entry.name.removePrefix(mediastreamerPrefix))

                            else -> null
                        } ?: continue

                        val normalizedPath = destinationPath.normalize()
                        check(normalizedPath.startsWith(destination.normalize())) {
                            "Unsafe Linphone resource path: ${entry.name}"
                        }
                        Files.createDirectories(normalizedPath.parent)
                        jar.getInputStream(entry).use { input ->
                            Files.copy(
                                input,
                                normalizedPath,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    }
                }

                check(Files.isRegularFile(paths.share.resolve("belr/grammars/vcard_grammar.belr"))) {
                    "Linphone SDK JAR does not contain vcard_grammar.belr"
                }
                Files.writeString(
                    marker,
                    fingerprint,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                )
            } finally {
                lock.release()
            }
        }
        return paths
    }
}
