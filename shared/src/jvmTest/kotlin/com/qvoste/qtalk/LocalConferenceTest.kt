package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlin.test.*

class LocalConferenceTest {
    private data class Leg(val number: String, var state: ConferenceLegState = ConferenceLegState.ACTIVE)
    private class Fixture {
        val first = Leg("201")
        var invited: Leg? = null
        var merged = false
        var finished = false
        var promoted: Leg? = null
        var failInvite = false
        var failMerge = false
        var failEndFirst = false
        var leaveDuringMerge = false
        val ended = mutableListOf<String>()
        val session = LocalConferenceSession(object : LocalConferenceBackend<Leg> {
            override fun number(call: Leg) = call.number
            override fun state(call: Leg) = call.state
            override fun pause(call: Leg) { call.state = ConferenceLegState.PAUSING }
            override fun resume(call: Leg) { call.state = ConferenceLegState.ACTIVE }
            override fun invite(number: String): Leg {
                check(!failInvite) { "Invite failed" }
                return Leg(number, ConferenceLegState.DIALING).also { invited = it }
            }
            override fun merge(first: Leg, second: Leg) {
                check(!failMerge)
                if (leaveDuringMerge) event(second, ConferenceLegState.ENDED)
                merged = true
            }
            override fun end(call: Leg) {
                ended += call.number
                check(!(failEndFirst && call == first))
                event(call, ConferenceLegState.ENDED)
            }
        }, { promoted = it }, { finished = true })

        fun event(leg: Leg, state: ConferenceLegState) {
            leg.state = state
            session.onCallState(leg, state)
        }
        fun consult() {
            session.begin(first, "202")
            assertNull(invited)
            event(first, ConferenceLegState.PAUSED)
            event(checkNotNull(invited), ConferenceLegState.ACTIVE)
        }
    }

    @Test fun holdsFirstBeforeDialingAndMergesOnlyAfterAnswer() {
        val f = Fixture()
        f.session.begin(f.first, "202")
        assertNull(f.invited)
        assertFailsWith<IllegalStateException> { f.session.merge() }
        f.event(f.first, ConferenceLegState.PAUSED)
        assertEquals(ConferencePhase.INVITING, f.session.status.value.phase)
        f.event(checkNotNull(f.invited), ConferenceLegState.ACTIVE)
        assertEquals(f.invited, f.session.foregroundCall)
        f.session.merge()
        assertTrue(f.merged)
        assertEquals(listOf("201", "202"), f.session.status.value.participants)
        assertEquals(ConferencePhase.ACTIVE, f.session.status.value.phase)
    }

    @Test fun cancellationAndDeclineReturnToFirstWithoutEndingIt() {
        val f = Fixture(); f.consult(); f.session.cancel()
        f.event(f.first, ConferenceLegState.ACTIVE)
        assertEquals(listOf("202"), f.ended)
        assertEquals(ConferencePhase.NONE, f.session.status.value.phase)
        assertFalse(f.finished)
        val declined = Fixture(); declined.consult()
        declined.event(checkNotNull(declined.invited), ConferenceLegState.FAILED)
        declined.event(declined.first, ConferenceLegState.ACTIVE)
        assertFalse(declined.finished)
        assertEquals(ConferencePhase.NONE, declined.session.status.value.phase)
    }

    @Test fun cancellationBeforeHoldCompletesDoesNotDial() {
        val f = Fixture(); f.session.begin(f.first, "202"); f.session.cancel()
        f.event(f.first, ConferenceLegState.PAUSED)
        f.event(f.first, ConferenceLegState.ACTIVE)
        assertNull(f.invited)
        assertEquals(ConferencePhase.NONE, f.session.status.value.phase)
    }

    @Test fun failedInvitationAndMergePreserveFirstCall() {
        val f = Fixture(); f.failInvite = true; f.session.begin(f.first, "202")
        f.event(f.first, ConferenceLegState.PAUSED)
        f.event(f.first, ConferenceLegState.ACTIVE)
        assertFalse(f.finished)
        assertTrue(f.ended.isEmpty())
        val merge = Fixture(); merge.consult(); merge.failMerge = true
        assertFailsWith<IllegalStateException> { merge.session.merge() }
        assertEquals(ConferencePhase.CONSULTING, merge.session.status.value.phase)
        assertTrue(merge.ended.isEmpty())
    }

    @Test fun aParticipantLeavingDoesNotEndTheOtherCall() {
        val f = Fixture(); f.consult(); f.session.merge()
        f.event(f.first, ConferenceLegState.ENDED)
        assertEquals(f.invited, f.promoted)
        assertEquals(listOf("202"), f.session.status.value.participants)
        assertFalse(f.finished)
        f.session.hangUpAll()
        assertTrue(f.finished)
    }

    @Test fun hangupAttemptsBothLegsEvenIfOneFails() {
        val f = Fixture(); f.consult(); f.session.merge(); f.failEndFirst = true
        assertFailsWith<IllegalStateException> { f.session.hangUpAll() }
        assertEquals(listOf("201", "202"), f.ended)
        assertFalse(f.finished)
    }

    @Test fun rejectsDuplicateInvitationsAndCallingSamePerson() {
        val f = Fixture()
        assertFailsWith<IllegalArgumentException> { f.session.begin(f.first, "201") }
        f.session.begin(f.first, "202")
        assertFailsWith<IllegalStateException> { f.session.begin(f.first, "203") }
    }

    @Test fun leavingDuringMergeDoesNotResurrectConference() {
        val f = Fixture(); f.consult(); f.leaveDuringMerge = true
        f.session.merge()
        assertNotEquals(ConferencePhase.ACTIVE, f.session.status.value.phase)
        f.event(f.first, ConferenceLegState.ACTIVE)
        assertEquals(ConferencePhase.NONE, f.session.status.value.phase)
        assertFalse(f.finished)
    }

    @Test fun hostCanRemoveEitherParticipantWithoutEndingTheOtherCall() {
        for (number in listOf("201", "202")) {
            val f = Fixture(); f.consult(); f.session.merge()
            f.session.removeParticipant(number)
            assertEquals(listOf(number), f.ended)
            assertEquals(1, f.session.status.value.participants.size)
            assertFalse(f.finished)
            f.session.hangUpAll()
            assertTrue(f.finished)
        }
    }

    @Test fun audioStateCannotShowMutedLocalMicrophoneAsSpeaking() {
        val f = Fixture(); f.consult(); f.session.merge()
        f.session.updateAudioState(true, true, setOf("202"), emptySet(), true)
        assertTrue(f.session.status.value.microphoneMuted)
        assertTrue(f.session.status.value.speakerMuted)
        assertFalse(f.session.status.value.localSpeaking)
        assertEquals(setOf("202"), f.session.status.value.speakingNumbers)
        f.session.reset()
        assertTrue(f.session.status.value.speakingNumbers.isEmpty())
    }
}
