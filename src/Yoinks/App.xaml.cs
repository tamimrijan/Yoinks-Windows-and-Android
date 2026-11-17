using Microsoft.UI.Xaml;

namespace Yoinks;

public partial class App : Application
{
    public static new App? Current { get; private set; }
    public static string? StartupUrl { get; set; }
    public static MainWindow? Window { get; private set; }

    public App()
    {
        Current = this;
        InitializeComponent();
        UnhandledException += (_, e) =>
        {
            Services.CrashLog.Write(e.Exception);
            e.Handled = true;
        };
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        Window = new MainWindow();
        Window.Activate();
        if (!string.IsNullOrEmpty(StartupUrl))
            Window.OpenUrl(StartupUrl);
    }

    // Called on a background thread when a second Yoinks.exe forwards its link here.
    public void OnUrlFromOtherInstance(string? url)
    {
        Window?.DispatcherQueue.TryEnqueue(() =>
        {
            Window.BringToFront();
            if (!string.IsNullOrEmpty(url))
                Window.OpenUrl(url);
        });
    }
}
