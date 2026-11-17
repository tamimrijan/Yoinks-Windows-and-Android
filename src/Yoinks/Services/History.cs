using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;

namespace Yoinks.Services;

public sealed class HistoryEntry
{
    public string Url { get; set; } = "";
    public string Title { get; set; } = "";
    public string? Uploader { get; set; }
    public string Platform { get; set; } = "";
    public string? Thumbnail { get; set; }
    public string FilePath { get; set; } = "";
    public string Choice { get; set; } = "";
    public bool IsAudio { get; set; }
    public DateTime When { get; set; } = DateTime.Now;
}

// Recent yoinks, newest first, in %LOCALAPPDATA%\Yoinks\history.json.
public static class History
{
    private const int Limit = 200;
    private static string FilePath => Path.Combine(AppSettings.Folder, "history.json");

    public static event Action? Changed;

    public static List<HistoryEntry> Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<List<HistoryEntry>>(File.ReadAllText(FilePath)) ?? new();
        }
        catch { }
        return new();
    }

    public static void Add(HistoryEntry entry)
    {
        if (!AppSettings.Current.SaveHistory) return;
        var list = Load().Where(e => !string.Equals(e.FilePath, entry.FilePath, StringComparison.OrdinalIgnoreCase)).ToList();
        list.Insert(0, entry);
        Save(list.Take(Limit).ToList());
    }

    public static void Remove(HistoryEntry entry)
    {
        var list = Load().Where(e => !(e.FilePath == entry.FilePath && e.When == entry.When)).ToList();
        Save(list);
    }

    public static void Clear() => Save(new());

    /// <summary>Distinct recent links, for the input box suggestions.</summary>
    public static List<string> RecentUrls() => Load().Select(e => e.Url).Distinct().Take(20).ToList();

    private static void Save(List<HistoryEntry> list)
    {
        try
        {
            Directory.CreateDirectory(AppSettings.Folder);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(list, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // history is a nicety - never let it break a download
        }
        Changed?.Invoke();
    }
}
