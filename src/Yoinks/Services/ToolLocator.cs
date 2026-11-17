using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Yoinks.Services;

public sealed record ToolStatus(string? YtDlpPath, string? YtDlpVersion, string? FfmpegPath, string? JsRuntime, string? JsRuntimePath)
{
    public bool Ready => YtDlpVersion != null;
    public bool HasFfmpeg => FfmpegPath != null;
    public bool HasJsRuntime => JsRuntimePath != null;
}

// Finds yt-dlp, ffmpeg and a JavaScript runtime (Deno) for YouTube.
// Order: fresh copies the app downloaded itself (%LOCALAPPDATA%\Yoinks\bin),
// then the ones the installer shipped ({app}\tools), then whatever is on PATH.
public static class ToolLocator
{
    public static string BundledDir { get; } = Path.Combine(AppContext.BaseDirectory, "tools");
    public static string UserBinDir { get; } = Path.Combine(AppSettings.Folder, "bin");

    private const string YtDlpUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private const string DenoUrl = "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip";

    private static readonly HttpClient Http = CreateHttp();

    private static HttpClient CreateHttp()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromMinutes(15) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd("Yoinks-Desktop/1.0");
        return c;
    }

    public static string? FindYtDlp() => FirstExisting("yt-dlp.exe");

    public static string? FindFfmpeg() => FirstExisting("ffmpeg.exe");

    public static (string Name, string Path)? FindJsRuntime()
    {
        var deno = FirstExisting("deno.exe");
        if (deno != null) return ("deno", deno);
        var node = OnPath("node.exe");
        if (node != null) return ("node", node);
        return null;
    }

    private static string? FirstExisting(string exe)
    {
        foreach (var dir in new[] { UserBinDir, BundledDir })
        {
            var p = Path.Combine(dir, exe);
            if (File.Exists(p)) return p;
        }
        return OnPath(exe);
    }

    private static string? OnPath(string exe)
    {
        var path = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (var dir in path.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            try
            {
                var p = Path.Combine(dir, exe);
                if (File.Exists(p)) return p;
            }
            catch { /* bad PATH entry */ }
        }
        return null;
    }

    public static async Task<ToolStatus> CheckAsync()
    {
        var ytdlp = FindYtDlp();
        string? version = null;
        if (ytdlp != null)
            version = (await RunQuickAsync(ytdlp, "--version"))?.Trim().Split('\n').FirstOrDefault()?.Trim();
        var js = FindJsRuntime();
        return new ToolStatus(ytdlp, string.IsNullOrEmpty(version) ? null : version, FindFfmpeg(), js?.Name, js?.Path);
    }

    public static async Task<string?> RunQuickAsync(string file, params string[] args)
    {
        try
        {
            var psi = new ProcessStartInfo(file)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                StandardOutputEncoding = Encoding.UTF8,
            };
            foreach (var a in args) psi.ArgumentList.Add(a);
            using var p = Process.Start(psi)!;
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(30));
            var output = await p.StandardOutput.ReadToEndAsync(cts.Token);
            await p.WaitForExitAsync(cts.Token);
            return p.ExitCode == 0 ? output : null;
        }
        catch
        {
            return null;
        }
    }

    /// <summary>Downloads the latest standalone yt-dlp.exe into the per-user bin folder.</summary>
    public static async Task UpdateYtDlpAsync(IProgress<double>? progress, CancellationToken cancel)
    {
        Directory.CreateDirectory(UserBinDir);
        var target = Path.Combine(UserBinDir, "yt-dlp.exe");
        var tmp = target + ".download";
        await DownloadFileAsync(YtDlpUrl, tmp, progress, cancel);
        File.Move(tmp, target, overwrite: true);
    }

    /// <summary>Downloads Deno (the JS runtime yt-dlp uses for YouTube) into the per-user bin folder.</summary>
    public static async Task InstallDenoAsync(IProgress<double>? progress, CancellationToken cancel)
    {
        Directory.CreateDirectory(UserBinDir);
        var zip = Path.Combine(UserBinDir, "deno.zip.download");
        await DownloadFileAsync(DenoUrl, zip, progress, cancel);
        try
        {
            using var archive = ZipFile.OpenRead(zip);
            var entry = archive.Entries.FirstOrDefault(e => e.Name.Equals("deno.exe", StringComparison.OrdinalIgnoreCase))
                        ?? throw new InvalidOperationException("deno.exe was not in the download.");
            entry.ExtractToFile(Path.Combine(UserBinDir, "deno.exe"), overwrite: true);
        }
        finally
        {
            try { File.Delete(zip); } catch { }
        }
    }

    private static async Task DownloadFileAsync(string url, string target, IProgress<double>? progress, CancellationToken cancel)
    {
        using var response = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancel);
        response.EnsureSuccessStatusCode();
        long? total = response.Content.Headers.ContentLength;
        await using var src = await response.Content.ReadAsStreamAsync(cancel);
        await using (var dst = File.Create(target))
        {
            var buffer = new byte[81920];
            long done = 0;
            int read;
            while ((read = await src.ReadAsync(buffer, cancel)) > 0)
            {
                await dst.WriteAsync(buffer.AsMemory(0, read), cancel);
                done += read;
                if (total > 0) progress?.Report((double)done / total.Value);
            }
        }
    }
}
