using Weave.Windows.Core;
using Microsoft.Win32;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace Weave.Windows;

internal sealed class WindowsSystemProxy
{
    private const string RegistryPath = @"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    private readonly string _backupPath;
    private readonly object _gate = new();
    private const string OwnedBypass = "<local>;localhost;127.*;10.*;192.168.*";
    private sealed record Backup(int Enabled, string? Server, string? Bypass, string OwnedServer);
    public WindowsSystemProxy(string directory) => _backupPath = Path.Combine(directory, "system-proxy-backup.bin");
    public void Enable(int port)
    {
        lock (_gate)
        {
        Recover();
        using var key = Registry.CurrentUser.OpenSubKey(RegistryPath, writable: true) ?? throw new IOException(L.T("无法读取本账户的系统代理设置"));
        if (!string.IsNullOrWhiteSpace(key.GetValue("AutoConfigURL") as string))
            throw new InvalidOperationException(L.T("W-P01：Windows 自动代理脚本可能覆盖浏览器出口。请先在 Windows 代理设置中关闭脚本后重连；Weave 未修改该脚本。"));
        var server = $"127.0.0.1:{port}";
        var old = new Backup(Convert.ToInt32(key.GetValue("ProxyEnable", 0)), key.GetValue("ProxyServer") as string, key.GetValue("ProxyOverride") as string, server);
        Directory.CreateDirectory(Path.GetDirectoryName(_backupPath)!);
        var plain = JsonSerializer.SerializeToUtf8Bytes(old);
        try { File.WriteAllBytes(_backupPath, new WindowsDpapiProtector().Protect(plain)); }
        finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(plain); }
        try
        {
            key.SetValue("ProxyServer", server); key.SetValue("ProxyOverride", OwnedBypass);
            key.SetValue("ProxyEnable", 1, RegistryValueKind.DWord);
            if (!Notify()) throw new IOException(L.T("W-P02：Windows 未确认系统代理变更，请检查本账户的代理策略。"));
        }
        catch { Recover(); throw; }
        }
    }
    public void Recover()
    {
        lock (_gate)
        {
        if (!File.Exists(_backupPath)) return;
        var plain = new WindowsDpapiProtector().Unprotect(File.ReadAllBytes(_backupPath));
        Backup? backup;
        try { backup = JsonSerializer.Deserialize<Backup>(plain); }
        finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(plain); }
        if (backup is null) throw new InvalidDataException(L.T("系统代理恢复记录无效"));
        using var key = Registry.CurrentUser.OpenSubKey(RegistryPath, writable: true) ?? throw new IOException(L.T("无法恢复系统代理"));
        // If another client/user changed it, leave their new configuration untouched.
        if ((key.GetValue("ProxyServer") as string) == backup.OwnedServer)
        {
            if (Convert.ToInt32(key.GetValue("ProxyEnable", 0)) == 1) key.SetValue("ProxyEnable", backup.Enabled, RegistryValueKind.DWord);
            Restore(key, "ProxyServer", backup.Server);
            if ((key.GetValue("ProxyOverride") as string) == OwnedBypass) Restore(key, "ProxyOverride", backup.Bypass);
            Notify();
        }
        File.Delete(_backupPath);
        }
    }
    private static void Restore(RegistryKey key, string name, string? value)
    { if (value is null) key.DeleteValue(name, false); else key.SetValue(name, value); }
    private static bool Notify()
    {
        var changed = InternetSetOption(IntPtr.Zero, 39, IntPtr.Zero, 0);
        var refreshed = InternetSetOption(IntPtr.Zero, 37, IntPtr.Zero, 0);
        return changed && refreshed;
    }
    [DllImport("wininet.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool InternetSetOption(IntPtr handle, int option, IntPtr buffer, int length);
}
