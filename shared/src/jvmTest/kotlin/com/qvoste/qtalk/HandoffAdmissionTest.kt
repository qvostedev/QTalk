package com.qvoste.qtalk

import com.qvoste.qtalk.voip.ConferenceHandoffPermit
import kotlin.test.*

class HandoffAdmissionTest {
    private val permit = ConferenceHandoffPermit("agreed-transfer", "102", "original-room", 100)
    @Test fun agreedSuccessorAlsoNeedsNegotiatedTransferId() {
        assertFalse(permit.accepts("102", null, "original-room", 10, false))
        assertFalse(permit.accepts("102", "wrong-transfer", "original-room", 10, false))
        assertTrue(permit.accepts("102", "agreed-transfer", "original-room", 10, false))
    }
    @Test fun wrongCallerExpiredChangedRoomAndSecondCallCannotAutoaccept() {
        assertFalse(permit.accepts("999", permit.id, permit.roomId, 10, false))
        assertFalse(permit.accepts("102", permit.id, permit.roomId, 100, false))
        assertFalse(permit.accepts("102", permit.id, "other-room", 10, false))
        assertFalse(permit.accepts("102", permit.id, permit.roomId, 10, true))
    }
}
