using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using Windows.ApplicationModel.DataTransfer;
using Yoinks.Services;

namespace Yoinks.Pages;

public sealed class DownloadItem
{
    public DownloadItem(HistoryEntry entry)
    {
        Entry = entry;
        Exists = File.Exists(entry.FilePath);
    }

    public HistoryEntry Entry { get; }
    public bool Exists { get; }
    public Visibility MissingVisibility => Exists ? Visibility.Collapsed : Visibility.Visible;
    public string Glyph => Entry.IsAudio ? "" : "";
    public string Meta =>
        string.Join("  ·  ", new[] { Entry.Platform, Entry.Choice, Entry.Uploader, Format.Ago(Entry.When) }.Where(s => !string.IsNullOrWhiteSpace(s)));
}

public sealed partial class DownloadsPage : Page
{
    public DownloadsPage()
    {
        InitializeComponent();
        ResponsiveLayout.Attach(Scroller, ContentPanel);
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        Refresh();
    }

    private void Refresh()
    {
        var all = History.Load();
        var q = Search.Text.Trim();
        var shown = all.Where(h => q.Length == 0
                                   || h.Title.Contains(q, StringComparison.OrdinalIgnoreCase)
                                   || (h.Uploader?.Contains(q, StringComparison.OrdinalIgnoreCase) ?? false)
                                   || h.Platform.Contains(q, StringComparison.OrdinalIgnoreCase))
                       .Select(h => new DownloadItem(h)).ToList();
        List.ItemsSource = shown;
        ClearButton.IsEnabled = all.Count > 0;
        EmptyState.Visibility = shown.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        if (all.Count > 0 && shown.Count == 0)
        {
            EmptyTitle.Text = "No matches";
            EmptyBody.Text = $"Nothing in your downloads matches \"{q}\".";
        }
        else
        {
            EmptyTitle.Text = "Nothing yoinked yet";
            EmptyBody.Text = AppSettings.Current.SaveHistory
                ? "Videos you download show up here."
                : "Download history is turned off in Settings.";
        }
    }

    private void Search_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args) => Refresh();

    private static DownloadItem? ItemOf(object sender) => (sender as FrameworkElement)?.Tag as DownloadItem;

    private void Play_Click(object sender, RoutedEventArgs e)
    {
        if (ItemOf(sender) is { } i) HomePage.OpenFile(i.Entry.FilePath);
    }

    private void Show_Click(object sender, RoutedEventArgs e)
    {
        if (ItemOf(sender) is { } i) HomePage.ShowInExplorer(i.Entry.FilePath);
    }

    private void Again_Click(object sender, RoutedEventArgs e)
    {
        if (ItemOf(sender) is { } i) App.Window?.OpenUrl(i.Entry.Url);
    }

    private void CopyLink_Click(object sender, RoutedEventArgs e)
    {
        if (ItemOf(sender) is not { } i) return;
        var dp = new DataPackage();
        dp.SetText(i.Entry.Url);
        Clipboard.SetContent(dp);
    }

    private async void OpenPage_Click(object sender, RoutedEventArgs e)
    {
        if (ItemOf(sender) is { } i && Uri.TryCreate(i.Entry.Url, UriKind.Absolute, out var uri))
            await Windows.System.Launcher.LaunchUriAsync(uri);
    }

    private void Remove_Click(object sender, RoutedEventArgs e)
    {
        if (ItemOf(sender) is not { } i) return;
        History.Remove(i.Entry);
        Refresh();
    }

    private async void Clear_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new ContentDialog
        {
            XamlRoot = XamlRoot,
            Title = "Clear the download list?",
            Content = "This only clears the list. The video and audio files you downloaded stay where they are.",
            PrimaryButtonText = "Clear list",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Close,
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        History.Clear();
        Refresh();
    }

    private void OpenFolder_Click(object sender, RoutedEventArgs e)
    {
        var dir = AppSettings.Current.ResolvedDownloadFolder;
        try
        {
            Directory.CreateDirectory(dir);
            Process.Start("explorer.exe", $"\"{dir}\"");
        }
        catch { }
    }

    private void GoHome_Click(object sender, RoutedEventArgs e) => App.Window?.GoTo("home");
}
