using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace Yoinks.Services;

public sealed class VideoInfo
{
    public string Title { get; init; } = "";
    public string? Uploader { get; init; }
    public double? Duration { get; init; }
    public string? Thumbnail { get; init; }
    public string? WebpageUrl { get; init; }
    public string? Extractor { get; init; }
    public List<RawFormat> Formats { get; init; } = new();
}

public sealed class RawFormat
{
    public string Id { get; init; } = "";
    public string? Ext { get; init; }
    public string? VCodec { get; init; }
    public string? ACodec { get; init; }
    public int? Height { get; init; }
    public double? Abr { get; init; }
    public double? Tbr { get; init; }
    public double? Filesize { get; init; }

    public bool HasVideo => !string.IsNullOrEmpty(VCodec) && VCodec != "none";
    public bool HasAudio => !string.IsNullOrEmpty(ACodec) && ACodec != "none";
}

public sealed record ProbeResult(VideoInfo Info, string InfoJsonPath);

public sealed class DownloadChoice
{
    public required string Label { get; init; }
    public required string Detail { get; init; }
    public required bool IsAudio { get; init; }
    public required string[] Args { get; init; }
    public string Glyph => IsAudio ? "" : "";

    // read by screen readers for the list item
    public override string ToString() => $"{Label}, {Detail}";
}

public sealed record DownloadProgress(double DownloadedBytes, double? TotalBytes, double? Speed, double? Eta, int Part, int TotalParts);

public sealed class YtDlpException : Exception
{
    public YtDlpException(string message) : base(message) { }
}

// Port of yoinks' lib/ytdlp.ts: probe a link, offer resolutions + mp3, download with live progress.
public static class YtDlp
{
    private const string ProgressPrefix = "YOINK|";
    private const string ProgressTemplate =
        ProgressPrefix + "%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s";

    private const int MaxVideoChoices = 8;

    /// <summary>Args every yt-dlp call gets: where ffmpeg lives and which JS runtime solves YouTube's challenges.</summary>
    private static IEnumerable<string> CommonArgs()
    {
        var ffmpeg = ToolLocator.FindFfmpeg();
        if (ffmpeg != null)
        {
            yield return "--ffmpeg-location";
            yield return Path.GetDirectoryName(ffmpeg)!;
        }
        var js = ToolLocator.FindJsRuntime();
        if (js != null)
        {
            yield return "--js-runtimes";
            yield return $"{js.Value.Name}:{js.Value.Path}";
        }
        yield return "--no-warnings";
        yield return "--no-playlist";
        yield return "--encoding";
        yield return "utf-8";
    }

    public static async Task<ProbeResult> ProbeAsync(string url, CancellationToken cancel)
    {
        var ytdlp = ToolLocator.FindYtDlp() ?? throw new YtDlpException("yt-dlp is missing. Open Settings and click Repair.");
        var args = new List<string>(CommonArgs()) { "-J", url };

        var stdout = new StringBuilder();
        var stderr = new StringBuilder();
        int code = await RunAsync(ytdlp, args, line => stdout.AppendLine(line), line => stderr.AppendLine(line), cancel);
        cancel.ThrowIfCancellationRequested();
        if (code != 0)
            throw new YtDlpException(CleanError(stderr.ToString()) ?? $"yt-dlp exited with code {code}");

        var json = stdout.ToString();
        VideoInfo info;
        try
        {
            info = ParseInfo(json);
        }
        catch
        {
            throw new YtDlpException("Could not read the video info from yt-dlp.");
        }

        var infoJsonPath = Path.Combine(Path.GetTempPath(), $"yoinks-info-{Environment.ProcessId}-{DateTime.Now.Ticks}.json");
        await File.WriteAllTextAsync(infoJsonPath, json, new UTF8Encoding(false), cancel);
        return new ProbeResult(info, infoJsonPath);
    }

