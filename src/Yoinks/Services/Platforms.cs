using System;
using System.Linq;

namespace Yoinks.Services;

public sealed record Platform(string Key, string Label, string Glyph, string Color);

public static class Platforms
{
    private static readonly (string[] Hosts, Platform Platform)[] Known =
    {
        (new[] { "youtube.com", "youtu.be", "music.youtube.com" }, new Platform("youtube", "YouTube", "", "#FF0033")),
        (new[] { "x.com", "twitter.com" }, new Platform("x", "X / Twitter", "", "#888888")),
        (new[] { "instagram.com" }, new Platform("instagram", "Instagram", "", "#E1306C")),
        (new[] { "threads.net", "threads.com" }, new Platform("threads", "Threads", "", "#888888")),
        (new[] { "tiktok.com" }, new Platform("tiktok", "TikTok", "", "#25F4EE")),
        (new[] { "vimeo.com" }, new Platform("vimeo", "Vimeo", "", "#1AB7EA")),
        (new[] { "twitch.tv" }, new Platform("twitch", "Twitch", "", "#9146FF")),
        (new[] { "reddit.com", "redd.it" }, new Platform("reddit", "Reddit", "", "#FF4500")),
        (new[] { "facebook.com", "fb.watch" }, new Platform("facebook", "Facebook", "", "#1877F2")),
        (new[] { "soundcloud.com" }, new Platform("soundcloud", "SoundCloud", "", "#FF5500")),
    };

    public static Platform Detect(string url)
    {
        string host;
        try
        {
            host = new Uri(url.Trim()).Host.ToLowerInvariant();
        }
        catch
        {
            return new Platform("unknown", "Unknown site", "", "#888888");
        }

        foreach (var (hosts, platform) in Known)
            if (hosts.Any(h => host == h || host.EndsWith("." + h, StringComparison.Ordinal)))
                return platform;

        if (host.StartsWith("www.", StringComparison.Ordinal)) host = host[4..];
        return new Platform("generic", host, "", "#888888");
    }

    public static bool IsProbablyUrl(string? input)
    {
        if (string.IsNullOrWhiteSpace(input)) return false;
        return Uri.TryCreate(input.Trim(), UriKind.Absolute, out var u)
               && (u.Scheme == Uri.UriSchemeHttp || u.Scheme == Uri.UriSchemeHttps)
               && u.Host.Contains('.');
    }
}
