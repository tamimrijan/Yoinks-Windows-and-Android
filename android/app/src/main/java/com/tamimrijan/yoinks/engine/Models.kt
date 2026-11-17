package com.tamimrijan.yoinks.engine

import com.tamimrijan.yoinks.data.HistoryEntry
import com.tamimrijan.yoinks.util.Platform

data class VideoInfo(
    val title: String,
    val uploader: String?,
    val duration: Double?,
    val thumbnail: String?,
    val formats: List<RawFormat>,
)

data class RawFormat(
    val id: String,
    val ext: String?,
    val vcodec: String?,
    val acodec: String?,
    val height: Int?,
    val abr: Double?,
    val tbr: Double?,
    val filesize: Double?,
) {
    val hasVideo get() = !vcodec.isNullOrEmpty() && vcodec != "none"
    val hasAudio get() = !acodec.isNullOrEmpty() && acodec != "none"
}

data class DownloadChoice(
    val label: String,
    val detail: String,
    val isAudio: Boolean,
    val args: List<Pair<String, String?>>,
)

data class Progress(
    val fraction: Float?,        // null until yt-dlp knows the size
    val downloadedBytes: Double,
    val totalBytes: Double?,
    val speed: Double?,
    val etaSeconds: Long?,
    val part: Int,
    val totalParts: Int,
)

/** What the Yoink screen is showing. Mirrors the phases of the original yoinks. */
sealed interface Phase {
    data class Input(val warning: String? = null) : Phase
    data class Probing(val url: String, val platform: Platform, val status: String) : Phase
    data class Picking(val url: String, val platform: Platform, val info: VideoInfo, val choices: List<DownloadChoice>) : Phase
    data class Downloading(
        val url: String,
        val platform: Platform,
        val info: VideoInfo,
        val choice: DownloadChoice,
        val progress: Progress? = null,
        val processing: Boolean = false,
        val refreshing: Boolean = false,
        val note: String? = null,
    ) : Phase
    data class Done(val entry: HistoryEntry) : Phase
    data class Error(val message: String, val canRetryDownload: Boolean) : Phase
}

sealed interface EngineStatus {
    data object Starting : EngineStatus
    data object Ready : EngineStatus
    data class Failed(val message: String) : EngineStatus
}
