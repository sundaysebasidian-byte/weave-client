using System.Runtime.Versioning;
using System.Security.AccessControl;
using System.Security.Principal;

namespace Weave.Windows.Core.Tests;

[SupportedOSPlatform("windows")]
public sealed class BrokerInstallationTests
{
    private static DirectorySecurity ProtectedAcl()
    {
        var acl = new DirectorySecurity();
        var system = new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);
        acl.SetOwner(system);
        acl.SetAccessRuleProtection(true, false);
        acl.AddAccessRule(new(system, FileSystemRights.FullControl, AccessControlType.Allow));
        return acl;
    }

    [NativeWindowsFact]
    public void OrdinaryUsersMayReadInstalledBinariesButCannotReadPrivateSessions()
    {
        var acl = ProtectedAcl();
        acl.AddAccessRule(new(new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null),
            FileSystemRights.ReadAndExecute, AccessControlType.Allow));
        BrokerInstallation.ValidateAcl(acl, privateAccess: false, ancestorsOnly: false);
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, true, false));
    }

    [NativeWindowsFact]
    public void UserWritableOrUserOwnedInstallationIsRejected()
    {
        var acl = ProtectedAcl();
        var users = new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null);
        acl.AddAccessRule(new(users, FileSystemRights.Write, AccessControlType.Allow));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, false, false));
        acl = ProtectedAcl();
        acl.SetOwner(users);
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, false, false));
    }

    [NativeWindowsFact]
    public void AncestorsMayAllowCreatingSiblingsButNeverReplacingExistingChildren()
    {
        var acl = ProtectedAcl();
        var users = new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null);
        acl.AddAccessRule(new(users, FileSystemRights.CreateDirectories, AccessControlType.Allow));
        BrokerInstallation.ValidateAcl(acl, false, true);
        acl.AddAccessRule(new(users, FileSystemRights.DeleteSubdirectoriesAndFiles, AccessControlType.Allow));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, false, true));
    }

    [NativeWindowsFact]
    public void NullDaclAndPerUserInstallationsAreRejected()
    {
        var system = new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null);
        var raw = new RawSecurityDescriptor(ControlFlags.DiscretionaryAclPresent, system, system, null, null);
        var bytes = new byte[raw.BinaryLength];
        raw.GetBinaryForm(bytes, 0);
        var acl = new DirectorySecurity();
        acl.SetSecurityDescriptorBinaryForm(bytes);
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, false, false));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.Inspect(Path.GetTempPath()));
    }

    private sealed class NativeWindowsFactAttribute : FactAttribute
    {
        public NativeWindowsFactAttribute() { if (!OperatingSystem.IsWindows()) Skip = "Requires Windows ACL types and installed path semantics."; }
    }
}
