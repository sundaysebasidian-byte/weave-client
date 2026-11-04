# Disposable runner fixture: ordinary elevated OpenProcess must fail, while SCM stop remains allowed.
param([Parameter(Mandatory)][int] $ServiceProcessId)
$ErrorActionPreference = 'Stop'
if ($env:WEAVE_ISOLATED_INSTALLER_TEST -ne '1' -or $env:GITHUB_ACTIONS -ne 'true') { throw 'Disposable runner required.' }
Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
public static class RestrictedServiceProcess {
    [StructLayout(LayoutKind.Sequential, Pack=4)] struct Privilege { public uint Count; public long Luid; public uint Attributes; }
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll")] static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr memory);
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool OpenProcessToken(IntPtr process, uint access, out IntPtr token);
    [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool LookupPrivilegeValue(string system, string name, out long luid);
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool AdjustTokenPrivileges(IntPtr token, bool disable, ref Privilege value, uint size, IntPtr previous, IntPtr returned);
    [DllImport("advapi32.dll", CharSet=CharSet.Unicode, SetLastError=true)] static extern bool ConvertStringSecurityDescriptorToSecurityDescriptor(string sddl, uint revision, out IntPtr descriptor, IntPtr size);
    [DllImport("advapi32.dll", SetLastError=true)] static extern bool SetKernelObjectSecurity(IntPtr handle, uint information, IntPtr descriptor);
    static void Check(bool ok) { if (!ok) throw new Win32Exception(Marshal.GetLastWin32Error()); }
    public static void Apply(int pid) {
        IntPtr token, descriptor = IntPtr.Zero, process = IntPtr.Zero;
        Check(OpenProcessToken(GetCurrentProcess(), 0x28, out token));
        var privilege = new Privilege { Count = 1, Attributes = 2 };
        try {
            Check(LookupPrivilegeValue(null, "SeDebugPrivilege", out privilege.Luid));
            Check(AdjustTokenPrivileges(token, false, ref privilege, 0, IntPtr.Zero, IntPtr.Zero));
            if (Marshal.GetLastWin32Error() != 0) throw new Win32Exception(Marshal.GetLastWin32Error());
            process = OpenProcess(0x40000, false, pid);
            Check(process != IntPtr.Zero);
            Check(ConvertStringSecurityDescriptorToSecurityDescriptor("D:P(D;;0x100000;;;BA)(A;;GA;;;SY)(A;;0x61000;;;BA)(A;;0x1000;;;AU)", 1, out descriptor, IntPtr.Zero));
            Check(SetKernelObjectSecurity(process, 4, descriptor));
        } finally {
            if (descriptor != IntPtr.Zero) LocalFree(descriptor);
            if (process != IntPtr.Zero) CloseHandle(process);
            privilege.Attributes = 0;
            Check(AdjustTokenPrivileges(token, false, ref privilege, 0, IntPtr.Zero, IntPtr.Zero));
            CloseHandle(token);
        }
        process = OpenProcess(0x101000, false, pid);
        int error = Marshal.GetLastWin32Error();
        if (process != IntPtr.Zero) CloseHandle(process);
        if (process != IntPtr.Zero || error != 5) throw new Exception("Fixture did not reproduce OpenProcess access denied.");
    }
}
'@
[RestrictedServiceProcess]::Apply($ServiceProcessId)
"Confirmed OpenProcess(SYNCHRONIZE | QUERY_LIMITED_INFORMATION) denied for service PID $ServiceProcessId."
