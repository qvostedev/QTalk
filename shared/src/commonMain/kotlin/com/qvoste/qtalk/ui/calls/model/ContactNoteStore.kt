package com.qvoste.qtalk.ui.calls.model

data class ContactNote(
    val id: String,
    val contactName: String,
    val contactNumber: String,
    val text: String,
    val pinned: Boolean = false
)

interface ContactNoteStore {
    fun load(): List<ContactNote>
    fun save(notes: List<ContactNote>)
}

class InMemoryContactNoteStore(
    initialNotes: List<ContactNote> = emptyList()
) : ContactNoteStore {
    private var notes = initialNotes.toList()

    override fun load(): List<ContactNote> = notes

    override fun save(notes: List<ContactNote>) {
        this.notes = notes.toList()
    }
}

internal fun ContactNote.belongsTo(contact: Contact): Boolean =
    contactNumber.normalizedContactNumber() == contact.number.normalizedContactNumber() &&
        contactName.normalizedContactName() == contact.name.normalizedContactName()

internal fun String.normalizedContactNumber(): String = filter(Char::isDigit)

private fun String.normalizedContactName(): String = trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
