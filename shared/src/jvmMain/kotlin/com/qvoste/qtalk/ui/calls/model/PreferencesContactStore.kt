package com.qvoste.qtalk.ui.calls.model

import java.util.prefs.Preferences

class PreferencesContactStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/contacts")
) : ContactStore {

    @Synchronized
    override fun load(): List<Contact> {
        if (!preferences.getBoolean(KEY_INITIALIZED, false)) {
            return emptyList()
        }

        val count = preferences.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        val contacts = buildList(count) {
            repeat(count) { index ->
                val prefix = "$index."
                val name = preferences.get(prefix + "name", "").trim()
                if (name.isBlank()) return@repeat
                add(
                    Contact(
                        name = name,
                        number = preferences.get(prefix + "number", ""),
                        favorite = preferences.getBoolean(prefix + "favorite", false),
                        position = preferences.get(prefix + "position", ""),
                        department = preferences.get(prefix + "department", ""),
                        company = preferences.get(prefix + "company", ""),
                        telegram = preferences.get(prefix + "telegram", ""),
                        office = preferences.get(prefix + "office", ""),
                        phone = preferences.get(prefix + "phone", ""),
                        source = runCatching {
                            ContactSource.valueOf(preferences.get(prefix + "source", ContactSource.LEGACY.name))
                        }.getOrDefault(ContactSource.LEGACY)
                    )
                )
            }
        }
        if (preferences.getInt(KEY_SCHEMA_VERSION, 0) < CURRENT_SCHEMA_VERSION) {
            val migrated = contacts.map { contact ->
                val department = when (contact.department.trim().lowercase()) {
                    "программисты 1с", "системные администраторы" -> "Отдел автоматизации"
                    else -> contact.department
                }
                contact.copy(department = department)
            }
            save(migrated)
            return migrated
        }
        return contacts
    }

    @Synchronized
    override fun save(contacts: List<Contact>) {
        val previousCount = preferences.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        contacts.forEachIndexed { index, contact ->
            val prefix = "$index."
            preferences.put(prefix + "name", contact.name)
            preferences.put(prefix + "number", contact.number)
            preferences.putBoolean(prefix + "favorite", contact.favorite)
            preferences.put(prefix + "position", contact.position)
            preferences.put(prefix + "department", contact.department)
            preferences.put(prefix + "company", contact.company)
            preferences.put(prefix + "telegram", contact.telegram)
            preferences.put(prefix + "office", contact.office)
            preferences.put(prefix + "phone", contact.phone)
            preferences.put(prefix + "source", contact.source.name)
        }
        for (index in contacts.size until previousCount) {
            val prefix = "$index."
            FIELDS.forEach { preferences.remove(prefix + it) }
        }
        preferences.putInt(KEY_COUNT, contacts.size)
        preferences.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        preferences.putBoolean(KEY_INITIALIZED, true)
        preferences.flush()
    }

    private companion object {
        const val KEY_INITIALIZED = "initialized"
        const val KEY_COUNT = "count"
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val CURRENT_SCHEMA_VERSION = 2
        val FIELDS = listOf(
            "name", "number", "favorite", "position", "department",
            "company", "telegram", "office", "phone", "source"
        )
    }
}
