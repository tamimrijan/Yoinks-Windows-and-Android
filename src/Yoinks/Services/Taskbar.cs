using System;
using System.Runtime.InteropServices;

namespace Yoinks.Services;

// Download progress on the taskbar button, like Explorer copies and browsers do.
public static class Taskbar
{
    public enum State { None = 0, Indeterminate = 1, Normal = 2, Error = 4, Paused = 8 }

    private static ITaskbarList3? _list;

    private static ITaskbarList3? List
    {
        get
        {
            if (_list != null) return _list;
            try
            {
                _list = (ITaskbarList3)new TaskbarInstance();
                _list.HrInit();
            }
            catch
            {
                _list = null;
            }
            return _list;
        }
    }

    public static void SetState(IntPtr hwnd, State state)
    {
        try { List?.SetProgressState(hwnd, (int)state); } catch { }
    }

    public static void SetValue(IntPtr hwnd, double fraction)
    {
        try
        {
            List?.SetProgressState(hwnd, (int)State.Normal);
            List?.SetProgressValue(hwnd, (ulong)(Math.Clamp(fraction, 0, 1) * 1000), 1000);
        }
        catch { }
    }

    [ComImport, Guid("56FDF344-FD6D-11d0-958A-006097C9A090"), ClassInterface(ClassInterfaceType.None)]
    private class TaskbarInstance { }

    [ComImport, Guid("ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ITaskbarList3
    {
        void HrInit();
        void AddTab(IntPtr hwnd);
        void DeleteTab(IntPtr hwnd);
        void ActivateTab(IntPtr hwnd);
        void SetActiveAlt(IntPtr hwnd);
        void MarkFullscreenWindow(IntPtr hwnd, [MarshalAs(UnmanagedType.Bool)] bool fullscreen);
        void SetProgressValue(IntPtr hwnd, ulong completed, ulong total);
        void SetProgressState(IntPtr hwnd, int flags);
    }
}
