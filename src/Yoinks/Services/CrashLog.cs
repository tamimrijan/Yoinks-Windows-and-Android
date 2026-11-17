using System;
using System.IO;

namespace Yoinks.Services;

// Writes %LOCALAPPDATA%\Yoinks\crash.log so bug reports have something to go on.
public static class CrashLog
{
    public static void Write(Exception? ex)
    {
        try
        {
            Directory.CreateDirectory(AppSettings.Folder);
            File.AppendAllText(Path.Combine(AppSettings.Folder, "crash.log"),
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}{Environment.NewLine}{Environment.NewLine}");
        }
        catch
        {
            // nothing else we can do
        }
    }
}
