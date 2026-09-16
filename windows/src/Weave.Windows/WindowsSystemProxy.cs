using Microsoft.Win32;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace Weave.Windows;

internal sealed class WindowsSystemProxy
{
    private const string RegistryPath = @"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    private readonly string _backupPath;
    private sealed record Backup(int Enabled, string? Server, string? Bypass, string OwnedServer);
    public WindowsSystemProxy(string directory) => _backupPath = Path.Combine(directory, "system-proxy-backup.bin");
    public void Enable(int port)
    {
        Recover();
        using var key = Registry.CurrentUser.OpenSubKey(RegistryPath, writable: true) ?? throw new IOException("无法读取本账户的系统代理设置");
        var server = $"127.0.0.1:{port}";
        var old = new Backup(Convert.ToInt32(key.GetValue("ProxyEnable", 0)), key.GetValue("ProxyServer") as string, key.GetValue("ProxyOverride") as string, server);
        Directory.CreateDirectory(Path.GetDirectoryName(_backupPath)!);
        var plain = JsonSerializer.SerializeToUtf8Bytes(old);
        try { File.WriteAllBytes(_backupPath, new WindowsDpapiProtector().Protect(plain)); }
        finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(plain); }
        try
        {
            key.SetValue("ProxyServer", server); key.SetValue("ProxyOverride", "<local>;localhost;127.*;10.*;192.168.*");
            key.SetValue("ProxyEnable", 1, RegistryValueKind.DWord); Notify();
        }
        catch { Recover(); throw; }
    }
    public void Recover()
    {
        if (!File.Exists(_backupPath)) return;
        var plain = new WindowsDpapiProtector().Unprotect(File.ReadAllBytes(_backupPath));
        Backup? backup;
        try { backup = JsonSerializer.Deserialize<Backup>(plain); }
        finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(plain); }
        if (backup is null) throw new InvalidDataException("系统代理恢复记录无效");
        using var key = Registry.CurrentUser.OpenSubKey(RegistryPath, writable: true) ?? throw new IOException("无法恢复系统代理");
        // If another client/user changed it, leave their new configuration untouched.
        if ((key.GetValue("ProxyServer") as string) == backup.OwnedServer)
        {
            key.SetValue("ProxyEnable", backup.Enabled, RegistryValueKind.DWord);
            Restore(key, "ProxyServer", backup.Server); Restore(key, "ProxyOverride", backup.Bypass);
            Notify();
        }
        File.Delete(_backupPath);
    }
    private static void Restore(RegistryKey key, string name, string? value)
    { if (value is null) key.DeleteValue(name, false); else key.SetValue(name, value); }
    private static void Notify()
    { InternetSetOption(IntPtr.Zero, 39, IntPtr.Zero, 0); InternetSetOption(IntPtr.Zero, 37, IntPtr.Zero, 0); }
    [DllImport("wininet.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool InternetSetOption(IntPtr handle, int option, IntPtr buffer, int length);
}
