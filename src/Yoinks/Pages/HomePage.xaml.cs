using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media.Imaging;
using Microsoft.UI.Xaml.Navigation;
using Windows.ApplicationModel.DataTransfer;
using Yoinks.Services;

namespace Yoinks.Pages;

public sealed partial class HomePage : Page
{
    public static HomePage? Current { get; private set; }
    public static string? PendingUrl { get; set; }

    private enum Phase { Input, Probing, Picking, Downloading, Done, Error }

    private Phase _phase = Phase.Input;
    private Phase _errorFrom = Phase.Input;
    private CancellationTokenSource? _cts;
    private string _url = "";
    private Platform? _platform;
    private VideoInfo? _info;
    private string? _infoJsonPath;
    private DownloadChoice? _choice;
    private string _lastFile = "";
    private string? _lastOfferedClipboard;
    private List<string> _recentUrls = new();

    public bool IsBusy => _phase is Phase.Probing or Phase.Downloading;

    public HomePage()
    {
        InitializeComponent();
        Current = this;
        ResponsiveLayout.Attach(Scroller, ContentPanel, OnWidth, maxWidth: 860);

        SiteChips.ItemsSource = new[] { "YouTube", "X", "Instagram", "Threads", "TikTok", "+1800 more" };

        History.Changed += () => DispatcherQueue.TryEnqueue(RefreshRecent);
        RefreshRecent();
        UpdateSaveTo();

        bool hooked = false;
        Loaded += async (_, _) =>
        {
            if (!hooked && App.Window != null)
            {
                hooked = true;
                App.Window.Activated += (_, e) =>
                {
                    if (e.WindowActivationState != WindowActivationState.Deactivated) _ = CheckClipboardAsync();
                };
            }
            await CheckToolsAsync();
            _ = CheckClipboardAsync();
        };
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        UpdateSaveTo();
        ConsumePendingUrl();
        if (_phase == Phase.Input) UrlBox.Focus(FocusState.Programmatic);
    }

    public void ConsumePendingUrl()
    {
        var url = PendingUrl;
        PendingUrl = null;
        if (string.IsNullOrEmpty(url)) return;
        if (IsBusy)
        {
            // don't interrupt a running yoink - leave the link ready in the box for later
            UrlBox.Text = url;
            return;
        }
        UrlBox.Text = url;
        Submit(url);
    }

    // ---------------- layout ----------------

    private void OnWidth(double width)
    {
        bool narrow = width < 660;
        Logo.CellSize = width < 420 ? 8 : 11;
        if (narrow)
        {
            PickRightCol.Width = new GridLength(0);
            PickLeftCol.Width = new GridLength(1, GridUnitType.Star);
            Grid.SetColumn(PickChoices, 0);
            Grid.SetRow(PickChoices, 1);
        }
        else
        {
            PickRightCol.Width = new GridLength(320);
            Grid.SetColumn(PickChoices, 1);
            Grid.SetRow(PickChoices, 0);
        }
    }

    private void SetPhase(Phase phase)
    {
        _phase = phase;
        InputPanel.Visibility = phase == Phase.Input ? Visibility.Visible : Visibility.Collapsed;
        ProbingPanel.Visibility = phase == Phase.Probing ? Visibility.Visible : Visibility.Collapsed;
        PickingPanel.Visibility = phase == Phase.Picking ? Visibility.Visible : Visibility.Collapsed;
        DownloadingPanel.Visibility = phase == Phase.Downloading ? Visibility.Visible : Visibility.Collapsed;
        DonePanel.Visibility = phase == Phase.Done ? Visibility.Visible : Visibility.Collapsed;
        ErrorPanel.Visibility = phase == Phase.Error ? Visibility.Visible : Visibility.Collapsed;

        var hwnd = App.Window?.Hwnd ?? IntPtr.Zero;
        if (hwnd != IntPtr.Zero)
        {
            if (phase == Phase.Probing) Taskbar.SetState(hwnd, Taskbar.State.Indeterminate);
            else if (phase == Phase.Error) { Taskbar.SetValue(hwnd, 1); Taskbar.SetState(hwnd, Taskbar.State.Error); }
            else if (phase != Phase.Downloading) Taskbar.SetState(hwnd, Taskbar.State.None);
        }
    }

    private void UpdateSaveTo()
    {
        SaveToText.Text = $"Saves to {AppSettings.Current.ResolvedDownloadFolder}";
    }

    private async Task CheckToolsAsync()
    {
        var status = await ToolLocator.CheckAsync();
        ToolsBar.IsOpen = !status.Ready;
    }

