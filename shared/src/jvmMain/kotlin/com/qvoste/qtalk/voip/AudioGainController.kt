package com.qvoste.qtalk.voip

import kotlinx.coroutines.*

/** Confined to the native dispatcher. A single ramp also debounces persistence. */
internal class AudioGainController(
    private val scope: CoroutineScope,
    initial: GainLevels,
    private val apply: (GainLevels) -> Unit,
    private val persist: suspend (GainLevels) -> Unit
) {
    var target: GainLevels = initial
        private set
    private var applied = initial
    private var job: Job? = null
    private var dirty = false
    fun target(value: GainLevels) {
        if (target == value) return
        target = value
        dirty = true
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                applied = applied.step(target)
                apply(applied)
                if (applied != target) { delay(15); continue }
                val snapshot = target
                delay(250)
                if (snapshot != target) continue
                persist(snapshot)
                if (snapshot == target) { dirty = false; break }
            }
        }
    }
    fun restore() { apply(applied) }
    suspend fun flush() {
        job?.cancelAndJoin()
        job = null
        if (dirty) { persist(target); dirty = false }
    }
}
