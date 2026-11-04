using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;

namespace Weave.Windows.Core;

/// <summary>A private, versioned session with an exclusive lease; legacy/unknown files are never swept.</summary>
public sealed class RuntimeSession : IDisposable
{
    private const string Marker = "weave-runtime-v1";
    private readonly string _leasePath;
    private FileStream? _lease;
    public string DirectoryPath { get; }

    private RuntimeSession(string root, string id, FileStream lease)
    {
        DirectoryPath = Path.Combine(root, id);
        _leasePath = Path.Combine(root, id + ".session");
        _lease = lease;
    }

    public static RuntimeSession Reserve(string root)
    {
        root = Path.GetFullPath(root);
        PrepareRoot(root);
        var id = Guid.NewGuid().ToString("N");
        var lease = new FileStream(Path.Combine(root, id + ".session"), FileMode.CreateNew, FileAccess.ReadWrite, FileShare.None);
        try
        {
            lease.Write(Encoding.ASCII.GetBytes(Marker));
            lease.Flush(flushToDisk: true);
            return new RuntimeSession(root, id, lease);
        }
        catch { lease.Dispose(); throw; }
    }

    public bool CleanupAfterCoreExit()
    {
        if (_lease is null) throw new ObjectDisposedException(nameof(RuntimeSession));
        if (!HasOnlyRuntimeFiles(DirectoryPath)) return false;
        if (Directory.Exists(DirectoryPath)) Directory.Delete(DirectoryPath, recursive: true);
        Dispose();
        File.Delete(_leasePath);
        return true;
    }

    public static (int Removed, int Retained) Recover(string root)
    {
        if (!Directory.Exists(root)) return (0, 0);
        if (IsLink(root)) return (0, 1);
        var removed = 0;
        var retained = 0;
        foreach (var path in Directory.EnumerateFiles(root, "*.session"))
        {
            var id = Path.GetFileNameWithoutExtension(path);
            if (!Guid.TryParseExact(id, "N", out _) || IsLink(path)) continue;
            try
            {
                using (var lease = new FileStream(path, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
                {
                    var bytes = new byte[64];
                    var count = lease.Read(bytes);
                    var directory = Path.Combine(root, id);
                    if (Encoding.ASCII.GetString(bytes, 0, count) != Marker || !HasOnlyRuntimeFiles(directory))
                    { retained++; continue; }
                    if (Directory.Exists(directory)) Directory.Delete(directory, recursive: true);
                }
                File.Delete(path);
                removed++;
            }
            catch (IOException) { retained++; } // An active lease or a file still mapped by the core.
            catch (UnauthorizedAccessException) { retained++; }
        }
        return (removed, retained);
    }

    public void Dispose()
    {
        _lease?.Dispose();
        _lease = null;
    }

    private static bool HasOnlyRuntimeFiles(string directory)
    {
        if (!Directory.Exists(directory)) return !File.Exists(directory);
        if (IsLink(directory)) return false;
        foreach (var path in Directory.EnumerateFileSystemEntries(directory))
        {
            if (IsLink(path)) return false;
            if (Path.GetFileName(path) == "providers" && Directory.Exists(path))
            {
                if (Directory.EnumerateFileSystemEntries(path).Any(file => IsLink(file) || Directory.Exists(file) || Path.GetExtension(file) != ".yaml")) return false;
            }
            else if (Directory.Exists(path) || Path.GetFileName(path) is not ("config.yaml" or "cache.db" or "GeoIP.dat" or "GeoSite.dat")) return false;
        }
        return true;
    }

    private static bool IsLink(string path) => (File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0;

    private static void PrepareRoot(string root)
    {
        Directory.CreateDirectory(root);
        if (IsLink(root)) throw new IOException("Runtime root must not be a symbolic link.");
        if (OperatingSystem.IsWindows())
        {
            using var identity = WindowsIdentity.GetCurrent();
            var user = identity.User ?? throw new IOException("Cannot identify the session owner.");
            var security = new DirectorySecurity();
            security.SetOwner(user);
            security.SetAccessRuleProtection(isProtected: true, preserveInheritance: false);
            security.AddAccessRule(new FileSystemAccessRule(user, FileSystemRights.FullControl,
                InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
            new DirectoryInfo(root).SetAccessControl(security);
        }
        else File.SetUnixFileMode(root, UnixFileMode.UserRead | UnixFileMode.UserWrite | UnixFileMode.UserExecute);
    }
}
