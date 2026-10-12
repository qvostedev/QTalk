package com.qvoste.qtalk.ui.calls.model

/** The PBX's in-call blind-transfer feature code followed by an internal extension. */
internal fun transferCodeFor(contact: Contact): String? {
    val extension = contact.number.trim()
    if (!contact.isEmployee || extension.length !in 2..5 || !extension.all(Char::isDigit)) return null
    return "**$extension"
}
