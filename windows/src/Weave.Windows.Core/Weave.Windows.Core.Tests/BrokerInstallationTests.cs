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

    [NativeWindowsFact]
    public void VolumeRootsMayGrantDeleteButNothingElseThatReplacesChildren()
    {
        var acl = ProtectedAcl();
        var users = new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null);
        // The default ACL of a freshly formatted data drive: Authenticated Users may modify the root.
        acl.AddAccessRule(new(users, FileSystemRights.Modify, AccessControlType.Allow));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, false, true));
        BrokerInstallation.ValidateAcl(acl, false, true, volumeRoot: true);
        acl.AddAccessRule(new(users, FileSystemRights.DeleteSubdirectoriesAndFiles, AccessControlType.Allow));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.ValidateAcl(acl, false, true, volumeRoot: true));
    }

    [NativeWindowsFact]
    public void ChosenLocationsMustBeDedicatedLocalWeaveFolders()
    {
        var system = Path.GetPathRoot(Environment.SystemDirectory)!;
        var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        BrokerInstallation.RequireMachineLocation(Path.Combine(programFiles, "Weave", "versions", "id"));
        BrokerInstallation.RequireMachineLocation(Path.Combine(system, "Apps", "Weave", "versions", "id"));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.RequireMachineLocation(Path.Combine(system, "Apps", "Tools", "versions", "id")));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.RequireMachineLocation(@"\\server\share\Weave\versions\id"));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.RequireMachineLocation(
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Weave", "versions", "id")));
        Assert.Throws<UnauthorizedAccessException>(() => BrokerInstallation.RequireMachineLocation(
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "Weave", "versions", "id")));
    }

    private sealed class NativeWindowsFactAttribute : FactAttribute
    {
        public NativeWindowsFactAttribute() { if (!OperatingSystem.IsWindows()) Skip = "Requires Windows ACL types and installed path semantics."; }
    }
}
