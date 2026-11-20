package com.tamimrijan.yoinks.engine

/**
 * What Quick download grabs: picked in the "Configure before download" sheet (and remembered),
 * so it can start without waiting for the video's info first.
 */
data class QuickOptions(
    val audio: Boolean = false,
    /** Highest video height to allow; 0 means the best there is. */
    val maxHeight: Int = 1080,
    /** For audio: true = MP3, false = M4A (original quality, no re-encoding). */
    val mp3: Boolean = true,
    val subtitles: Boolean = false,
    val thumbnail: Boolean = false,
) {
    val label: String
        get() = when {
            audio -> if (mp3) "MP3" else "M4A"
            maxHeight == 0 -> "Best"
            else -> "${maxHeight}p"
        }

    fun toChoice(): DownloadChoice {
        val extras = buildList<Pair<String, String?>> {
            if (thumbnail) add("--embed-thumbnail" to null)
            if (subtitles && !audio) {
                add("--embed-subs" to null)
                add("--sub-langs" to "en.*,bn.*,hi.*")
                // subtitles are an extra: if YouTube refuses them (e.g. HTTP 429), still save the video
                add("--ignore-errors" to null)
            }
        }
        return if (audio) {
            DownloadChoice(
                label = "Audio only",
                detail = if (mp3) "MP3" else "M4A",
                isAudio = true,
                args = (if (mp3) listOf("-f" to "ba/b", "-x" to null, "--audio-format" to "mp3", "--audio-quality" to "0")
                else listOf("-f" to "ba[ext=m4a]/ba/b", "-x" to null, "--audio-format" to "m4a")) + extras,
            )
        } else {
            val cap = if (maxHeight > 0) "[height<=$maxHeight]" else ""
            DownloadChoice(
                label = if (maxHeight > 0) "${maxHeight}p" else "Best",
                detail = "MP4 video",
                isAudio = false,
                args = listOf(
                    "-f" to "bv*$cap+ba/b$cap/bv*+ba/b",
                    "--merge-output-format" to "mp4",
                    // H.264 + AAC plays everywhere (yt-dlp would otherwise pick AV1 + Opus)
                    "-S" to "res,vcodec:h264,acodec:aac",
                ) + extras,
            )
        }
    }

    companion object {
        val HEIGHTS = listOf(0, 1080, 720, 480, 360)
    }
}
