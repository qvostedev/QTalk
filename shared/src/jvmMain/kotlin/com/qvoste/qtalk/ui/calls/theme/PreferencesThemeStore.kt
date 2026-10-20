package com.qvoste.qtalk.ui.calls.theme

import java.util.prefs.Preferences

class PreferencesThemeStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/qvoste/qtalk/appearance")
) {
    fun loadScale(): Float = runCatching {
        preferences.getFloat("interfaceScale", 0f)
            .takeIf { it == 0f || it in 0.65f..1.5f }?.coerceAtMost(1.25f) ?: 0f
    }.getOrDefault(0f)

    fun saveScale(scale: Float) {
        preferences.putFloat("interfaceScale", scale)
        preferences.flush()
    }

    fun load(): ThemeMode = runCatching {
        ThemeMode.valueOf(preferences.get("theme", ThemeMode.DARK.name))
    }.getOrDefault(ThemeMode.DARK)

    fun save(mode: ThemeMode) {
        preferences.put("theme", mode.name)
        preferences.flush()
    }
}
