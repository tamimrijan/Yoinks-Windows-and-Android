package com.tamimrijan.yoinks.util

import java.util.Locale
import kotlin.math.roundToLong

object Format {
    fun bytes(bytes: Double): String {
        if (bytes.isNaN() || bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024; unit++
        }
        val n = if (value >= 10 || unit == 0) value.roundToLong().toString() else String.format(Locale.US, "%.1f", value)
        return "$n ${units[unit]}"
    }

    fun duration(seconds: Double): String {
        if (seconds.isNaN() || seconds <= 0) return ""
        val s = seconds.roundToLong()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun speed(bytesPerSecond: Double): String = if (bytesPerSecond > 0) "${bytes(bytesPerSecond)}/s" else ""

    fun ago(millis: Long): String {
        val d = (System.currentTimeMillis() - millis) / 1000
        return when {
            d < 60 -> "just now"
            d < 3600 -> "${d / 60} min ago"
            d < 86_400 -> "${d / 3600} h ago"
            d < 7 * 86_400 -> "${d / 86_400} d ago"
            else -> java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(java.util.Date(millis))
        }
    }

    /** "12.34MiB" style sizes from yt-dlp's progress lines, in bytes. */
    fun parseSize(number: String?, unit: String?): Double? {
        val n = number?.toDoubleOrNull() ?: return null
        val mult = when (unit?.lowercase()) {
            "b" -> 1.0
            "kib", "kb" -> 1024.0
            "mib", "mb" -> 1024.0 * 1024
            "gib", "gb" -> 1024.0 * 1024 * 1024
            "tib", "tb" -> 1024.0 * 1024 * 1024 * 1024
            else -> return null
        }
        return n * mult
    }
}
