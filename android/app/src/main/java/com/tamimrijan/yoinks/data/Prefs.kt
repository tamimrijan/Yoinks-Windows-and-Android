package com.tamimrijan.yoinks.data

import android.content.Context
import com.tamimrijan.yoinks.engine.QuickOptions
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
    /** Last choices in the Quick download sheet (used as its defaults). */
    val quick: QuickOptions = QuickOptions(),
    /** Quick download shows the options sheet first (like Seal); off = download instantly with [quick]. */
    val quickAsk: Boolean = true,
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
        quick = QuickOptions(
            audio = sp.getBoolean("quickAudio", false),
            maxHeight = sp.getInt("quickHeight", 1080),
            mp3 = sp.getBoolean("quickMp3", true),
            subtitles = sp.getBoolean("quickSubs", false),
            thumbnail = sp.getBoolean("quickThumb", false),
        ),
        quickAsk = sp.getBoolean("quickAsk", true),
    )

    fun update(change: (Settings) -> Settings) {
        val s = change(_state.value)
        sp.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("watchClipboard", s.watchClipboard)
            .putBoolean("saveHistory", s.saveHistory)
            .putBoolean("wifiOnly", s.wifiOnly)
            .putBoolean("quickAudio", s.quick.audio)
            .putInt("quickHeight", s.quick.maxHeight)
            .putBoolean("quickMp3", s.quick.mp3)
            .putBoolean("quickSubs", s.quick.subtitles)
            .putBoolean("quickThumb", s.quick.thumbnail)
            .putBoolean("quickAsk", s.quickAsk)
            .apply()
        _state.value = s
    }
}
