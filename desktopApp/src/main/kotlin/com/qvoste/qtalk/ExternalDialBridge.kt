package com.qvoste.qtalk

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

internal object ExternalDialLink {
    private val validNumber = Regex("\\+?[0-9*#]{1,32}")

    fun isValidNumber(number: String): Boolean = validNumber.matches(number)

    fun numberFromArgument(argument: String): String? {
        val uri = runCatching { URI(argument) }.getOrNull() ?: return null
        if (!uri.scheme.equals("qtalk", ignoreCase = true) ||
            !uri.host.equals("dial", ignoreCase = true)) return null
        val encoded = uri.rawQuery.orEmpty().split('&').firstOrNull {
            it.substringBefore('=') == "number"
        }?.substringAfter('=', missingDelimiterValue = "") ?: return null
        val number = runCatching { URLDecoder.decode(encoded, Charsets.UTF_8) }.getOrNull()
            ?: return null
        return number.takeIf(::isValidNumber)
    }
}

internal class ExternalDialBridge private constructor(
    val initialNumber: String?,
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket,
    private val endpointFile: Path
) : Closeable {
    override fun close() {
        server.close()
        Files.deleteIfExists(endpointFile)
        lock.release()
        channel.close()
    }

    companion object {
        fun start(
            args: Array<String>,
            onDial: (String) -> Unit,
            onShow: () -> Unit,
            directory: Path = Path.of(
                System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "QTalk"
            )
        ): ExternalDialBridge? {
            val initialNumber = args.firstNotNullOfOrNull(ExternalDialLink::numberFromArgument)
            Files.createDirectories(directory)
            val endpointFile = directory.resolve("desktop-endpoint")
            val channel = FileChannel.open(
                directory.resolve("desktop.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                sendToRunningApp(endpointFile, initialNumber)
                return null
            }

            try {
                val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
                val token = UUID.randomUUID().toString()
                Files.writeString(
                    endpointFile,
                    "${server.localPort} $token",
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                )
                Thread({
                    while (!server.isClosed) {
                        try {
                            server.accept().use { socket ->
                                socket.soTimeout = 2000
                                val line = socket.getInputStream().bufferedReader().readLine()
                                if (line == null || line.length > 100 || !line.startsWith("$token ")) {
                                    return@use
                                }
                                val command = line.removePrefix("$token ")
                                if (command == "SHOW") onShow()
                                else if (command.startsWith("DIAL ")) {
                                    val number = command.removePrefix("DIAL ")
                                    if (ExternalDialLink.isValidNumber(number)) onDial(number)
                                }
                            }
                        } catch (_: java.net.SocketException) {
                            if (server.isClosed) break
                        } catch (_: Exception) {
                            // A malformed local request must not stop the application.
                        }
                    }
                }, "QTalk external link listener").apply { isDaemon = true; start() }
                return ExternalDialBridge(initialNumber, channel, lock, server, endpointFile)
            } catch (error: Exception) {
                lock.release()
                channel.close()
                throw error
            }
        }

        private fun sendToRunningApp(endpointFile: Path, number: String?) {
            repeat(20) {
                try {
                    val (portText, token) = Files.readString(endpointFile).trim().split(' ', limit = 2)
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), portText.toInt()), 500)
                        socket.getOutputStream().bufferedWriter().use { writer ->
                            writer.write("$token ${if (number == null) "SHOW" else "DIAL $number"}\n")
                            writer.flush()
                        }
                    }
                    return
                } catch (_: Exception) {
                    Thread.sleep(100)
                }
            }
            System.err.println("QTalk: could not deliver the external link to the running app")
        }
    }
}
