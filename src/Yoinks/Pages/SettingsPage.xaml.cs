using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.Win32;
using Windows.ApplicationModel.DataTransfer;
using Windows.Storage.Pickers;
using Yoinks.Services;

namespace Yoinks.Pages;

public sealed partial class SettingsPage : Page
{
    private const string Bookmarklet = "javascript:location.href='yoinks:'+encodeURIComponent(location.href)";
    private readonly bool _loading = true;
    private CancellationTokenSource? _cts;

    public SettingsPage()
    {
        InitializeComponent();
        ResponsiveLayout.Attach(Scroller, ContentPanel);
        var s = AppSettings.Current;
        ThemeBox.SelectedItem = ThemeBox.Items.OfType<ComboBoxItem>().FirstOrDefault(i => (string)i.Tag == s.Theme) ?? ThemeBox.Items[0];
        OpenFolderSwitch.IsOn = s.OpenFolderWhenDone;
        ClipboardSwitch.IsOn = s.WatchClipboard;
        HistorySwitch.IsOn = s.SaveHistory;
        DataText.Text = AppSettings.Folder;
        ProtocolWarning.Visibility = ProtocolRegistered() ? Visibility.Collapsed : Visibility.Visible;
        UpdateFolderText();
        _loading = false;

        Loaded += async (_, _) => await RefreshToolsAsync();
        Unloaded += (_, _) => _cts?.Cancel();
    }

    private void Theme_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (_loading || ThemeBox.SelectedItem is not ComboBoxItem item) return;
        AppSettings.Current.Theme = (string)item.Tag;
        AppSettings.Current.Save();
        App.Window?.ApplyTheme(AppSettings.Current.Theme);
    }

    private void Toggle_Changed(object sender, RoutedEventArgs e)
    {
        if (_loading) return;
        var s = AppSettings.Current;
        s.OpenFolderWhenDone = OpenFolderSwitch.IsOn;
        s.WatchClipboard = ClipboardSwitch.IsOn;
        s.SaveHistory = HistorySwitch.IsOn;
        s.Save();
    }

    // ---------- download folder ----------

    private void UpdateFolderText()
    {
        FolderText.Text = AppSettings.Current.ResolvedDownloadFolder;
        ResetFolderButton.Visibility = string.IsNullOrEmpty(AppSettings.Current.DownloadFolder) ? Visibility.Collapsed : Visibility.Visible;
    }

    private async void ChangeFolder_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.Downloads };
        picker.FileTypeFilter.Add("*");
        WinRT.Interop.InitializeWithWindow.Initialize(picker, App.Window!.Hwnd);
        var folder = await picker.PickSingleFolderAsync();
        if (folder == null) return;
        AppSettings.Current.DownloadFolder = folder.Path;
        AppSettings.Current.Save();
        UpdateFolderText();
    }

    private void ResetFolder_Click(object sender, RoutedEventArgs e)
    {
        AppSettings.Current.DownloadFolder = "";
        AppSettings.Current.Save();
        UpdateFolderText();
    }

    // ---------- components ----------

    private async Task RefreshToolsAsync()
    {
        var status = await ToolLocator.CheckAsync();
        if (status.Ready)
        {
            YtDlpText.Text = $"Version {status.YtDlpVersion}  ·  {Where(status.YtDlpPath)}";
            YtDlpButton.Content = "Update";
        }
        else
        {
            YtDlpText.Text = "Not found. Yoinks can't download anything without it.";
            YtDlpButton.Content = "Repair";
        }

        if (status.HasJsRuntime)
        {
            DenoText.Text = status.JsRuntime == "deno"
                ? $"Installed  ·  {Where(status.JsRuntimePath)}. Needed for full YouTube quality."
                : $"Using Node.js from PATH. Install Deno if YouTube downloads fail.";
            DenoButton.Content = status.JsRuntime == "deno" ? "Reinstall" : "Install";
        }
        else
        {
            DenoText.Text = "Not installed. YouTube may only offer low resolutions without it (about 45 MB download).";
            DenoButton.Content = "Install";
        }

        FfmpegText.Text = status.HasFfmpeg
            ? $"Ready  ·  {Where(status.FfmpegPath)}"
            : "Not found. Single-file videos still work, but HD merges and MP3 need it. Reinstall Yoinks to restore it.";
    }

    private static string Where(string? path)
    {
        if (path == null) return "";
        if (path.StartsWith(ToolLocator.UserBinDir, StringComparison.OrdinalIgnoreCase)) return "updated copy";
        if (path.StartsWith(ToolLocator.BundledDir, StringComparison.OrdinalIgnoreCase)) return "bundled";
        return "from PATH";
    }

    private async void UpdateYtDlp_Click(object sender, RoutedEventArgs e) =>
        await RunComponentAsync(YtDlpButton, YtDlpProgress, YtDlpText, "Downloading the latest yt-dlp...",
            (p, c) => ToolLocator.UpdateYtDlpAsync(p, c));

    private async void InstallDeno_Click(object sender, RoutedEventArgs e) =>
        await RunComponentAsync(DenoButton, DenoProgress, DenoText, "Downloading Deno...",
            (p, c) => ToolLocator.InstallDenoAsync(p, c));

    private async Task RunComponentAsync(Button button, ProgressBar bar, TextBlock text, string busy, Func<IProgress<double>, CancellationToken, Task> work)
    {
        if (HomePage.Current?.IsBusy == true)
        {
            text.Text = "Wait for the current download to finish first.";
            return;
        }
        _cts = new CancellationTokenSource();
        button.IsEnabled = false;
        bar.Visibility = Visibility.Visible;
        bar.Value = 0;
        text.Text = busy;
        try
        {
            await work(new Progress<double>(v => bar.Value = v), _cts.Token);
            await RefreshToolsAsync();
        }
        catch (Exception ex)
        {
            text.Text = $"Failed: {ex.Message}";
        }
        finally
        {
            button.IsEnabled = true;
            bar.Visibility = Visibility.Collapsed;
        }
    }

    // ---------- browser ----------

    private static bool ProtocolRegistered()
    {
        try
        {
            using var k = Registry.ClassesRoot.OpenSubKey(@"yoinks\shell\open\command");
            return k?.GetValue("") is string cmd && cmd.Length > 0;
        }
        catch
        {
            return false;
        }
    }

    private void CopyBookmark_Click(object sender, RoutedEventArgs e)
    {
        var dp = new DataPackage();
        dp.SetText(Bookmarklet);
        Clipboard.SetContent(dp);
        CopyBookmarkButton.Content = "Copied!";
    }

    private void OpenData_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            Directory.CreateDirectory(AppSettings.Folder);
            Process.Start("explorer.exe", $"\"{AppSettings.Folder}\"");
        }
        catch { }
    }
}
