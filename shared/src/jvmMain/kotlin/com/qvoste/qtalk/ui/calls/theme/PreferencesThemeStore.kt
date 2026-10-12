package com.qvoste.qtalk.ui.calls.theme

import java.util.prefs.Preferences

class PreferencesThemeStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/appearance")
) {
    fun load(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.get("theme", ThemeMode.DARK.name))
    }.getOrDefault(ThemeMode.DARK)

    fun save(mode: ThemeMode) {
        preferences.put("theme", mode.name)
        preferences.flush()
    }
}
