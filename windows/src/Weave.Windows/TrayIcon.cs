using System.Runtime.InteropServices;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>What the tray menu needs to know about the window right now.</summary>
internal readonly record struct TrayState(bool Connected, bool Connecting, bool Busy);

/// <summary>Small native tray integration; no timer, web view or background UI rendering.</summary>
internal sealed class TrayIcon : IDisposable
{
    private const uint CallbackMessage = 0x8000 + 90;
    private const int OpenCommand = 1, ExitCommand = 2, ToggleCommand = 3;
    private readonly IntPtr _window;
    private readonly Func<TrayState> _state;
    private readonly Action _show, _toggle, _exit, _resume;
    private readonly SubclassProc _procedure;
    private readonly uint _taskbarCreated;
    private IntPtr _icon;
    private string _tip = "Weave";
    private bool _disposed;
    public bool Available { get; private set; }

    public TrayIcon(IntPtr window, Func<TrayState> state, Action show, Action toggle, Action exit, Action resume)
    {
        _window = window; _state = state; _show = show; _toggle = toggle; _exit = exit; _resume = resume;
        _procedure = HandleMessage;
        _taskbarCreated = RegisterWindowMessage("TaskbarCreated");
        // Allow only the harmless show-window message across normal/elevated launches.
        ChangeWindowMessageFilterEx(window, WindowActivation.Message, 1, IntPtr.Zero);
        LoadIcon();
        if (!SetWindowSubclass(window, _procedure, new UIntPtr(90), IntPtr.Zero)) return;
        Add();
    }

    // NIF_MESSAGE | NIF_ICON | NIF_TIP for add; NIF_ICON or NIF_TIP alone for modify.
    private NotifyIconData Data(uint flags = 1 | 2 | 4) => new() { Size = (uint)Marshal.SizeOf<NotifyIconData>(), Window = _window,
        Id = 90, Flags = flags, Callback = CallbackMessage, Icon = _icon, Tip = _tip, Info = "", Title = "" };

    private void Add() { var data = Data(); Available = ShellNotifyIcon(0, ref data); }

    /// <summary>Shown when hovering the icon: the app name and the connection status.</summary>
    public void SetTip(string tip)
    {
        if (_disposed) return;
        if (tip.Length > 120) tip = tip[..120];
        if (tip == _tip) return;
        _tip = tip;
        if (!Available) return;
        var data = Data(4); ShellNotifyIcon(1, ref data);
    }

    // The shell draws the tray icon at the small-icon size of the system DPI (16 px at 100 %, 32 px at 200 %, ...).
    // Asking for exactly that size picks the matching image of the multi-size icon instead of letting Windows scale one.
    private void LoadIcon()
    {
        var previous = _icon;
        _icon = IntPtr.Zero;
        var path = Environment.ProcessPath;
        if (path is not null)
        {
            try
            {
                var dpi = GetDpiForSystem();
                var size = GetSystemMetricsForDpi(49, dpi == 0 ? 96 : dpi); // SM_CXSMICON
                if (size > 0 && PrivateExtractIcons(path, 0, size, size, out var icon, out _, 1, 0) == 1) _icon = icon;
            }
            catch (Exception error) when (error is EntryPointNotFoundException or DllNotFoundException) { }
        }
        if (_icon == IntPtr.Zero && path is not null) ExtractIconEx(path, 0, out _icon, IntPtr.Zero, 1);
        if (previous != IntPtr.Zero && previous != _icon) DestroyIcon(previous);
    }

    private void ReloadIcon()
    {
        LoadIcon();
        if (!Available) return;
        var data = Data(2); ShellNotifyIcon(1, ref data);
    }

