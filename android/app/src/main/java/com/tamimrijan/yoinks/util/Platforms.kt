package com.tamimrijan.yoinks.util

import android.net.Uri

data class Platform(val key: String, val label: String)

object Platforms {
    private val known = listOf(
        listOf("youtube.com", "youtu.be", "music.youtube.com") to Platform("youtube", "YouTube"),
        listOf("x.com", "twitter.com") to Platform("x", "X / Twitter"),
        listOf("instagram.com") to Platform("instagram", "Instagram"),
        listOf("threads.net", "threads.com") to Platform("threads", "Threads"),
        listOf("tiktok.com") to Platform("tiktok", "TikTok"),
        listOf("vimeo.com") to Platform("vimeo", "Vimeo"),
        listOf("twitch.tv") to Platform("twitch", "Twitch"),
        listOf("reddit.com", "redd.it") to Platform("reddit", "Reddit"),
        listOf("facebook.com", "fb.watch") to Platform("facebook", "Facebook"),
        listOf("soundcloud.com") to Platform("soundcloud", "SoundCloud"),
    )

    fun detect(url: String): Platform {
        val host = runCatching { Uri.parse(url.trim()).host?.lowercase() }.getOrNull()
            ?: return Platform("unknown", "Unknown site")
        for ((hosts, platform) in known) {
            if (hosts.any { host == it || host.endsWith(".$it") }) return platform
        }
        return Platform("generic", host.removePrefix("www."))
    }

    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    fun isProbablyUrl(input: String?): Boolean {
        val t = input?.trim() ?: return false
        return t.matches(Regex("""(?i)^https?://[^\s/]+\.[^\s]+$"""))
    }

    /**
     * Pulls the link out of shared text ("Check this out! https://youtu.be/...") or a yoinks: link.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var t = text.trim()
        if (t.startsWith("yoinks:", ignoreCase = true)) {
            t = Uri.decode(t.substring(7).trimStart('/'))
            if (t.startsWith("https//", true)) t = "https://" + t.substring(7)
            if (t.startsWith("http//", true)) t = "http://" + t.substring(6)
        }
        return urlRegex.find(t)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
    }
}
