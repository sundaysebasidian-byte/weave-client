using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using Microsoft.Win32.SafeHandles;

namespace Weave.Windows.Service;

internal static class ServiceProcessAccess
{
    public static void AllowImageQuery(SafeProcessHandle ownProcess)
    {
        // SYSTEM's default process DACL need not allow ordinary users to authenticate
        // its image. Grant only QUERY_LIMITED_INFORMATION on this process object.
        const int kernelObject = 6, daclInformation = 4;
        var error = GetSecurityInfo(ownProcess, kernelObject, daclInformation,
            out _, out _, out _, out _, out var descriptor);
        if (error != 0) throw new Win32Exception(error);
        try
        {
            var bytes = new byte[checked((int)GetSecurityDescriptorLength(descriptor))];
            Marshal.Copy(descriptor, bytes, 0, bytes.Length);
            var acl = new RawSecurityDescriptor(bytes, 0).DiscretionaryAcl
                ?? throw new UnauthorizedAccessException("The service process requires an explicit DACL.");
            var index = 0;
            while (index < acl.Count && (acl[index].AceFlags & AceFlags.Inherited) == 0 &&
                acl[index] is QualifiedAce { AceQualifier: AceQualifier.AccessDenied }) index++;
            acl.InsertAce(index, new CommonAce(AceFlags.None, AceQualifier.AccessAllowed, 0x1000,
                new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null), false, null));
            var updated = new byte[acl.BinaryLength];
            acl.GetBinaryForm(updated, 0);
            error = SetSecurityInfo(ownProcess, kernelObject, daclInformation, IntPtr.Zero, IntPtr.Zero, updated, IntPtr.Zero);
            if (error != 0) throw new Win32Exception(error);
        }
        finally { LocalFree(descriptor); }
    }

    [DllImport("advapi32.dll")]
    private static extern int GetSecurityInfo(SafeProcessHandle handle, int objectType, int information,
        out IntPtr owner, out IntPtr group, out IntPtr dacl, out IntPtr sacl, out IntPtr descriptor);
    [DllImport("advapi32.dll")]
    private static extern int SetSecurityInfo(SafeProcessHandle handle, int objectType, int information,
        IntPtr owner, IntPtr group, byte[] dacl, IntPtr sacl);
    [DllImport("advapi32.dll")]
    private static extern uint GetSecurityDescriptorLength(IntPtr descriptor);
    [DllImport("kernel32.dll")]
    private static extern IntPtr LocalFree(IntPtr memory);
}
