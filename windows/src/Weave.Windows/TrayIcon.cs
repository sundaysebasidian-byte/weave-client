using System.Runtime.InteropServices;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>Small native tray integration; no timer, web view or background UI rendering.</summary>
internal sealed class TrayIcon : IDisposable
{
    private const uint CallbackMessage = 0x8000 + 90;
    private readonly IntPtr _window;
    private readonly Action _show, _exit, _resume;
    private readonly SubclassProc _procedure;
    private readonly uint _taskbarCreated;
    private IntPtr _icon;
    private bool _disposed;
    public bool Available { get; private set; }

    public TrayIcon(IntPtr window, Action show, Action exit, Action resume)
    {
        _window = window; _show = show; _exit = exit; _resume = resume;
        _procedure = HandleMessage;
        _taskbarCreated = RegisterWindowMessage("TaskbarCreated");
        // Allow only the harmless show-window message across normal/elevated launches.
        ChangeWindowMessageFilterEx(window, WindowActivation.Message, 1, IntPtr.Zero);
        ExtractIconEx(Environment.ProcessPath!, 0, out _icon, IntPtr.Zero, 1);
        if (!SetWindowSubclass(window, _procedure, new UIntPtr(90), IntPtr.Zero)) return;
        Add();
    }

    private NotifyIconData Data() => new() { Size = (uint)Marshal.SizeOf<NotifyIconData>(), Window = _window,
        Id = 90, Flags = 1 | 2 | 4, Callback = CallbackMessage, Icon = _icon, Tip = "Weave", Info = "", Title = "" };

    private void Add() { var data = Data(); Available = ShellNotifyIcon(0, ref data); }

    private IntPtr HandleMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam, UIntPtr id, IntPtr reference)
    {
        try
        {
          if (!_disposed)
          {
            if (message == WindowActivation.Message) { _show(); return IntPtr.Zero; }
            if (message == _taskbarCreated) Add();
            if (message == 0x218 && (wParam.ToInt64() == 7 || wParam.ToInt64() == 18)) _resume();
            if (message == CallbackMessage)
            {
                var code = lParam.ToInt64() & 0xffff;
                if (code == 0x203) _show(); // double click
                if (code == 0x205 || code == 0x7b) ShowMenu();
                return IntPtr.Zero;
            }
          }
        }
        catch (Exception) { /* Never unwind a managed exception through the native window procedure. */ }
        return DefSubclassProc(window, message, wParam, lParam);
    }

    private void ShowMenu()
    {
        var menu = CreatePopupMenu();
        if (menu == IntPtr.Zero) return;
        try
        {
            AppendMenu(menu, 0, new UIntPtr(1), L.T("打开 Weave"));
            AppendMenu(menu, 0, new UIntPtr(2), L.T("断开并退出"));
            GetCursorPos(out var point); SetForegroundWindow(_window);
            var selected = TrackPopupMenu(menu, 0x100 | 0x2, point.X, point.Y, 0, _window, IntPtr.Zero);
            if (selected == 1) _show(); else if (selected == 2) _exit();
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
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern uint RegisterWindowMessage(string message);
    [DllImport("user32.dll")] private static extern bool DestroyIcon(IntPtr icon);
    [DllImport("user32.dll")] private static extern IntPtr CreatePopupMenu();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool AppendMenu(IntPtr menu, uint flags, UIntPtr id, string text);
    [DllImport("user32.dll")] private static extern int TrackPopupMenu(IntPtr menu, uint flags, int x, int y, int reserved, IntPtr owner, IntPtr rect);
    [DllImport("user32.dll")] private static extern bool DestroyMenu(IntPtr menu);
    [DllImport("user32.dll")] private static extern bool GetCursorPos(out Point point);
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr hwnd, uint message, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] private static extern bool ChangeWindowMessageFilterEx(IntPtr hwnd, uint message, uint action, IntPtr changeFilter);
}
