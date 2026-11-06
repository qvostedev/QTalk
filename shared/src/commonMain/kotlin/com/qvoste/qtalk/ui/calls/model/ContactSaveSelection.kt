package com.qvoste.qtalk.ui.calls.model

import com.qvoste.qtalk.voip.CallStatus

internal data class ContactSaveSelection(val contact: Contact?, val dialNumber: String)

internal fun contactSelectionAfterSave(saved: Contact, contacts: List<Contact>,
    dialNumber: String, call: CallStatus): ContactSaveSelection =
    if (call.state.isInProgress) ContactSaveSelection(
        saved.takeIf { it.matchesCallNumber(call.number) } ?: contacts.firstOrNull { it.matchesCallNumber(call.number) }, dialNumber)
    else ContactSaveSelection(saved, formatPhoneNumber(saved.number))