    private IntPtr HandleMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr reference)
    {
        try
        {
          if (!_disposed)
          {
            if (message == WindowActivation.Message) { _show(); return IntPtr.Zero; }
            if (message == _taskbarCreated) { LoadIcon(); Add(); }
            else if (message == 0x02E0 || message == 0x7E) ReloadIcon(); // WM_DPICHANGED, WM_DISPLAYCHANGE
            if (message == 0x218 && (wParam.ToInt64() == 7 || wParam.ToInt64() == 18)) _resume();
            if (message == CallbackMessage)
            {
                var code = lParam.ToInt64() & 0xffff;
                if (code == 0x202 || code == 0x203) _show(); // left click / double click
                if (code == 0x205 || code == 0x7b) ShowMenu();
                return IntPtr.Zero;
            }
          }
        }
        catch (Exception) { /* Never unwind a managed exception through the native window procedure. */ }
        return DefSubclassProc(window, message, wParam, lParam);
    }

    // The menu is built when it opens, so its connect/disconnect entry always matches the window.
    private void ShowMenu()
    {
        var menu = CreatePopupMenu();
        if (menu == IntPtr.Zero) return;
        try
        {
            var state = _state();
            AppendMenu(menu, 0, new UIntPtr(OpenCommand), L.T("打开 Weave"));
            SetMenuDefaultItem(menu, OpenCommand, 0);
            AppendMenu(menu, 0x800, UIntPtr.Zero, null);
            var toggle = state.Connecting ? L.T("取消连接") : state.Connected ? L.T("断开连接") : L.T("连接");
            AppendMenu(menu, state.Busy ? 0x1u : 0u, new UIntPtr(ToggleCommand), toggle); // MF_GRAYED while an operation runs
            AppendMenu(menu, 0x800, UIntPtr.Zero, null);
            AppendMenu(menu, 0, new UIntPtr(ExitCommand), L.T("断开并退出"));
            GetCursorPos(out var point); SetForegroundWindow(_window);
            var selected = TrackPopupMenu(menu, 0x100 | 0x2, point.X, point.Y, 0, _window, IntPtr.Zero);
            if (selected == OpenCommand) _show();
            else if (selected == ToggleCommand) _toggle();
            else if (selected == ExitCommand) _exit();
            PostMessage(_window, 0, IntPtr.Zero, IntPtr.Zero);
        }
        finally { DestroyMenu(menu); }
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        var data = Data(); ShellNotifyIcon(2, ref data);
        RemoveWindowSubclass(_window, _procedure, new UIntPtr(90));
        if (_icon != IntPtr.Zero) DestroyIcon(_icon);
        _icon = IntPtr.Zero; Available = false;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NotifyIconData
    {
        public uint Size; public IntPtr Window; public uint Id, Flags, Callback; public IntPtr Icon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string Tip;
        public uint State, StateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string Info;
        public uint Timeout;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string Title;
        public uint InfoFlags; public Guid Guid; public IntPtr BalloonIcon;
    }
    [StructLayout(LayoutKind.Sequential)] private struct Point { public int X, Y; }
    private delegate IntPtr SubclassProc(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr reference);
    [DllImport("comctl32.dll")] private static extern bool SetWindowSubclass(IntPtr hwnd, SubclassProc procedure, UIntPtr id, IntPtr reference);
    [DllImport("comctl32.dll")] private static extern bool RemoveWindowSubclass(IntPtr hwnd, SubclassProc procedure, UIntPtr id);
    [DllImport("comctl32.dll")] private static extern IntPtr DefSubclassProc(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("shell32.dll", EntryPoint = "Shell_NotifyIconW", CharSet = CharSet.Unicode)] private static extern bool ShellNotifyIcon(uint message, ref NotifyIconData data);
    [DllImport("shell32.dll", CharSet = CharSet.Unicode)] private static extern uint ExtractIconEx(string path, int index, out IntPtr large, IntPtr small, uint count);
    [DllImport("user32.dll", EntryPoint = "PrivateExtractIconsW", CharSet = CharSet.Unicode)]
    private static extern uint PrivateExtractIcons(string path, int index, int width, int height, out IntPtr icon, out uint iconId, uint count, uint flags);
    [DllImport("user32.dll")] private static extern uint GetDpiForSystem();
    [DllImport("user32.dll")] private static extern int GetSystemMetricsForDpi(int index, uint dpi);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern uint RegisterWindowMessage(string message);
    [DllImport("user32.dll")] private static extern bool DestroyIcon(IntPtr icon);
    [DllImport("user32.dll")] private static extern IntPtr CreatePopupMenu();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool AppendMenu(IntPtr menu, uint flags, UIntPtr id, string? text);
    [DllImport("user32.dll")] private static extern bool SetMenuDefaultItem(IntPtr menu, uint item, uint byPosition);
    [DllImport("user32.dll")] private static extern int TrackPopupMenu(IntPtr menu, uint flags, int x, int y, int reserved, IntPtr owner, IntPtr rect);
    [DllImport("user32.dll")] private static extern bool DestroyMenu(IntPtr menu);
    [DllImport("user32.dll")] private static extern bool GetCursorPos(out Point point);
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] private static extern bool ChangeWindowMessageFilterEx(IntPtr hwnd, uint message, uint action, IntPtr changeFilter);
}
