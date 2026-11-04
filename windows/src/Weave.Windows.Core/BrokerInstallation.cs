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
        var programFiles = Path.GetFullPath(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles));
        var relative = Path.GetRelativePath(programFiles, root);
        if (relative is "." or ".." || relative.StartsWith(".." + Path.DirectorySeparatorChar, StringComparison.Ordinal) || Path.IsPathRooted(relative))
            throw new UnauthorizedAccessException("The service must be installed under Program Files.");
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

    internal static void CheckAncestors(string path)
    {
        for (var parent = Directory.GetParent(path); parent is not null; parent = parent.Parent)
            CheckEntry(parent.FullName, ancestorsOnly: true);
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

    internal static void CheckEntry(string path, bool privateAccess = false, bool ancestorsOnly = false)
    {
        var attributes = File.GetAttributes(path);
        if ((attributes & FileAttributes.ReparsePoint) != 0) throw new UnauthorizedAccessException("Service paths cannot contain reparse points.");
        FileSystemSecurity security = (attributes & FileAttributes.Directory) != 0
            ? new DirectoryInfo(path).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access)
            : new FileInfo(path).GetAccessControl(AccessControlSections.Owner | AccessControlSections.Access);
        ValidateAcl(security, privateAccess, ancestorsOnly);
    }

    internal static void ValidateAcl(FileSystemSecurity security, bool privateAccess, bool ancestorsOnly)
    {
        if (security.GetOwner(typeof(SecurityIdentifier)) is not SecurityIdentifier owner || !Trusted(owner) ||
            new RawSecurityDescriptor(security.GetSecurityDescriptorBinaryForm(), 0).DiscretionaryAcl is null)
            throw new UnauthorizedAccessException("Service path ownership or access control is unsafe.");
        var forbidden = ancestorsOnly ? AncestorMutation : Mutation;
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