    private static VideoInfo ParseInfo(string json)
    {
        using var doc = JsonDocument.Parse(json);
        var r = doc.RootElement;
        var formats = new List<RawFormat>();
        if (r.TryGetProperty("formats", out var fs) && fs.ValueKind == JsonValueKind.Array)
        {
            foreach (var f in fs.EnumerateArray())
            {
                formats.Add(new RawFormat
                {
                    Id = Str(f, "format_id") ?? "",
                    Ext = Str(f, "ext"),
                    VCodec = Str(f, "vcodec"),
                    ACodec = Str(f, "acodec"),
                    Height = (int?)Num(f, "height"),
                    Abr = Num(f, "abr"),
                    Tbr = Num(f, "tbr"),
                    Filesize = Num(f, "filesize") ?? Num(f, "filesize_approx"),
                });
            }
        }
        return new VideoInfo
        {
            Title = Str(r, "title") ?? Str(r, "fulltitle") ?? "Untitled",
            Uploader = Str(r, "uploader") ?? Str(r, "channel") ?? Str(r, "uploader_id"),
            Duration = Num(r, "duration"),
            Thumbnail = Str(r, "thumbnail"),
            WebpageUrl = Str(r, "webpage_url"),
            Extractor = Str(r, "extractor_key"),
            Formats = formats,
        };
    }

    private static string? Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

