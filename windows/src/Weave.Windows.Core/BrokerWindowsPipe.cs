using System.ComponentModel;
using System.Diagnostics;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Runtime.Versioning;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using Microsoft.Win32.SafeHandles;

namespace Weave.Windows.Core;

public sealed record BrokerPeer(string UserSid, int ProcessId, int SessionId);

[SupportedOSPlatform("windows")]
public static class BrokerWindowsPipe
{
    public const string PipeName = "Weave.Core.v1";
    public const string ServiceName = "WeaveCore";
    private static readonly byte[] Hello = "WEAVE001"u8.ToArray();
    private const PipeAccessRights ClientRights = PipeAccessRights.ReadData | PipeAccessRights.WriteData | PipeAccessRights.Synchronize;

    public static NamedPipeServerStream CreateServer(string name)
    {
        using var identity = WindowsIdentity.GetCurrent();
        var owner = identity.User ?? throw new UnauthorizedAccessException("Service owner is unavailable.");
        var acl = new PipeSecurity();
        acl.SetOwner(owner);
        acl.SetAccessRuleProtection(true, false);
        acl.AddAccessRule(new(new SecurityIdentifier(WellKnownSidType.NetworkSid, null), PipeAccessRights.FullControl, AccessControlType.Deny));
        acl.AddAccessRule(new(new SecurityIdentifier(WellKnownSidType.AnonymousSid, null), PipeAccessRights.FullControl, AccessControlType.Deny));
        acl.AddAccessRule(new(owner, PipeAccessRights.FullControl, AccessControlType.Allow));
        acl.AddAccessRule(new(new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null), PipeAccessRights.FullControl, AccessControlType.Allow));
        // FILE_CREATE_PIPE_INSTANCE shares the append-data bit. Never grant generic
        // write/full control to clients, even when they are authenticated local users.
        acl.AddAccessRule(new(new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null), ClientRights, AccessControlType.Allow));
        return NamedPipeServerStreamAcl.Create(name, PipeDirection.InOut, 1, PipeTransmissionMode.Byte,
            PipeOptions.Asynchronous | PipeOptions.FirstPipeInstance, 16_384, 16_384, acl, HandleInheritability.None);
    }

    public static NamedPipeClientStream CreateClient(string name) => new(".", name, ClientRights,
        PipeOptions.Asynchronous, TokenImpersonationLevel.Identification, HandleInheritability.None);

    public static async Task<NamedPipeClientStream> ConnectServiceAsync(string installedServiceExecutable, CancellationToken token)
    {
        var pipe = CreateClient(PipeName);
        try
        {
            await pipe.ConnectAsync(3000, token).ConfigureAwait(false);
            // Authenticate the OS endpoint before any profile or node credential is sent.
            if (!GetNamedPipeServerProcessId(pipe.SafePipeHandle, out var pid)) throw new Win32Exception(Marshal.GetLastWin32Error());
            if (pid == 0 || pid != ServiceProcessId()) throw new UnauthorizedAccessException("Unexpected service pipe owner.");
            using var serviceProcess = VerifyProcessImage(checked((int)pid), installedServiceExecutable);
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(5));
            await pipe.WriteAsync(Hello, timeout.Token).ConfigureAwait(false);
            var response = new byte[Hello.Length];
            await pipe.ReadExactlyAsync(response, timeout.Token).ConfigureAwait(false);
            if (!response.AsSpan().SequenceEqual(Hello)) throw new InvalidDataException("Unsupported service handshake.");
            return pipe;
        }
        catch { pipe.Dispose(); throw; }
    }

    public static async Task<BrokerPeer> AuthenticateClientAsync(NamedPipeServerStream pipe,
        string installedGuiExecutable, CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(5));
        var hello = new byte[Hello.Length];
        // ImpersonateNamedPipeClient identifies the sender of the last message read.
        await pipe.ReadExactlyAsync(hello, timeout.Token).ConfigureAwait(false);
        if (!hello.AsSpan().SequenceEqual(Hello)) throw new InvalidDataException("Unsupported client handshake.");
        if (!GetNamedPipeClientProcessId(pipe.SafePipeHandle, out var pid) || pid == 0)
            throw new Win32Exception(Marshal.GetLastWin32Error());
        using var process = VerifyProcessImage(checked((int)pid), installedGuiExecutable);
        string? sid = null;
        pipe.RunAsClient(() =>
        {
            using var identity = WindowsIdentity.GetCurrent(TokenAccessLevels.Query);
            if (!identity.IsAuthenticated || identity.User is null || identity.ImpersonationLevel != TokenImpersonationLevel.Identification)
                throw new UnauthorizedAccessException("A local identified client is required.");
            sid = identity.User.Value;
        });
        if (process.HasExited || sid is null) throw new UnauthorizedAccessException("Client exited during authentication.");
        var peer = new BrokerPeer(sid, process.Id, process.SessionId);
        await pipe.WriteAsync(Hello, timeout.Token).ConfigureAwait(false);
        return peer;
    }

    private static Process VerifyProcessImage(int pid, string expected)
    {
        var process = Process.GetProcessById(pid);
        try
        {
            var path = new StringBuilder(32_768);
            var size = path.Capacity;
            // A standard-user GUI must not request PROCESS_ALL_ACCESS to inspect SYSTEM.
            using var handle = OpenProcess(0x1000, false, pid); // PROCESS_QUERY_LIMITED_INFORMATION
            if (handle.IsInvalid || !QueryFullProcessImageName(handle, 0, path, ref size)) throw new Win32Exception(Marshal.GetLastWin32Error());
            if (!Path.GetFullPath(path.ToString()).Equals(Path.GetFullPath(expected), StringComparison.OrdinalIgnoreCase))
                throw new UnauthorizedAccessException("Unexpected executable at the service boundary.");
            return process;
        }
        catch { process.Dispose(); throw; }
    }

    private static uint ServiceProcessId()
    {
        using var manager = OpenSCManager(null, null, 1);
        if (manager.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error());
        using var service = OpenService(manager, ServiceName, 4);
        if (service.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error());
        if (!QueryServiceStatusEx(service, 0, out var status, Marshal.SizeOf<ServiceStatusProcess>(), out _))
            throw new Win32Exception(Marshal.GetLastWin32Error());
        if (status.CurrentState != 4) throw new IOException("The core service is not running.");
        return status.ProcessId;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct ServiceStatusProcess
    {
        public uint ServiceType, CurrentState, ControlsAccepted, Win32ExitCode, ServiceSpecificExitCode, CheckPoint, WaitHint, ProcessId, ServiceFlags;
    }
    private sealed class ServiceHandle : SafeHandleZeroOrMinusOneIsInvalid
    {
        public ServiceHandle() : base(true) { }
        protected override bool ReleaseHandle() => CloseServiceHandle(handle);
    }
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern ServiceHandle OpenSCManager(string? machine, string? database, uint access);
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern ServiceHandle OpenService(ServiceHandle manager, string name, uint access);
    [DllImport("advapi32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryServiceStatusEx(ServiceHandle service, int level, out ServiceStatusProcess buffer, int size, out int needed);
    [DllImport("advapi32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseServiceHandle(IntPtr handle);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool QueryFullProcessImageName(SafeProcessHandle process, int flags, StringBuilder path, ref int size);
    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern SafeProcessHandle OpenProcess(uint access, [MarshalAs(UnmanagedType.Bool)] bool inherit, int processId);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetNamedPipeServerProcessId(SafePipeHandle pipe, out uint processId);
    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetNamedPipeClientProcessId(SafePipeHandle pipe, out uint processId);
}
