package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.test.*

class AudioGainControllerTest {
    @Test fun burstTargetsCoalesceSmoothlyAndPersistFinalValueOnce() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            try {
                val applied = mutableListOf<GainLevels>()
                val saved = mutableListOf<GainLevels>()
                val controller = AudioGainController(scope, GainLevels(), { applied += it }, { saved += it })
                withContext(dispatcher) {
                    repeat(100) { controller.target(GainLevels(it / 100f, it / 100f)) }
                    controller.target(GainLevels(.2f, 0f))
                }
                withTimeout(5_000) { while (withContext(dispatcher) { saved.isEmpty() }) delay(10) }
                withContext(dispatcher) {
                    assertEquals(listOf(GainLevels(.2f, 0f)), saved)
                    assertEquals(saved.last(), applied.last())
                    assertTrue(applied.size < 40)
                    assertTrue(applied.zipWithNext().all { (a, b) -> kotlin.math.abs(a.microphone - b.microphone) <= .041f })
                    controller.restore()
                    assertEquals(saved.last(), applied.last())
                }
                withContext(dispatcher) { controller.target(GainLevels()); controller.flush() }
                withContext(dispatcher) { assertEquals(GainLevels(), saved.last()) }
            } finally { scope.cancel() }
        }
    }
}
