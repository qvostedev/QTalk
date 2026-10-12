package com.qvoste.qtalk.ui.calls.model

import java.util.prefs.Preferences

class PreferencesContactNoteStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/contactNotes")
) : ContactNoteStore {

    @Synchronized
    override fun load(): List<ContactNote> {
        val count = preferences.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        return buildList(count) {
            repeat(count) { index ->
                val prefix = "$index."
                val id = preferences.get(prefix + "id", "").trim()
                val text = preferences.get(prefix + "text", "").trim()
                if (id.isBlank() || text.isBlank()) return@repeat
                add(
                    ContactNote(
                        id = id,
                        contactName = preferences.get(prefix + "contactName", ""),
                        contactNumber = preferences.get(prefix + "contactNumber", ""),
                        text = text,
                        pinned = preferences.getBoolean(prefix + "pinned", false)
                    )
                )
            }
        }
    }

    @Synchronized
    override fun save(notes: List<ContactNote>) {
        val previousCount = preferences.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        notes.forEachIndexed { index, note ->
            val prefix = "$index."
            preferences.put(prefix + "id", note.id)
            preferences.put(prefix + "contactName", note.contactName)
            preferences.put(prefix + "contactNumber", note.contactNumber)
            preferences.put(prefix + "text", note.text)
            preferences.putBoolean(prefix + "pinned", note.pinned)
        }
        for (index in notes.size until previousCount) {
            val prefix = "$index."
            FIELDS.forEach { preferences.remove(prefix + it) }
        }
        preferences.putInt(KEY_COUNT, notes.size)
        preferences.flush()
    }

    private companion object {
        const val KEY_COUNT = "count"
        val FIELDS = listOf("id", "contactName", "contactNumber", "text", "pinned")
    }
}
