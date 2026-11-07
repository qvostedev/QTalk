package com.qvoste.qtalk.voip

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface PreviewPlayback : AutoCloseable {
    fun start(onEnd: () -> Unit)
}

/** One loader, one active clip. Requests invalidate even an uninterruptible native open. */
internal class RingtonePreview(
    scope: CoroutineScope,
    private val open: suspend (String, Float) -> PreviewPlayback
) {
    private data class Request(val generation: Long, val id: String, val volume: Float)
    private val lock = Any()
    private var generation = 0L
    private var active: PreviewPlayback? = null
    private var allowed = true
    private val requests = Channel<Request>(Channel.CONFLATED)
    private val _playing = MutableStateFlow<String?>(null)
    val playing = _playing.asStateFlow()

    init {
        scope.launch {
            try {
                for (request in requests) {
                    if (synchronized(lock) { generation != request.generation }) continue
                    val playback = try { open(request.id, request.volume) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { finish(request.generation); continue }
                    synchronized(lock) {
                        if (generation != request.generation || !isActive) runCatching { playback.close() }
                        else {
                            active = playback
                            try { playback.start { finish(request.generation) } }
                            catch (_: Exception) { finish(request.generation) }
                        }
                    }
                }
            } finally { stop() }
        }
    }

    fun play(id: String, volume: Float) = synchronized(lock) {
        stop()
        if (allowed) {
            _playing.value = id
            requests.trySend(Request(generation, id, volume))
        }
        Unit
    }

    fun setAllowed(value: Boolean) = synchronized(lock) {
        allowed = value
        if (!value) stop()
    }

    private fun finish(token: Long) = synchronized(lock) {
        if (generation == token) stop()
    }

    fun stop() = synchronized(lock) {
        generation++
        _playing.value = null
        val previous = active
        active = null
        runCatching { previous?.close() }
        Unit
    }
}
