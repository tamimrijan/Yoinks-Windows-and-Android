using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace Yoinks.Services;

// Unpackaged apps can't use ApplicationData, so settings live in a small JSON file
// under %LOCALAPPDATA%\Yoinks (the uninstaller offers to remove it).
public sealed class AppSettings
{
    public string Theme { get; set; } = "System";          // System | Light | Dark
    public string DownloadFolder { get; set; } = "";       // empty = installer default, then Downloads
    public bool OpenFolderWhenDone { get; set; }
    public bool WatchClipboard { get; set; } = true;
    public bool SaveHistory { get; set; } = true;

    public static string Folder { get; } =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Yoinks");

    private static string FilePath => Path.Combine(Folder, "settings.json");

    public static AppSettings Current { get; } = Load();

    private static AppSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(FilePath)) ?? new AppSettings();
        }
        catch
        {
            // corrupted file: fall back to defaults
        }
        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Folder);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // settings are a convenience, never crash over them
        }
    }

    /// <summary>The folder downloads go to: user choice, then the one picked in the installer, then Downloads.</summary>
    public string ResolvedDownloadFolder
    {
        get
        {
            if (!string.IsNullOrWhiteSpace(DownloadFolder)) return DownloadFolder;
            var fromInstaller = InstallerDefaultFolder();
            if (!string.IsNullOrWhiteSpace(fromInstaller)) return fromInstaller!;
            return KnownDownloadsFolder();
        }
    }

    private static string? InstallerDefaultFolder()
    {
        try
        {
            var file = Path.Combine(AppContext.BaseDirectory, "defaults.json");
            if (!File.Exists(file)) return null;
            using var doc = JsonDocument.Parse(File.ReadAllText(file));
            if (doc.RootElement.TryGetProperty("DownloadFolder", out var f))
            {
                var value = Environment.ExpandEnvironmentVariables(f.GetString() ?? "");
                return string.IsNullOrWhiteSpace(value) ? null : value;
            }
        }
        catch { }
        return null;
    }

    public static string KnownDownloadsFolder()
    {
        try
        {
            var id = new Guid("374DE290-123F-4565-9164-39C4925E467B");
            if (SHGetKnownFolderPath(ref id, 0, IntPtr.Zero, out var ptr) == 0)
            {
                var path = Marshal.PtrToStringUni(ptr);
                Marshal.FreeCoTaskMem(ptr);
                if (!string.IsNullOrEmpty(path)) return path;
            }
        }
        catch { }
        return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
    }

    [DllImport("shell32.dll")]
    private static extern int SHGetKnownFolderPath(ref Guid id, uint flags, IntPtr token, out IntPtr path);
}
