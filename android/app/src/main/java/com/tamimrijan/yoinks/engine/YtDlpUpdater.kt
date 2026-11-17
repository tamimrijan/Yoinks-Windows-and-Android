package com.tamimrijan.yoinks.engine

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps yt-dlp fresh. YouTube changes all the time, and an old yt-dlp gets "HTTP Error 403".
 *
 * The library's own updater asks api.github.com for the latest release, which only allows
 * 60 requests an hour per IP. Phones on mobile data share IPs with thousands of others, so it
 * almost always fails ("failed to update youtube-dl"). This uses GitHub's plain release
 * download links instead, which have no such limit.
 */
class YtDlpUpdater(private val context: Context) {
    private val dir get() = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
    private val bin get() = File(dir, "yt-dlp")
    private val prefs = context.getSharedPreferences("ytdlp-updater", Context.MODE_PRIVATE)

    sealed interface Result {
        data class Updated(val from: String?, val to: String) : Result
        data class UpToDate(val version: String) : Result
    }

    /** The version of the yt-dlp the app will actually run (asks yt-dlp itself). */
    fun installedVersion(): String? = runCatching {
        YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).addOption("--version"))
            .out.trim().lines().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Latest release tag, read from the redirect of /releases/latest (not the rate-limited API). */
    fun latestVersion(): String {
        val c = (URL("https://github.com/yt-dlp/yt-dlp/releases/latest").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Yoinks-Android")
        }
        try {
            val location = c.getHeaderField("Location") ?: throw IllegalStateException("GitHub didn't say which version is the newest")
            return location.substringAfterLast("/tag/").substringBefore('?').trim()
        } finally {
            c.disconnect()
        }
    }

    val lastCheck: Long get() = prefs.getLong("lastCheck", 0)

    fun update(): Result {
        val current = installedVersion()
        val latest = latestVersion()
        prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        if (current != null && current == latest) return Result.UpToDate(current)

        dir.mkdirs()
        val tmp = File(dir, "yt-dlp.download")
        download("https://github.com/yt-dlp/yt-dlp/releases/download/$latest/yt-dlp", tmp)
        if (tmp.length() < 1_000_000) {
            tmp.delete()
            throw IllegalStateException("The download was incomplete. Try again on a better connection.")
        }

        // swap it in, but keep the old one until the new one proves it runs
        val backup = File(dir, "yt-dlp.bak")
        if (bin.exists()) bin.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(bin)) {
            tmp.copyTo(bin, overwrite = true)
            tmp.delete()
        }
        val now = installedVersion()
        if (now == null) {
            if (backup.exists()) backup.copyTo(bin, overwrite = true)
            backup.delete()
            throw IllegalStateException("The new yt-dlp didn't start on this phone, so the old one was kept.")
        }
        backup.delete()
        return Result.Updated(current, now)
    }

    private fun download(url: String, target: File) {
        var address = url
        repeat(5) { // follow redirects by hand (github.com → objects.githubusercontent.com)
            val c = (URL(address).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Yoinks-Android")
            }
            try {
                when (c.responseCode) {
                    in 300..399 -> { address = c.getHeaderField("Location") ?: error("Bad redirect"); return@repeat }
                    200 -> {
                        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                        return
                    }
                    else -> throw IllegalStateException("GitHub answered ${c.responseCode} while downloading yt-dlp")
                }
            } finally {
                c.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects while downloading yt-dlp")
    }
}
