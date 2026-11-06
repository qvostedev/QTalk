package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ConferenceHandoffTest {
    private val id = "22222222-2222-2222-2222-222222222222"
    private class Transport(var answers: MutableList<Boolean>) : ConferenceHandoff.Transport {
        val instructions = mutableListOf<String>()
        val waited = mutableListOf<String>()
        val timeouts = mutableListOf<Long>()
        override fun instruct(instruction: String) { instructions += instruction.substringBefore('|') }
        override suspend fun awaitAcknowledgements(ack: String, timeoutMillis: Long): Boolean {
            waited += ack
            timeouts += timeoutMillis
            return answers.removeAt(0)
        }
    }

    @Test fun threeParticipantsLeavingTwoWaitsForPreparedAudioAndCommitAcknowledgements() = runBlocking {
        val plan = ConferenceHandoff(id, "102", listOf("102", "103"))
        val transport = Transport(mutableListOf(true, true, true))
        plan.transfer(transport)
        assertEquals(listOf("prepare", "connect", "commit"), transport.instructions)
        assertEquals(listOf("prepared:$id", "ready:$id", "committed:$id"), transport.waited)
        assertEquals(listOf(5_000L, 15_000L, 8_000L), transport.timeouts)
        assertEquals(ConferenceHandoff.Phase.COMPLETE, plan.phase)
    }

    @Test fun prepareTimeoutAbortsWithoutSwitchOrCommit() = runBlocking {
        val plan = ConferenceHandoff(id, "102", listOf("102", "103"))
        val transport = Transport(mutableListOf(false, true))
        assertFailsWith<IllegalStateException> { plan.transfer(transport) }
        assertEquals(listOf("prepare", "abort"), transport.instructions)
        assertEquals(listOf("prepared:$id", "aborted:$id"), transport.waited)
        assertEquals(ConferenceHandoff.Phase.ABORTED, plan.phase)
    }

    @Test fun missingAudioReadinessAbortsAndNeverAuthorizesOldHostTeardown() = runBlocking {
        val plan = ConferenceHandoff(id, "102", listOf("102", "103", "104"))
        val transport = Transport(mutableListOf(true, false, true))
        assertFailsWith<IllegalStateException> { plan.transfer(transport) }
        assertEquals(listOf("prepare", "connect", "abort"), transport.instructions)
        assertEquals(listOf("prepared:$id", "ready:$id", "aborted:$id"), transport.waited)
        assertEquals(ConferenceHandoff.Phase.ABORTED, plan.phase)
    }

    @Test fun lostCommitAckStaysCommittedAndRetryDoesNotRestartOrPretendRollback() = runBlocking {
        val plan = ConferenceHandoff(id, "102", listOf("102", "103"))
        val transport = Transport(mutableListOf(true, true, false))
        assertFailsWith<IllegalStateException> { plan.transfer(transport) }
        assertEquals(ConferenceHandoff.Phase.COMMITTING, plan.phase)
        assertFalse("abort" in transport.instructions)
        transport.answers += true
        plan.transfer(transport)
        assertEquals(listOf("prepare", "connect", "commit", "commit"), transport.instructions)
        assertEquals(ConferenceHandoff.Phase.COMPLETE, plan.phase)
    }

    @Test fun missingRollbackAckIsRecordedForEngineToBlockAnotherTransfer() = runBlocking {
        val plan = ConferenceHandoff(id, "102", listOf("102", "103"))
        val transport = Transport(mutableListOf(true, false, false))
        assertFailsWith<IllegalStateException> { plan.transfer(transport) }
        assertEquals(ConferenceHandoff.Phase.ABORTED, plan.phase)
        assertFalse(plan.rollbackAcknowledged)
    }
}
