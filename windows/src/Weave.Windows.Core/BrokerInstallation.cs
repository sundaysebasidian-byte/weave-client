using System.Runtime.Versioning;
using System.Security.AccessControl;
using System.Security.Cryptography;
using System.Security.Principal;

namespace Weave.Windows.Core;

[SupportedOSPlatform("windows")]
public sealed record BrokerInstallation(string Root, string ServiceExecutable, string GuiExecutable,
    string CoreExecutable, string GeoDataDirectory)
{
    public const string ServiceExecutableName = "Weave.Core.Service.exe";
    public const string CoreSha256 = "cf894375dbc00ab6708c1314ac35bbd29059f4c37f315353aaca7f1a9c566de6";
    private const FileSystemRights Mutation = FileSystemRights.Write | FileSystemRights.Delete |
        FileSystemRights.DeleteSubdirectoriesAndFiles | FileSystemRights.ChangePermissions | FileSystemRights.TakeOwnership;
    private const FileSystemRights AncestorMutation = FileSystemRights.Delete | FileSystemRights.DeleteSubdirectoriesAndFiles |
        FileSystemRights.ChangePermissions | FileSystemRights.TakeOwnership;
    private static readonly SecurityIdentifier SystemSid = new(WellKnownSidType.LocalSystemSid, null);
    private static readonly SecurityIdentifier AdministratorsSid = new(WellKnownSidType.BuiltinAdministratorsSid, null);
    private static readonly SecurityIdentifier InstallerSid = (SecurityIdentifier)new NTAccount("NT SERVICE", "TrustedInstaller")
        .Translate(typeof(SecurityIdentifier));

    public static BrokerInstallation Inspect(string root)
    {
        root = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
        RequireMachineLocation(root);
        CheckAncestors(root);
        CheckTree(root);
        var installation = new BrokerInstallation(root, Path.Combine(root, "service", ServiceExecutableName),
            Path.Combine(root, "Weave.Windows.exe"), Path.Combine(root, "runtime", "mihomo.exe"), Path.Combine(root, "geodata"));
        foreach (var file in new[] { installation.ServiceExecutable, installation.GuiExecutable })
            if (!File.Exists(file)) throw new FileNotFoundException("The installed application is incomplete.");
        VerifyHash(installation.CoreExecutable, CoreSha256);
        VerifyHash(Path.Combine(installation.GeoDataDirectory, "GeoIP.dat"), "1e49d985b16d13f3407d43582af64e0431c76e204a97460e5a8f859537687d13");
        VerifyHash(Path.Combine(installation.GeoDataDirectory, "GeoSite.dat"), "b2c9500f8e3403126a99f47bd9a5bced435c04316823b914bab6d5ee639e8cb7");
        return installation;
    }

    public static string PrepareRuntimeRoot()
    {
        using var identity = WindowsIdentity.GetCurrent();
        if (identity.User != SystemSid) throw new UnauthorizedAccessException("The installed service identity is required.");
        var common = Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData);
        CheckAncestors(Path.Combine(common, "Weave"));
        var root = common;
        foreach (var name in new[] { "Weave", "Core", "runtime-sessions" })
        {
            root = Path.Combine(root, name);
            var security = new DirectorySecurity();
            security.SetOwner(SystemSid);
            security.SetAccessRuleProtection(true, false);
            foreach (var sid in new[] { SystemSid, AdministratorsSid })
                security.AddAccessRule(new(sid, FileSystemRights.FullControl,
                    InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            // Creation supplies the ACL atomically. Existing directories are checked, never repaired here.
            security.CreateDirectory(root);
            CheckEntry(root, privateAccess: true);
        }
        return root;
    }

    /// <summary>
    /// Program Files by default; the installer may also place Weave in a dedicated folder on a local fixed drive. Either way the
    /// path must stay out of Windows, user profiles and ProgramData, and the ACL checks decide whether it is safe for SYSTEM.
    /// </summary>
    internal static void RequireMachineLocation(string root)
    {
        if (root.StartsWith(@"\\", StringComparison.Ordinal) || Path.GetPathRoot(root) is not { Length: 3 } volume || volume[1] != ':')
            throw new UnauthorizedAccessException("The service must be installed on a local drive.");
        if (new DriveInfo(volume).DriveType != DriveType.Fixed)
            throw new UnauthorizedAccessException("The service must be installed on a local fixed drive.");
        var programFiles = Path.GetFullPath(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles));
        if (IsUnder(root, programFiles) && !string.Equals(root, programFiles, StringComparison.OrdinalIgnoreCase)) return;
        foreach (var folder in new[] { Environment.SpecialFolder.Windows, Environment.SpecialFolder.CommonApplicationData })
            if (IsUnder(root, Environment.GetFolderPath(folder)))
                throw new UnauthorizedAccessException("The service cannot run from Windows or ProgramData.");
        var profiles = Directory.GetParent(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile))?.FullName;
        if (profiles is not null && IsUnder(root, profiles))
            throw new UnauthorizedAccessException("The service cannot run from a user profile.");
        // The release sits at <install>\versions\<id>; the install folder itself is a dedicated "Weave" folder.
        var install = Directory.GetParent(root)?.Parent;
        if (install is null || !string.Equals(install.Name, "Weave", StringComparison.OrdinalIgnoreCase) || install.Parent is null)
            throw new UnauthorizedAccessException("The service must run from a dedicated Weave installation folder.");
    }

    private static bool IsUnder(string path, string folder)
    {
        folder = Path.GetFullPath(folder).TrimEnd(Path.DirectorySeparatorChar);
        return path.StartsWith(folder, StringComparison.OrdinalIgnoreCase) &&
            (path.Length == folder.Length || path[folder.Length] == Path.DirectorySeparatorChar);
    }

    internal static void CheckAncestors(string path)
    {
        // A volume root cannot be renamed or deleted, so DELETE on it does not let anyone move the tree below.
        for (var parent = Directory.GetParent(path); parent is not null; parent = parent.Parent)
            CheckEntry(parent.FullName, ancestorsOnly: true, volumeRoot: parent.Parent is null);
    }

    internal static void CheckTree(string root)
    {
        CheckEntry(root);
        foreach (var path in Directory.EnumerateFileSystemEntries(root))
        {
            CheckEntry(path);
            if (Directory.Exists(path)) CheckTree(path);
        }
    }

    internal static void CheckEntry(string path, bool privateAccess = false, bool ancestorsOnly = false, bool volumeRoot = false)
    {
        var attributes = File.GetAttributes(path);
        if ((attributes & FileAttributes.ReparsePoint) != 0) throw new UnauthorizedAccessException("Service paths cannot contain reparse points.");
        FileSystemSecurity security = (attributes & FileAttributes.Directory) != 0
            ? new DirectoryInfo(path).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access)
            : new FileInfo(path).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access);
        ValidateAcl(security, privateAccess, ancestorsOnly, volumeRoot);
    }

    internal static void ValidateAcl(FileSystemSecurity security, bool privateAccess, bool ancestorsOnly, bool volumeRoot = false)
    {
        if (security.GetOwner(typeof(SecurityIdentifier)) is not SecurityIdentifier owner || !Trusted(owner) ||
            new RawSecurityDescriptor(security.GetSecurityDescriptorBinaryForm(), 0).DiscretionaryAcl is null)
            throw new UnauthorizedAccessException("Service path ownership or access control is unsafe.");
        var forbidden = ancestorsOnly ? AncestorMutation : Mutation;
        if (volumeRoot) forbidden &= ~FileSystemRights.Delete;
        foreach (FileSystemAccessRule rule in security.GetAccessRules(true, true, typeof(SecurityIdentifier)))
        {
            if (rule.AccessControlType != AccessControlType.Allow ||
                (rule.PropagationFlags & PropagationFlags.InheritOnly) != 0 || Trusted((SecurityIdentifier)rule.IdentityReference)) continue;
            if (privateAccess || (rule.FileSystemRights & forbidden) != 0)
                throw new UnauthorizedAccessException("An untrusted identity can access or replace service files.");
        }
    }

    private static bool Trusted(SecurityIdentifier sid) => sid == SystemSid || sid == AdministratorsSid || sid == InstallerSid;

    private static void VerifyHash(string path, string expected)
    {
        CheckEntry(path);
        using var file = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        if (!Convert.ToHexString(SHA256.HashData(file)).Equals(expected, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("An installed core or geodata file does not match the pinned release.");
    }
}
