package com.tamimrijan.yoinks.engine

import com.tamimrijan.yoinks.util.Format
import org.json.JSONObject

/** Port of yoinks' buildChoices(): up to 8 resolutions as mp4, plus audio-only mp3. */
object Choices {
    private const val MAX_VIDEO_CHOICES = 8

    fun parseInfo(json: String): VideoInfo {
        val r = JSONObject(json)
        fun JSONObject.str(k: String) = if (has(k) && !isNull(k)) optString(k).ifEmpty { null } else null
        fun JSONObject.num(k: String) = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
        val formats = mutableListOf<RawFormat>()
        r.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                formats += RawFormat(
                    id = f.str("format_id") ?: "",
                    ext = f.str("ext"),
                    vcodec = f.str("vcodec"),
                    acodec = f.str("acodec"),
                    height = f.num("height")?.toInt(),
                    abr = f.num("abr"),
                    tbr = f.num("tbr"),
                    filesize = f.num("filesize") ?: f.num("filesize_approx"),
                )
            }
        }
        return VideoInfo(
            title = r.str("title") ?: r.str("fulltitle") ?: "Untitled",
            uploader = r.str("uploader") ?: r.str("channel") ?: r.str("uploader_id"),
            duration = r.num("duration"),
            thumbnail = r.str("thumbnail"),
            formats = formats,
        )
    }

    fun build(info: VideoInfo): List<DownloadChoice> {
        val formats = info.formats
        val choices = mutableListOf<DownloadChoice>()

        val bestAudio = formats.filter { it.hasAudio && !it.hasVideo }.maxByOrNull { it.abr ?: it.tbr ?: 0.0 }
        val audioSize = bestAudio?.filesize ?: 0.0

        val videos = formats.filter { it.hasVideo && (it.height ?: 0) > 0 }
        val heights = videos.mapNotNull { it.height }.distinct().sortedDescending().take(MAX_VIDEO_CHOICES)

        for (h in heights) {
            val best = videos.filter { it.height == h }.maxBy(::score)
            // only estimate when the video part's size is known, or it'd just show the audio size
            val size = best.filesize?.takeIf { it > 0 }?.let { it + if (best.hasAudio) 0.0 else audioSize } ?: 0.0
            choices += DownloadChoice(
                label = "${h}p",
                detail = "MP4 video" + if (size > 0) " · ~${Format.bytes(size)}" else "",
                isAudio = false,
                args = listOf(
                    "-f" to "bv*[height=$h]+ba/b[height=$h]/bv*[height<=$h]+ba/b",
                    "--merge-output-format" to "mp4",
                    // prefer H.264 + AAC: plays on every phone and player (yt-dlp would otherwise pick AV1 + Opus)
                    "-S" to "res,vcodec:h264,acodec:aac",
                ),
            )
        }

        if (choices.isEmpty()) {
            choices += DownloadChoice("Best available", "MP4 video", false, listOf("-f" to "bv*+ba/b", "--merge-output-format" to "mp4", "-S" to "res,vcodec:h264,acodec:aac"))
        }

        choices += DownloadChoice(
            label = "Audio only",
            detail = "MP3" + if (audioSize > 0) " · ~${Format.bytes(audioSize)}" else "",
            isAudio = true,
            args = listOf("-f" to "ba/b", "-x" to null, "--audio-format" to "mp3", "--audio-quality" to "0"),
        )
        return choices
    }

    private fun score(f: RawFormat): Double {
        var s = f.tbr ?: 0.0
        if (f.ext == "mp4") s += 10_000
        if (f.vcodec?.startsWith("avc") == true) s += 5_000
        return s
    }
}
