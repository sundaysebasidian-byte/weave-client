using System.Runtime.InteropServices;

namespace Weave.Windows;

/// <summary>Enforces a minimum client layout size (in DIPs, scaled by the window's DPI) through WM_GETMINMAXINFO.</summary>
internal sealed class WindowMinSize : IDisposable
{
    private const uint GetMinMaxInfo = 0x0024;
    private readonly IntPtr _window;
    private readonly int _widthDip, _heightDip;
    private readonly SubclassProc _procedure;
    private bool _installed;

    public WindowMinSize(IntPtr window, int widthDip, int heightDip)
    {
        _window = window; _widthDip = widthDip; _heightDip = heightDip;
        _procedure = HandleMessage;
        _installed = SetWindowSubclass(window, _procedure, new UIntPtr(91), IntPtr.Zero);
    }

    private IntPtr HandleMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr reference)
    {
        if (message == GetMinMaxInfo && lParam != IntPtr.Zero)
        {
            try
            {
                var scale = Math.Max(96, GetDpiForWindow(window)) / 96.0;
                var info = Marshal.PtrToStructure<MinMaxInfo>(lParam);
                info.MinTrackSize = new Point { X = (int)Math.Ceiling(_widthDip * scale), Y = (int)Math.Ceiling(_heightDip * scale) };
                Marshal.StructureToPtr(info, lParam, false);
            }
            catch (Exception) { /* Never unwind a managed exception through the native window procedure. */ }
        }
        return DefSubclassProc(window, message, wParam, lParam);
    }

    public void Dispose()
    {
        if (!_installed) return;
        _installed = false;
        RemoveWindowSubclass(_window, _procedure, new UIntPtr(91));
    }

    [StructLayout(LayoutKind.Sequential)] private struct Point { public int X, Y; }
    [StructLayout(LayoutKind.Sequential)] private struct MinMaxInfo { public Point Reserved, MaxSize, MaxPosition, MinTrackSize, MaxTrackSize; }
    private delegate IntPtr SubclassProc(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr reference);
    [DllImport("comctl32.dll")] private static extern bool SetWindowSubclass(IntPtr hwnd, SubclassProc procedure, UIntPtr id, IntPtr reference);
    [DllImport("comctl32.dll")] private static extern bool RemoveWindowSubclass(IntPtr hwnd, SubclassProc procedure, UIntPtr id);
    [DllImport("comctl32.dll")] private static extern IntPtr DefSubclassProc(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr hwnd);
}
