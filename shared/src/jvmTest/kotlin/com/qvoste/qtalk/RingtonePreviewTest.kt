package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class RingtonePreviewTest {
    private class Playback : PreviewPlayback {
        val started = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var end: () -> Unit = {}
        override fun start(onEnd: () -> Unit) { end = onEnd; started.countDown() }
        override fun close() { closed.countDown() }
    }

    @Test fun playsPastTwoSecondsUntilEofThenReleases() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val playback = Playback()
            val preview = RingtonePreview(scope) { _, _ -> playback }
            preview.play("warm", .4f)
            assertTrue(playback.started.await(5, TimeUnit.SECONDS))
            delay(2_100)
            assertEquals(1L, playback.closed.count, "full file must continue beyond legacy two-second cutoff")
            assertEquals("warm", preview.playing.value)
            playback.end()
            assertEquals(0L, playback.closed.count)
            assertNull(preview.playing.value)
        } finally { scope.cancel() }
    }

    @Test fun stopDuringDelayedOpenCannotStartPlayback() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val playback = Playback()
            val preview = RingtonePreview(scope) { _, _ -> entered.complete(Unit); release.await(); playback }
            preview.play("warm", 0f)
            entered.await()
            preview.stop()
            release.complete(Unit)
            assertTrue(playback.closed.await(5, TimeUnit.SECONDS))
            assertEquals(1L, playback.started.count, "stopped loading must never start")
            assertNull(preview.playing.value)
        } finally { scope.cancel() }
    }

    @Test fun replacementStaleEofAndCallGateKeepOnlyLatestPreview() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val first = Playback(); val second = Playback()
            val preview = RingtonePreview(scope) { id, volume ->
                assertEquals(.25f, volume)
                if (id == "warm") first else second
            }
            preview.play("warm", .25f)
            assertTrue(first.started.await(5, TimeUnit.SECONDS))
            preview.play("round", .25f)
            assertTrue(first.closed.await(5, TimeUnit.SECONDS))
            assertTrue(second.started.await(5, TimeUnit.SECONDS))
            first.end()
            assertEquals("round", preview.playing.value)
            preview.setAllowed(false)
            assertTrue(second.closed.await(5, TimeUnit.SECONDS))
            preview.play("warm", .25f)
            assertNull(preview.playing.value)
            preview.setAllowed(true)
            preview.play("round", .25f)
            assertEquals("round", preview.playing.value)
            preview.stop()
        } finally { scope.cancel() }
    }

    @Test fun delayedOpenAndBurstSelectionsLoadOnlyLatestAndCloseObsoleteResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val obsolete = Playback(); val latest = Playback()
            val opened = java.util.concurrent.atomic.AtomicInteger()
            val preview = RingtonePreview(scope) { id, _ ->
                opened.incrementAndGet()
                if (id == "warm") { entered.complete(Unit); release.await(); obsolete } else latest
            }
            preview.play("warm", .5f); entered.await()
            repeat(100) { preview.play("round", .5f) }
            release.complete(Unit)
            assertTrue(latest.started.await(5, TimeUnit.SECONDS))
            assertEquals(0L, obsolete.closed.count); assertEquals(1L, obsolete.started.count)
            assertEquals(2, opened.get())
            preview.stop()
            assertEquals(0L, latest.closed.count)
        } finally { scope.cancel() }
    }
}
