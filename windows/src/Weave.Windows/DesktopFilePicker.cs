using Weave.Windows.Core;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

namespace Weave.Windows;

/// <summary>Classic desktop dialog also works when the TUN app is elevated.</summary>
internal static class DesktopFilePicker
{
    internal static string? Open(IntPtr owner, string? filter = null) =>
        Pick(owner, filter ?? L.T("Clash 配置\0*.yaml;*.yml;*.json;*.txt\0\0"), false, "");
    internal static string? SaveZip(IntPtr owner) => Pick(owner, L.T("ZIP 配置包\0*.zip\0\0"), true, "Weave-subscriptions.zip");
    private static string? Pick(IntPtr owner, string filter, bool save, string initialName)
    {
        var data = new OpenFileName
        {
            Size = Marshal.SizeOf<OpenFileName>(), Owner = owner, Filter = filter, FilterIndex = 1,
            File = new StringBuilder(initialName, 32768), MaxFile = 32768,
            Flags = 0x00080000 | 0x00000800 | 0x00000008 | (save ? 0x2 : 0x1000),
            DefaultExtension = save ? "zip" : null,
        };
        if (save ? GetSaveFileNameW(ref data) : GetOpenFileNameW(ref data)) return data.File.ToString();
        var error = CommDlgExtendedError();
        if (error != 0) throw new Win32Exception((int)error, L.T("无法打开文件选择窗口"));
        return null;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct OpenFileName
    {
        public int Size;
        public IntPtr Owner, Instance;
        public string? Filter;
        public IntPtr CustomFilter;
        public int MaxCustomFilter, FilterIndex;
        public StringBuilder File;
        public int MaxFile;
        public IntPtr FileTitle;
        public int MaxFileTitle;
        public string? InitialDirectory, Title;
        public int Flags;
        public short FileOffset, FileExtension;
        public string? DefaultExtension;
        public IntPtr CustomData, Hook, TemplateName, Reserved;
        public int ReservedValue, FlagsEx;
    }
    [DllImport("comdlg32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetOpenFileNameW(ref OpenFileName data);
    [DllImport("comdlg32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetSaveFileNameW(ref OpenFileName data);
    [DllImport("comdlg32.dll")]
    private static extern uint CommDlgExtendedError();
}