    private static double? Num(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.Number ? v.GetDouble() : null;

    public static List<DownloadChoice> BuildChoices(VideoInfo info)
    {
        var formats = info.Formats;
        var choices = new List<DownloadChoice>();

        var bestAudio = formats.Where(f => f.HasAudio && !f.HasVideo)
                               .OrderByDescending(f => f.Abr ?? f.Tbr ?? 0)
                               .FirstOrDefault();
        double audioSize = bestAudio?.Filesize ?? 0;

        var videos = formats.Where(f => f.HasVideo && f.Height > 0).ToList();
        var heights = videos.Select(f => f.Height!.Value).Distinct().OrderByDescending(h => h).Take(MaxVideoChoices);

        foreach (var height in heights)
        {
            var best = videos.Where(f => f.Height == height).OrderByDescending(Score).First();
            // only estimate when the video part's size is known, or it'd just show the audio size
            double size = best.Filesize is > 0 ? best.Filesize.Value + (best.HasAudio ? 0 : audioSize) : 0;
            choices.Add(new DownloadChoice
            {
                Label = $"{height}p",
                Detail = "MP4 video" + (size > 0 ? $"  ·  ~{Format.Bytes(size)}" : ""),
                IsAudio = false,
                Args = new[]
                {
                    "-f", $"bv*[height={height}]+ba/b[height={height}]/bv*[height<={height}]+ba/b",
                    "--merge-output-format", "mp4",
                    // prefer H.264 + AAC: plays everywhere (yt-dlp would otherwise pick AV1 + Opus,
                    // which Windows 10 can't play without an extra codec)
                    "-S", "res,vcodec:h264,acodec:aac",
                },
            });
        }

        if (choices.Count == 0)
        {
            choices.Add(new DownloadChoice
            {
                Label = "Best available",
                Detail = "MP4 video",
                IsAudio = false,
                Args = new[] { "-f", "bv*+ba/b", "--merge-output-format", "mp4", "-S", "res,vcodec:h264,acodec:aac" },
            });
        }

        choices.Add(new DownloadChoice
        {
            Label = "Audio only",
            Detail = "MP3" + (audioSize > 0 ? $"  ·  ~{Format.Bytes(audioSize)}" : ""),
            IsAudio = true,
            Args = new[] { "-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "0" },
        });

        return choices;
    }

    private static double Score(RawFormat f)
    {
        double score = f.Tbr ?? 0;
        if (f.Ext == "mp4") score += 10_000;
        if (f.VCodec?.StartsWith("avc", StringComparison.Ordinal) == true) score += 5_000;
        return score;
    }

    /// <summary>Downloads one choice and returns the final file path.</summary>
    public static async Task<string> DownloadAsync(
        string url, string? infoJsonPath, DownloadChoice choice, string outDir,
        Action<DownloadProgress> onProgress, Action onProcessing, CancellationToken cancel)
    {
        var ytdlp = ToolLocator.FindYtDlp() ?? throw new YtDlpException("yt-dlp is missing. Open Settings and click Repair.");
        Directory.CreateDirectory(outDir);

        var args = new List<string>();
        if (infoJsonPath != null) { args.Add("--load-info-json"); args.Add(infoJsonPath); }
        else args.Add(url);
        args.AddRange(choice.Args);
        args.AddRange(CommonArgs());
        args.AddRange(new[]
        {
            "--newline",
            // --print implies --quiet, which hides the progress and [Merger] lines we read
            "--no-quiet",
            "--progress",
            "--progress-template", "download:" + ProgressTemplate,
            "--print", "after_move:filepath",
            "--no-simulate",
            "--no-mtime",
            "-o", Path.Combine(outDir, "%(title).80s.%(ext)s"),
        });

        string filepath = "";
        int part = 0, totalParts = 1;
        double lastDownloaded = 0;
        var destinations = new List<string>();
        var stderr = new StringBuilder();

        void OnLine(string raw)
        {
            var line = raw.Trim();
            if (line.Length == 0) return;
            if (line.StartsWith(ProgressPrefix, StringComparison.Ordinal))
            {
                var p = line[ProgressPrefix.Length..].Split('|');
                double downloaded = ToNumber(p.ElementAtOrDefault(0)) ?? 0;
                if (downloaded < lastDownloaded) part++;
                lastDownloaded = downloaded;
                onProgress(new DownloadProgress(downloaded,
                    ToNumber(p.ElementAtOrDefault(1)) ?? ToNumber(p.ElementAtOrDefault(2)),
                    ToNumber(p.ElementAtOrDefault(3)), ToNumber(p.ElementAtOrDefault(4)),
                    Math.Min(part, totalParts - 1), totalParts));
            }
            else if (line.Contains("Downloading 1 format(s):"))
            {
                // "[info] xxx: Downloading 1 format(s): 395+251" - each id is one file
                totalParts = Math.Max(1, line.Split("format(s):").Last().Trim().Split('+').Length);
            }
            else if (line.Contains("[Merger]") || line.Contains("[ExtractAudio]") || line.Contains("[VideoConvertor]") || line.Contains("[FixupM3u8]"))
            {
                var m = Regex.Match(line, "^\\[Merger\\] Merging formats into \"(.+)\"$");
                var x = Regex.Match(line, "^\\[ExtractAudio\\] Destination: (.+)$");
                if (m.Success) destinations.Add(m.Groups[1].Value);
                if (x.Success) destinations.Add(x.Groups[1].Value);
                onProcessing();
            }
            else if (line.StartsWith("[download] Destination: ", StringComparison.Ordinal))
            {
                destinations.Add(line["[download] Destination: ".Length..]);
            }
            else if (Path.IsPathFullyQualified(line) && !line.StartsWith('['))
            {
                filepath = line;
            }
        }

        int code = await RunAsync(ytdlp, args, OnLine, l => stderr.AppendLine(l), cancel);

        if (cancel.IsCancellationRequested)
        {
            RemovePartials(destinations);
            throw new OperationCanceledException(cancel);
        }
        if (code == 0 && filepath.Length > 0)
            return filepath;
        throw new YtDlpException(CleanError(stderr.ToString()) ?? $"Download failed (yt-dlp exit code {code}).");
    }

    private static void RemovePartials(IEnumerable<string> destinations)
    {
        foreach (var dest in destinations)
            foreach (var f in new[] { dest, dest + ".part", dest + ".ytdl" })
                try { if (File.Exists(f)) File.Delete(f); } catch { }
    }

    private static double? ToNumber(string? value)
    {
        if (string.IsNullOrEmpty(value) || value == "NA" || value == "None") return null;
        return double.TryParse(value, NumberStyles.Float, CultureInfo.InvariantCulture, out var n) && double.IsFinite(n) ? n : null;
    }

    private static string? CleanError(string stderr)
    {
        var last = stderr.Split('\n').Select(l => l.Trim()).LastOrDefault(l => l.StartsWith("ERROR:", StringComparison.Ordinal));
        if (last == null) return null;
        return Regex.Replace(last, @"^ERROR:\s*(\[[^\]]+\]\s*)?", "");
    }

    private static async Task<int> RunAsync(string file, IEnumerable<string> args, Action<string> onStdout, Action<string> onStderr, CancellationToken cancel)
    {
        var psi = new ProcessStartInfo(file)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        foreach (var a in args) psi.ArgumentList.Add(a);
        psi.Environment["PYTHONIOENCODING"] = "utf-8";
        psi.Environment["PYTHONUTF8"] = "1";

        using var process = new Process { StartInfo = psi, EnableRaisingEvents = true };
        process.OutputDataReceived += (_, e) => { if (e.Data != null) onStdout(e.Data); };
        process.ErrorDataReceived += (_, e) => { if (e.Data != null) onStderr(e.Data); };

        process.Start();
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();

        using var reg = cancel.Register(() =>
        {
            try { if (!process.HasExited) process.Kill(entireProcessTree: true); }
            catch { /* already gone */ }
        });

        await process.WaitForExitAsync(CancellationToken.None);
        process.WaitForExit(); // flush the async output readers
        return process.ExitCode;
    }
}
