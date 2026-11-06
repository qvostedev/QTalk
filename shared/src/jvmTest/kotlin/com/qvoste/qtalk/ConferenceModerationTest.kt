package com.qvoste.qtalk

import com.qvoste.qtalk.voip.*
import kotlin.test.*

class ConferenceModerationTest {
    private val room = ConferenceStatus(ConferencePhase.ACTIVE, participants = listOf("102", "103"),
        hostNumber = "101", roomId = "room", pendingNumbers = listOf("104"), originalCreatorNumber = "101")

    @Test fun creatorCanRemoveOnlyAnotherJoinedMember() {
        assertTrue(room.canRemoveParticipant("101", "102"))
        assertFalse(room.canRemoveParticipant("101", "101"))
        assertFalse(room.canRemoveParticipant("101", "104"))
        assertFalse(room.canRemoveParticipant("101", "999"))
    }

    @Test fun mixerSuccessorDoesNotInheritCreatorPowers() {
        val migrated = room.copy(hostNumber = "102", participants = listOf("101", "103"))
        assertFalse(migrated.canRemoveParticipant("102", "103"))
        assertFalse(migrated.canRemoveParticipant("999", "103"))
        assertFalse(migrated.copy(isHost = false).canRemoveParticipant("101", "103"))
    }

    @Test fun missingCreatorOrInactiveRoomFailsClosed() {
        assertFalse(room.copy(originalCreatorNumber = "").canRemoveParticipant("101", "102"))
        assertFalse(room.copy(phase = ConferencePhase.INVITING).canRemoveParticipant("101", "102"))
    }
}
