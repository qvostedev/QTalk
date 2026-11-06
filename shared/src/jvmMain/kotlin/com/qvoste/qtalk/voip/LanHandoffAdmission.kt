package com.qvoste.qtalk.voip

/** SIP identity/contact are bound to capability-negotiated LAN membership, not employee authentication. */
internal object LanHandoffAdmission {
    fun accepts(permit: ConferenceHandoffPermit, remote: String, header: String?, room: String,
        now: Long, staged: Boolean, sameOriginal: Boolean, fromHost: String?, contact: LanSipEndpoint?,
        expected: LanSipEndpoint?): Boolean = sameOriginal && expected != null &&
        fromHost == expected.host && contact == expected &&
        permit.accepts(remote, header, room, now, staged)
}
