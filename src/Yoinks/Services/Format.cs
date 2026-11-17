using System;

namespace Yoinks.Services;

public static class Format
{
    public static string Bytes(double bytes)
    {
        if (double.IsNaN(bytes) || bytes <= 0) return "";
        string[] units = { "B", "KB", "MB", "GB", "TB" };
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.Length - 1)
        {
            value /= 1024;
            unit++;
        }
        return $"{(value >= 10 || unit == 0 ? Math.Round(value).ToString("0") : value.ToString("0.0"))} {units[unit]}";
    }

    public static string Duration(double seconds)
    {
        if (double.IsNaN(seconds) || seconds <= 0) return "";
        var t = TimeSpan.FromSeconds(Math.Round(seconds));
        return t.TotalHours >= 1 ? $"{(int)t.TotalHours}:{t.Minutes:00}:{t.Seconds:00}" : $"{t.Minutes}:{t.Seconds:00}";
    }

    public static string Speed(double bytesPerSecond) =>
        bytesPerSecond > 0 ? $"{Bytes(bytesPerSecond)}/s" : "";

    public static string Ago(DateTime when)
    {
        var d = DateTime.Now - when;
        if (d.TotalMinutes < 1) return "just now";
        if (d.TotalHours < 1) return $"{(int)d.TotalMinutes} min ago";
        if (d.TotalDays < 1) return $"{(int)d.TotalHours} h ago";
        if (d.TotalDays < 7) return $"{(int)d.TotalDays} d ago";
        return when.ToString("d MMM yyyy");
    }
}
