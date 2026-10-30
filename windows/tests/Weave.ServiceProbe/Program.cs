using Weave.Windows.TestFixtures;
using System.Net;
using System.Net.Sockets;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Security.Principal;
using System.Text;
using Microsoft.Win32.SafeHandles;
using Weave.Windows.Core;

if (Environment.GetEnvironmentVariable("WEAVE_ISOLATED_SERVICE_TEST") != "1" || args is not [var mode, var servicePath] ||
    mode is not ("check" or "reject" or "crash")) return 2;
using var identity = WindowsIdentity.GetCurrent();
if (new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator)) return 3;
using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(60));
try
{
    if (mode == "reject")
    {
        try { await using var invalid = await BrokerClient.ConnectAsync(servicePath, timeout.Token); }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException) { return 0; }
        return 4;
    }
    using var listener = new TcpListener(IPAddress.Loopback, 0);
    listener.Start();
    var server = SyntheticUpstream.ServeAsync(listener, timeout.Token);
    using var importer = new SubscriptionImporter();
    var port = ((IPEndPoint)listener.LocalEndpoint).Port;
    var subscription = importer.ImportText("service-fixture", "inline://service-fixture",
        $"proxies: [{{name: fixture, type: http, server: 127.0.0.1, port: {port}, username: fixture-user, password: fixture-password}}]");
    await using var client = await BrokerClient.ConnectAsync(servicePath, timeout.Token);
    ServiceAccessProbe.Verify();
    Console.WriteLine("SERVICE_IDENTITY_AND_ACCESS_CONFIRMED");
    if (await client.CheckReadyAsync(timeout.Token)) return 5;
    var profile = BrokerProfile.FromLocal([subscription], [], subscription.Id, subscription.Nodes[0].Id,
        new() { EnableTun = true, RoutingMode = RoutingMode.Global, Ipv6Enabled = false });
    await client.StartAsync(profile, timeout.Token);
    Console.WriteLine("SERVICE_CORE_STARTED");
    using var http = new HttpClient(new SocketsHttpHandler { UseProxy = false });
    try
    {
        if (await http.GetStringAsync("http://198.51.100.42/check", timeout.Token) != "weave-service-upstream") return 6;
    }
    catch
    {
        Console.Error.WriteLine($"Synthetic upstream task: {server.Status}; {server.Exception}");
        await SyntheticNetworkSnapshot.RecordAsync(Console.Error.WriteLine);
        throw;
    }
    var header = await server;
    if (!header.Contains("Proxy-Authorization: Basic " + Convert.ToBase64String(Encoding.ASCII.GetBytes("fixture-user:fixture-password")), StringComparison.OrdinalIgnoreCase)) return 7;
    Console.WriteLine("CORE_READY");
    if (mode == "crash")
    {
        var ended = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        client.Exited += (_, _) => ended.TrySetResult();
        if (!client.IsReady) ended.TrySetResult();
        await ended.Task.WaitAsync(timeout.Token);
        if (client.IsReady) return 8;
        Console.WriteLine("CRASH_REVOKED");
    }
    else
    {
        await client.StopAsync(timeout.Token);
        if (client.IsReady) return 9;
        await using var next = await BrokerClient.ConnectAsync(servicePath, timeout.Token);
        if (await next.CheckReadyAsync(timeout.Token)) return 10;
        await next.StopAsync(timeout.Token);
        Console.WriteLine("STOP_CONFIRMED_AND_RECONNECTED");
    }
    return 0;
}
catch (Exception error)
{
    // This executable only uses the public synthetic fixture, never user profiles.
    Console.Error.WriteLine(error);
    return 1;
}

internal static class ServiceAccessProbe
{
    public static void Verify()
    {
        var services = Process.GetProcessesByName("Weave.Core.Service");
        try
        {
            if (services.Length != 1) throw new InvalidOperationException("Expected one isolated service process.");
            using var query = OpenProcess(0x1000, false, services[0].Id);
            if (query.IsInvalid) throw new InvalidOperationException("Ordinary client cannot query the service image.");
            // Terminate, inject, read/write memory, duplicate handles and change its DACL must all fail.
            foreach (var access in new[] { 0x1, 0x2, 0x8, 0x10, 0x20, 0x40, 0x40000 })
            {
                using var forbidden = OpenProcess(access, false, services[0].Id);
                if (!forbidden.IsInvalid || Marshal.GetLastWin32Error() != 5)
                    throw new InvalidOperationException("An ordinary client received unsafe service process access.");
            }
        }
        finally { foreach (var process in services) process.Dispose(); }
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern SafeProcessHandle OpenProcess(int access, [MarshalAs(UnmanagedType.Bool)] bool inherit, int processId);
}
