package com.qvoste.qtalk.ui.calls.model

interface ContactStore {
    fun load(): List<Contact>
    fun save(contacts: List<Contact>)
}

class InMemoryContactStore(
    initialContacts: List<Contact> = emptyList()
) : ContactStore {
    private var contacts = initialContacts.toList()

    override fun load(): List<Contact> = contacts

    override fun save(contacts: List<Contact>) {
        this.contacts = contacts.toList()
    }
}
