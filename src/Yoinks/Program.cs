using System;
using System.Linq;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppLifecycle;
using Yoinks.Services;

namespace Yoinks;

// Custom entry point so Yoinks stays single instance: a link sent from the
// browser (yoinks: protocol) or the command line lands in the open window.
public static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        AppDomain.CurrentDomain.UnhandledException += (_, e) => CrashLog.Write(e.ExceptionObject as Exception);
        WinRT.ComWrappersSupport.InitializeComWrappers();

        if (RedirectToRunningInstance())
            return 0;

        App.StartupUrl = UrlFromArgs(args);
        Application.Start(p =>
        {
            var context = new DispatcherQueueSynchronizationContext(DispatcherQueue.GetForCurrentThread());
            SynchronizationContext.SetSynchronizationContext(context);
            _ = new App();
        });
        return 0;
    }

    private static bool RedirectToRunningInstance()
    {
        AppInstance key;
        try
        {
            key = AppInstance.FindOrRegisterForKey("Yoinks.Tamim.Main");
        }
        catch
        {
            return false;
        }

        if (key.IsCurrent)
        {
            key.Activated += OnActivated;
            return false;
        }

        var activation = AppInstance.GetCurrent().GetActivatedEventArgs();
        IntPtr done = CreateEvent(IntPtr.Zero, true, false, null);
        Task.Run(() =>
        {
            try { key.RedirectActivationToAsync(activation).AsTask().Wait(); }
            catch { /* the main window just won't get the link */ }
            SetEvent(done);
        });
        _ = CoWaitForMultipleObjects(0, 0xFFFFFFFF, 1, new[] { done }, out _);
        return true;
    }

    private static void OnActivated(object? sender, AppActivationArguments e)
    {
        string? url = null;
        if (e.Kind == ExtendedActivationKind.Launch &&
            e.Data is Windows.ApplicationModel.Activation.ILaunchActivatedEventArgs launch)
        {
            url = UrlFromArgs(SplitCommandLine(launch.Arguments));
        }
        App.Current?.OnUrlFromOtherInstance(url);
    }

    // Accepts "https://..." or "yoinks:https%3A%2F%2F..." / "yoinks://https://..."
    public static string? UrlFromArgs(string[] args)
    {
        foreach (var raw in args.Select(a => a.Trim().Trim('"')))
        {
            var candidate = raw;
            if (candidate.StartsWith("yoinks:", StringComparison.OrdinalIgnoreCase))
            {
                candidate = candidate["yoinks:".Length..].TrimStart('/');
                try { candidate = Uri.UnescapeDataString(candidate); } catch { }
                // "yoinks://https//x.com/..." - some browsers eat the colon after the scheme
                if (candidate.StartsWith("https//", StringComparison.OrdinalIgnoreCase)) candidate = "https://" + candidate[7..];
                if (candidate.StartsWith("http//", StringComparison.OrdinalIgnoreCase)) candidate = "http://" + candidate[6..];
            }
            if (Platforms.IsProbablyUrl(candidate))
                return candidate;
        }
        return null;
    }

    private static string[] SplitCommandLine(string commandLine)
    {
        if (string.IsNullOrWhiteSpace(commandLine))
            return Array.Empty<string>();
        IntPtr argv = CommandLineToArgvW(commandLine, out int count);
        if (argv == IntPtr.Zero)
            return Array.Empty<string>();
        try
        {
            var result = new string[count];
            for (int i = 0; i < count; i++)
                result[i] = Marshal.PtrToStringUni(Marshal.ReadIntPtr(argv, i * IntPtr.Size)) ?? "";
            return result;
        }
        finally
        {
            LocalFree(argv);
        }
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr CreateEvent(IntPtr attributes, bool manualReset, bool initialState, string? name);

    [DllImport("kernel32.dll")]
    private static extern bool SetEvent(IntPtr handle);

    [DllImport("ole32.dll")]
    private static extern uint CoWaitForMultipleObjects(uint flags, uint timeout, ulong count, IntPtr[] handles, out uint index);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr CommandLineToArgvW(string commandLine, out int count);

    [DllImport("kernel32.dll")]
    private static extern IntPtr LocalFree(IntPtr mem);
}
