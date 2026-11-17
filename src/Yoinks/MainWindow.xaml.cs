using System;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using Microsoft.UI;
using Microsoft.UI.Composition.SystemBackdrops;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Windows.Graphics;
using Yoinks.Pages;
using Yoinks.Services;

namespace Yoinks;

public sealed partial class MainWindow : Window
{
    public IntPtr Hwnd { get; }
    private bool _closeConfirmed;

    public MainWindow()
    {
        InitializeComponent();
        Hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);

        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "Assets", "Yoinks.ico"));
        AppWindow.Title = "Yoinks";

        var v = typeof(MainWindow).Assembly.GetName().Version;
        if (v != null) VersionBadge.Text = $"v{v.Major}.{v.Minor}";

        ApplyBackdrop();
        SizeAndCenter(1100, 760);
        if (AppWindow.Presenter is OverlappedPresenter presenter)
        {
            double scale = GetDpiForWindow(Hwnd) / 96.0;
            presenter.PreferredMinimumWidth = (int)(500 * scale);
            presenter.PreferredMinimumHeight = (int)(560 * scale);
        }

        ApplyTheme(AppSettings.Current.Theme);
        RootGrid.ActualThemeChanged += (_, _) => UpdateCaptionButtons();
        RootGrid.Loaded += (_, _) => StartAmbientMotion();
        RootGrid.SizeChanged += (_, e) => LayoutBlobs(e.NewSize.Width, e.NewSize.Height);

        AppWindow.Closing += AppWindow_Closing;

        Nav.SelectedItem = Nav.MenuItems[0];
    }

    private void ApplyBackdrop()
    {
        // Mica on Windows 11, acrylic on Windows 10
        if (MicaController.IsSupported())
            SystemBackdrop = new MicaBackdrop { Kind = MicaKind.Base };
        else if (DesktopAcrylicController.IsSupported())
            SystemBackdrop = new DesktopAcrylicBackdrop();
        else
            RootGrid.Background = (Brush)Application.Current.Resources["SolidBackgroundFillColorBaseBrush"];
    }

    private void SizeAndCenter(int width, int height)
    {
        double scale = GetDpiForWindow(Hwnd) / 96.0;
        int w = (int)(width * scale), h = (int)(height * scale);
        var area = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary).WorkArea;
        w = Math.Min(w, area.Width);
        h = Math.Min(h, area.Height);
        AppWindow.MoveAndResize(new RectInt32(area.X + (area.Width - w) / 2, area.Y + (area.Height - h) / 2, w, h));
    }

    // ---------- ambient glow: four soft colour fields drifting slowly ----------

    private void LayoutBlobs(double w, double h)
    {
        Canvas.SetLeft(BlobA, -w * 0.18); Canvas.SetTop(BlobA, -h * 0.30);
        Canvas.SetLeft(BlobB, w * 0.50); Canvas.SetTop(BlobB, -h * 0.35);
        Canvas.SetLeft(BlobC, w * 0.30); Canvas.SetTop(BlobC, h * 0.45);
        Canvas.SetLeft(BlobD, -w * 0.12); Canvas.SetTop(BlobD, h * 0.55);
    }

    private void StartAmbientMotion()
    {
        var sb = new Storyboard();
        void Drift(CompositeTransform t, double dx, double dy, double seconds)
        {
            foreach (var (prop, to) in new[] { ("TranslateX", dx), ("TranslateY", dy) })
            {
                var a = new DoubleAnimation
                {
                    From = 0,
                    To = to,
                    Duration = TimeSpan.FromSeconds(seconds),
                    AutoReverse = true,
                    RepeatBehavior = RepeatBehavior.Forever,
                    EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
                };
                Storyboard.SetTarget(a, t);
                Storyboard.SetTargetProperty(a, prop);
                sb.Children.Add(a);
            }
        }
        Drift(BlobATransform, 120, 80, 19);
        Drift(BlobBTransform, -140, 110, 23);
        Drift(BlobCTransform, -90, -120, 27);
        Drift(BlobDTransform, 130, -70, 21);
        sb.Begin();
    }

    // ---------- theme ----------

    public void ApplyTheme(string theme)
    {
        RootGrid.RequestedTheme = theme switch
        {
            "Light" => ElementTheme.Light,
            "Dark" => ElementTheme.Dark,
            _ => ElementTheme.Default,
        };
        UpdateCaptionButtons();
    }

    private void UpdateCaptionButtons()
    {
        var tb = AppWindow.TitleBar;
        bool dark = RootGrid.ActualTheme == ElementTheme.Dark;
        tb.ButtonBackgroundColor = Colors.Transparent;
        tb.ButtonInactiveBackgroundColor = Colors.Transparent;
        tb.ButtonForegroundColor = dark ? Colors.White : Colors.Black;
        tb.ButtonHoverForegroundColor = dark ? Colors.White : Colors.Black;
        tb.ButtonHoverBackgroundColor = dark ? Windows.UI.Color.FromArgb(0x18, 0xFF, 0xFF, 0xFF) : Windows.UI.Color.FromArgb(0x10, 0, 0, 0);
        tb.ButtonPressedBackgroundColor = dark ? Windows.UI.Color.FromArgb(0x28, 0xFF, 0xFF, 0xFF) : Windows.UI.Color.FromArgb(0x20, 0, 0, 0);
        tb.ButtonInactiveForegroundColor = dark ? Windows.UI.Color.FromArgb(0x80, 0xFF, 0xFF, 0xFF) : Windows.UI.Color.FromArgb(0x80, 0, 0, 0);
    }

    // ---------- navigation ----------

    private void Nav_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        Type page = args.IsSettingsSelected
            ? typeof(SettingsPage)
            : (args.SelectedItemContainer?.Tag as string) switch
            {
                "downloads" => typeof(DownloadsPage),
                "about" => typeof(AboutPage),
                _ => typeof(HomePage),
            };
        if (page == typeof(DownloadsPage)) SetDownloadsBadge(0);
        if (ContentFrame.CurrentSourcePageType != page)
            ContentFrame.Navigate(page, null, new EntranceNavigationTransitionInfo());
    }

    // On narrow windows the menu collapses to a floating hamburger button; push the page below it.
    private void Nav_DisplayModeChanged(NavigationView sender, NavigationViewDisplayModeChangedEventArgs args)
    {
        ContentFrame.Margin = args.DisplayMode == NavigationViewDisplayMode.Minimal
            ? new Thickness(0, 40, 0, 0)
            : new Thickness(0);
    }

    public void GoTo(string tag)
    {
        if (tag == "settings")
        {
            Nav.SelectedItem = Nav.SettingsItem;
            return;
        }
        var item = Nav.MenuItems.Concat(Nav.FooterMenuItems)
            .OfType<NavigationViewItem>()
            .FirstOrDefault(i => (i.Tag as string) == tag);
        if (item != null) Nav.SelectedItem = item;
    }

    private int _unseen;

    public void SetDownloadsBadge(int add)
    {
        _unseen = add == 0 ? 0 : _unseen + add;
        DownloadsBadge.Value = _unseen;
        DownloadsBadge.Visibility = _unseen > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    // Links from the command line, the browser (yoinks:) or another instance.
    public void OpenUrl(string url)
    {
        HomePage.PendingUrl = url;
        if (ContentFrame.Content is HomePage hp)
            hp.ConsumePendingUrl();
        else
            GoTo("home");
    }

    public void BringToFront()
    {
        if (AppWindow.Presenter is OverlappedPresenter p && p.State == OverlappedPresenterState.Minimized)
            p.Restore();
        Activate();
        SetForegroundWindow(Hwnd);
    }

    // Don't leave yt-dlp running in the background when the window closes mid-download.
    private async void AppWindow_Closing(AppWindow sender, AppWindowClosingEventArgs args)
    {
        if (_closeConfirmed || HomePage.Current?.IsBusy != true) return;
        args.Cancel = true;
        var dialog = new ContentDialog
        {
            XamlRoot = RootGrid.XamlRoot,
            Title = "Stop the download?",
            Content = "A video is still downloading. Closing Yoinks will cancel it.",
            PrimaryButtonText = "Stop and close",
            CloseButtonText = "Keep downloading",
            DefaultButton = ContentDialogButton.Close,
        };
        if (await dialog.ShowAsync() == ContentDialogResult.Primary)
        {
            HomePage.Current?.CancelRun();
            _closeConfirmed = true;
            Close();
        }
    }

    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hwnd);
}
