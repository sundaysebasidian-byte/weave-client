using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core.Tests;

public sealed class BrokerSessionTests
{
    [BrokerProbeTheory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task RevokingConnectionDuringValidationStopsOnlyItsChildAndCleansItsSession(bool explicitStop)
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_BROKER_PROBE")!;
        var root = Path.Combine(Path.GetTempPath(), "weave-broker-session-" + Guid.NewGuid().ToString("N"));
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(15));
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        using var client = new TcpClient();
        var accept = listener.AcceptTcpClientAsync(timeout.Token).AsTask();
        await client.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port, timeout.Token);
        using var server = await accept;
        await using var broker = explicitStop ? new BrokerClient(client.GetStream()) : null;
        await using var session = new BrokerSession(root, executable, "not-used");
        var running = BrokerConnection.RunAsync(server.GetStream(), session, timeout.Token);
        try
        {
            var profile = new BrokerProfile([], [], null, null,
                new(RoutingMode.Direct, false, false, false, DnsProfile.Privacy, null, "", null, null));
            Task<BrokerRuntimeInfo>? start = null;
            if (broker is not null) start = broker.StartAsync(profile, timeout.Token);
            else await BrokerWire.WriteAsync<BrokerRequest>(client.GetStream(), new BrokerStart(1, profile), timeout.Token);
            var marker = Path.Combine(root, "probe.pid");
            while (!File.Exists(marker))
            {
                Assert.False(running.IsCompleted, "The connection exited before starting the probe.");
                await Task.Delay(25, timeout.Token);
            }
            var pid = int.Parse(await File.ReadAllTextAsync(marker, timeout.Token));
            using var child = Process.GetProcessById(pid);
            Assert.False(child.HasExited);
            if (broker is not null)
            {
                await broker.StopAsync(timeout.Token);
                await Assert.ThrowsAsync<IOException>(() => start!);
                Assert.True(child.HasExited);
                Assert.Empty(Directory.EnumerateDirectories(root));
                Assert.Empty(Directory.EnumerateFiles(root, "*.session"));
            }
            else client.Dispose();
            await running.WaitAsync(TimeSpan.FromSeconds(5));
            await child.WaitForExitAsync(timeout.Token);
            Assert.False(session.IsReady);
            Assert.False(session.CleanupPending);
            Assert.Empty(Directory.EnumerateDirectories(root));
            Assert.Empty(Directory.EnumerateFiles(root, "*.session"));
            Assert.Equal([marker], Directory.GetFiles(root));
        }
        finally
        {
            client.Dispose();
            timeout.Cancel();
            try { await running; } catch (OperationCanceledException) { }
            await session.DisposeAsync();
            if (Directory.Exists(root)) Directory.Delete(root, true);
        }
    }

    [Fact]
    public async Task SessionRejectsQueriesOutsideItsOwnedNodeSetAndInvalidRequestIds()
    {
        await using var session = new BrokerSession("unused", "not-executed", "not-used");
        await Assert.ThrowsAsync<InvalidDataException>(() => session.HandleAsync(new BrokerProbe(1, "another-session-node"), CancellationToken.None));
        await Assert.ThrowsAsync<InvalidDataException>(() => session.HandleAsync(new BrokerResolve(1, "https://arbitrary/path"), CancellationToken.None));
        await Assert.ThrowsAsync<InvalidDataException>(() => session.HandleAsync(new BrokerStatus(0), CancellationToken.None));
        Assert.False((await session.HandleAsync(new BrokerStatus(1), CancellationToken.None)).Ready);
        await session.DisposeAsync();
        await Assert.ThrowsAsync<ObjectDisposedException>(() => session.HandleAsync(new BrokerStatus(2), CancellationToken.None));
    }

    private sealed class BrokerProbeTheoryAttribute : TheoryAttribute
    {
        public BrokerProbeTheoryAttribute()
        {
            if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("WEAVE_TEST_BROKER_PROBE")))
                Skip = "Requires the task-owned native broker validation probe.";
        }
    }
}
