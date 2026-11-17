package com.tamimrijan.yoinks.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val url: String,
    val title: String,
    val uploader: String?,
    val platform: String,
    val thumbnail: String?,
    val filePath: String,
    val choice: String,
    val isAudio: Boolean,
    val sizeBytes: Long,
    val time: Long = System.currentTimeMillis(),
) {
    val exists: Boolean get() = File(filePath).exists()
}

/** Recent yoinks, newest first, in files/history.json. */
class History(context: Context, private val prefs: Prefs) {
    private val file = File(context.filesDir, "history.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<HistoryEntry>> = _items.asStateFlow()

    private fun load(): List<HistoryEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            HistoryEntry(
                url = o.optString("url"),
                title = o.optString("title"),
                uploader = o.optString("uploader").ifEmpty { null },
                platform = o.optString("platform"),
                thumbnail = o.optString("thumbnail").ifEmpty { null },
                filePath = o.optString("filePath"),
                choice = o.optString("choice"),
                isAudio = o.optBoolean("isAudio"),
                sizeBytes = o.optLong("sizeBytes"),
                time = o.optLong("time"),
            )
        }
    }.getOrDefault(emptyList())

    fun add(entry: HistoryEntry) {
        if (!prefs.state.value.saveHistory) return
        save((listOf(entry) + _items.value.filter { it.filePath != entry.filePath }).take(200))
    }

    fun remove(entry: HistoryEntry) = save(_items.value.filter { !(it.filePath == entry.filePath && it.time == entry.time) })

    fun clear() = save(emptyList())

    /** For tests and previews. */
    fun replaceAll(list: List<HistoryEntry>) = save(list)

    private fun save(list: List<HistoryEntry>) {
        _items.value = list
        runCatching {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().apply {
                    put("url", e.url); put("title", e.title); put("uploader", e.uploader ?: "")
                    put("platform", e.platform); put("thumbnail", e.thumbnail ?: ""); put("filePath", e.filePath)
                    put("choice", e.choice); put("isAudio", e.isAudio); put("sizeBytes", e.sizeBytes); put("time", e.time)
                })
            }
            file.writeText(arr.toString())
        }
    }
}