    // ---------------- clipboard ----------------

    private async Task CheckClipboardAsync()
    {
        if (!AppSettings.Current.WatchClipboard || _phase != Phase.Input) return;
        try
        {
            var content = Clipboard.GetContent();
            if (!content.Contains(StandardDataFormats.Text)) return;
            var text = (await content.GetTextAsync())?.Trim();
            if (!Platforms.IsProbablyUrl(text) || text == UrlBox.Text.Trim()) { ClipboardChip.Visibility = Visibility.Collapsed; return; }
            _lastOfferedClipboard = text;
            ClipboardText.Text = $"Link in your clipboard · {text}";
            ClipboardChip.Visibility = Visibility.Visible;
        }
        catch
        {
            // clipboard busy / denied, try again next time
        }
    }

    private void PasteClipboard_Click(object sender, RoutedEventArgs e)
    {
        if (_lastOfferedClipboard == null) return;
        UrlBox.Text = _lastOfferedClipboard;
        ClipboardChip.Visibility = Visibility.Collapsed;
        Submit(_lastOfferedClipboard);
    }

    // ---------------- input ----------------

    private void UrlBox_GotFocus(object sender, RoutedEventArgs e)
    {
        _recentUrls = History.RecentUrls();
    }

    private void UrlBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        WarningText.Visibility = Visibility.Collapsed;
        if (args.Reason != AutoSuggestionBoxTextChangeReason.UserInput) return;
        var q = sender.Text.Trim();
        sender.ItemsSource = q.Length == 0
            ? null
            : _recentUrls.Where(u => u.Contains(q, StringComparison.OrdinalIgnoreCase) && u != q).Take(6).ToList();
        if (q == _lastOfferedClipboard) ClipboardChip.Visibility = Visibility.Collapsed;
    }

    private void UrlBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    {
        Submit(args.ChosenSuggestion as string ?? sender.Text);
    }

    private void Yoink_Click(object sender, RoutedEventArgs e) => Submit(UrlBox.Text);

    private void Submit(string value)
    {
        var url = value.Trim();
        if (!Platforms.IsProbablyUrl(url))
        {
            WarningText.Text = url.Length == 0
                ? "Paste a link first."
                : "That doesn't look like a link. Paste the full address, starting with https://";
            WarningText.Visibility = Visibility.Visible;
            return;
        }
        _url = url;
        _ = StartProbeAsync(url);
    }

    // ---------------- probe ----------------

    private async Task StartProbeAsync(string url)
    {
        CancelRun(resetUi: false);
        var cts = new CancellationTokenSource();
        _cts = cts;

        _info = null;
        CleanupInfoJson();
        _platform = Platforms.Detect(url);
        ProbePlatform.Text = _platform.Label;
        ProbePlatformIcon.Glyph = _platform.Glyph;
        ProbeUrl.Text = url;
        ProbeStatus.Text = "warming up...";
        ClipboardChip.Visibility = Visibility.Collapsed;
        SetPhase(Phase.Probing);

        try
        {
            if (ToolLocator.FindYtDlp() == null)
                throw new YtDlpException("yt-dlp is missing. Open Settings and click Repair to download it again.");
            ProbeStatus.Text = "fetching video info...";
            var result = await YtDlp.ProbeAsync(url, cts.Token);
            if (cts.IsCancellationRequested) return;

            _info = result.Info;
            _infoJsonPath = result.InfoJsonPath;
            ShowPicker();
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            if (cts.IsCancellationRequested) return;
            ShowError(ex, Phase.Input);
        }
    }

    private void ShowPicker()
    {
        var info = _info!;
        TitleText.Text = info.Title;
        PickPlatform.Text = _platform?.Label ?? "";
        PickPlatformIcon.Glyph = _platform?.Glyph ?? "";
        UploaderText.Text = info.Uploader ?? "";
        var dur = info.Duration.HasValue ? Format.Duration(info.Duration.Value) : "";
        DurationText.Text = dur;
        DurationBadge.Visibility = dur.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
        Thumb.Source = MakeImage(info.Thumbnail);
        ChoiceList.ItemsSource = YtDlp.BuildChoices(info);
        SetPhase(Phase.Picking);
    }

    private static BitmapImage? MakeImage(string? url)
    {
        if (string.IsNullOrEmpty(url)) return null;
        try { return new BitmapImage(new Uri(url)); } catch { return null; }
    }

    // ---------------- download ----------------

    private void ChoiceList_ItemClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is DownloadChoice choice)
            _ = StartDownloadAsync(choice);
    }

    private async Task StartDownloadAsync(DownloadChoice choice)
    {
        var cts = new CancellationTokenSource();
        _cts = cts;
        _choice = choice;

        DlTitle.Text = _info?.Title ?? _url;
        DlChoice.Text = $"{choice.Label}  ·  {choice.Detail}";
        DlThumb.Source = MakeImage(_info?.Thumbnail);
        DlPercent.Text = "0%";
        DlPart.Text = "";
        DlBar.IsIndeterminate = false;
        DlBar.Value = 0;
        DlSpinner.IsActive = true;
        DlMeta.Text = "starting download...";
        SetPhase(Phase.Downloading);
        var hwnd = App.Window?.Hwnd ?? IntPtr.Zero;
        Taskbar.SetState(hwnd, Taskbar.State.Indeterminate);

        var outDir = AppSettings.Current.ResolvedDownloadFolder;

        void OnProgress(DownloadProgress p) => DispatcherQueue.TryEnqueue(() =>
        {
            if (_cts != cts || _phase != Phase.Downloading) return;
            DlPart.Text = p.TotalParts > 1 ? $"part {p.Part + 1} of {p.TotalParts}" : "";
            if (p.TotalBytes is > 0)
            {
                double fraction = Math.Clamp(p.DownloadedBytes / p.TotalBytes.Value, 0, 1);
                // merges download video then audio; spread both parts over one bar
                double overall = (p.Part + fraction) / Math.Max(1, p.TotalParts);
                DlBar.IsIndeterminate = false;
                DlBar.Value = overall * 100;
                DlPercent.Text = $"{Math.Floor(overall * 100)}%";
                DlSpinner.IsActive = false;
                var meta = new List<string> { $"{Format.Bytes(p.DownloadedBytes)} of {Format.Bytes(p.TotalBytes.Value)}" };
                if (p.Speed is > 0) meta.Add(Format.Speed(p.Speed.Value));
                if (p.Eta is > 0) meta.Add($"{Format.Duration(p.Eta.Value)} left");
                DlMeta.Text = string.Join("  ·  ", meta);
                Taskbar.SetValue(hwnd, overall);
            }
            else
            {
                DlBar.IsIndeterminate = true;
                DlSpinner.IsActive = true;
                DlPercent.Text = Format.Bytes(p.DownloadedBytes);
                DlMeta.Text = "downloading..." + (p.Speed is > 0 ? $"  ·  {Format.Speed(p.Speed.Value)}" : "");
            }
        });

        void OnProcessing() => DispatcherQueue.TryEnqueue(() =>
        {
            if (_cts != cts || _phase != Phase.Downloading) return;
            DlBar.IsIndeterminate = true;
            DlSpinner.IsActive = true;
            DlPercent.Text = "100%";
            DlMeta.Text = choice.IsAudio ? "converting to mp3..." : "merging video and audio...";
            Taskbar.SetState(hwnd, Taskbar.State.Indeterminate);
        });

        try
        {
            string file;
            try
            {
                // reuse the probe's metadata - starts right away instead of re-extracting
                file = await YtDlp.DownloadAsync(_url, _infoJsonPath, choice, outDir, OnProgress, OnProcessing, cts.Token);
            }
            catch (YtDlpException) when (!cts.IsCancellationRequested)
            {
                // media links in the cached info can expire: retry with a fresh extraction
                DlBar.IsIndeterminate = true;
                DlMeta.Text = "link expired, grabbing a fresh one...";
                file = await YtDlp.DownloadAsync(_url, null, choice, outDir, OnProgress, OnProcessing, cts.Token);
            }
            if (cts.IsCancellationRequested) return;

            _lastFile = file;
            History.Add(new HistoryEntry
            {
                Url = _url,
                Title = _info?.Title ?? Path.GetFileNameWithoutExtension(file),
                Uploader = _info?.Uploader,
                Platform = _platform?.Label ?? "",
                Thumbnail = _info?.Thumbnail,
                FilePath = file,
                Choice = choice.IsAudio ? "MP3" : $"{choice.Label} MP4",
                IsAudio = choice.IsAudio,
                When = DateTime.Now,
            });
            App.Window?.SetDownloadsBadge(1);

            DoneFile.Text = Path.GetFileName(file);
            DoneFolder.Text = Path.GetDirectoryName(file) ?? "";
            SetPhase(Phase.Done);
            UrlBox.Text = "";
            CleanupInfoJson();
            FlashWindow();

            if (AppSettings.Current.OpenFolderWhenDone) ShowInExplorer(file);
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            if (cts.IsCancellationRequested) return;
            ShowError(ex, Phase.Picking);
        }
    }

    // ---------------- cancel / reset ----------------

    public void CancelRun() => CancelRun(resetUi: true);

    private void CancelRun(bool resetUi)
    {
        _cts?.Cancel();
        _cts = null;
        if (!resetUi) return;
        UrlBox.Text = _url; // keep the link around so a cancel isn't destructive
        SetPhase(Phase.Input);
    }

    private void ResetToInput()
    {
        CleanupInfoJson();
        _info = null;
        ChoiceList.ItemsSource = null;
        SetPhase(Phase.Input);
        UrlBox.Focus(FocusState.Programmatic);
        _ = CheckClipboardAsync();
    }

    private void CleanupInfoJson()
    {
        if (_infoJsonPath == null) return;
        try { File.Delete(_infoJsonPath); } catch { }
        _infoJsonPath = null;
    }

    private void ShowError(Exception ex, Phase from)
    {
        _errorFrom = _info != null ? from : Phase.Input;
        var msg = ex.Message;
        if (ex is System.ComponentModel.Win32Exception) msg = "Could not start yt-dlp. Open Settings and click Repair.";
        ErrorText.Text = msg;
        SetPhase(Phase.Error);
        CrashLog.Write(ex);
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => CancelRun();

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        if (_phase == Phase.Error && _errorFrom == Phase.Picking && _info != null) { SetPhase(Phase.Picking); return; }
        UrlBox.Text = _url;
        ResetToInput();
    }

    private void Retry_Click(object sender, RoutedEventArgs e)
    {
        if (_errorFrom == Phase.Picking && _choice != null && _info != null)
            _ = StartDownloadAsync(_choice);
        else
            _ = StartProbeAsync(_url);
    }

    private void Another_Click(object sender, RoutedEventArgs e)
    {
        _url = "";
        UrlBox.Text = "";
        ResetToInput();
    }

    private void Logo_Click(object sender, RoutedEventArgs e)
    {
        if (IsBusy) CancelRun();
        else if (_phase != Phase.Input) ResetToInput();
    }

    private void Escape_Invoked(KeyboardAccelerator sender, KeyboardAcceleratorInvokedEventArgs args)
    {
        if (IsBusy) CancelRun();
        else if (_phase is Phase.Picking or Phase.Error or Phase.Done) ResetToInput();
        else return;
        args.Handled = true;
    }

    // ---------------- files ----------------

    private void OpenFile_Click(object sender, RoutedEventArgs e) => OpenFile(_lastFile);

    private void ShowInFolder_Click(object sender, RoutedEventArgs e) => ShowInExplorer(_lastFile);

    private void RecentOpen_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: string path }) OpenFile(path);
    }

    private void SeeAll_Click(object sender, RoutedEventArgs e) => App.Window?.GoTo("downloads");

    private void OpenSettings_Click(object sender, RoutedEventArgs e) => App.Window?.GoTo("settings");

    public static void OpenFile(string path)
    {
        try
        {
            if (File.Exists(path))
                Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch { }
    }

    public static void ShowInExplorer(string path)
    {
        try
        {
            if (File.Exists(path))
                Process.Start("explorer.exe", $"/select,\"{path}\"");
            else if (Directory.Exists(Path.GetDirectoryName(path)))
                Process.Start("explorer.exe", $"\"{Path.GetDirectoryName(path)}\"");
        }
        catch { }
    }

    private void RefreshRecent()
    {
        var items = History.Load().Take(3).ToList();
        RecentList.ItemsSource = items;
        var vis = items.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        RecentHeader.Visibility = vis;
        RecentList.Visibility = vis;
    }

    private static void FlashWindow()
    {
        var hwnd = App.Window?.Hwnd ?? IntPtr.Zero;
        if (hwnd == IntPtr.Zero || GetForegroundWindow() == hwnd) return;
        var info = new FLASHWINFO
        {
            cbSize = (uint)System.Runtime.InteropServices.Marshal.SizeOf<FLASHWINFO>(),
            hwnd = hwnd,
            dwFlags = 0x3 | 0xC, // FLASHW_ALL | FLASHW_TIMERNOFG
            uCount = 3,
        };
        FlashWindowEx(ref info);
    }

    [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Sequential)]
    private struct FLASHWINFO
    {
        public uint cbSize;
        public IntPtr hwnd;
        public uint dwFlags;
        public uint uCount;
        public uint dwTimeout;
    }

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool FlashWindowEx(ref FLASHWINFO info);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();
}
