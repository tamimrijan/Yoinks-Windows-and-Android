package com.tamimrijan.yoinks.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val watchClipboard: Boolean = true,
    val saveHistory: Boolean = true,
    val wifiOnly: Boolean = false,
)

/** Small settings store (SharedPreferences) exposed as a StateFlow so the UI updates live. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("yoinks", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun read() = Settings(
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = sp.getBoolean("dynamicColor", false),
        watchClipboard = sp.getBoolean("watchClipboard", true),
        saveHistory = sp.getBoolean("saveHistory", true),
        wifiOnly = sp.getBoolean("wifiOnly", false),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(_state.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("watchClipboard", s.watchClipboard)
            .putBoolean("saveHistory", s.saveHistory)
            .putBoolean("wifiOnly", s.wifiOnly)
            .apply()
        _state.value = s
    }
}
