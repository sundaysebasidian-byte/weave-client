using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core.Tests;

public sealed class BrokerSessionTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task StopReplyRemainsAvailableUntilClientClosesOrServiceStops(bool serviceStopping)
    {
        using var request = new MemoryStream();
        await BrokerWire.WriteAsync<BrokerRequest>(request, new BrokerStop(1), CancellationToken.None);
        using var stream = new HeldStopReplyStream(request.ToArray());
        await using var session = new BrokerSession("unused", "not-executed", "not-used");
        using var stopping = new CancellationTokenSource();
        var running = BrokerConnection.RunAsync(stream, session, stopping.Token);
        try
        {
            Assert.False(running.IsCompleted, "Disconnecting now could discard the unread stop acknowledgement.");
            stream.Reply.Position = 0;
            var reply = await BrokerWire.ReadAsync<BrokerReply>(stream.Reply, CancellationToken.None);
            Assert.Equal(1, reply!.Id);
            Assert.False(reply.Ready);
            if (serviceStopping)
            {
                stopping.Cancel();
                await Assert.ThrowsAnyAsync<OperationCanceledException>(() => running);
            }
            else
            {
                stream.ClientClosed.TrySetResult(0);
                await running.WaitAsync(TimeSpan.FromSeconds(2));
            }
        }
        finally { stream.ClientClosed.TrySetResult(0); await running.ConfigureAwait(ConfigureAwaitOptions.SuppressThrowing); }
    }

    private sealed class HeldStopReplyStream(byte[] request) : MemoryStream(request)
    {
        public MemoryStream Reply { get; } = new();
        public TaskCompletionSource<int> ClientClosed { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default) =>
            Position < Length ? base.ReadAsync(buffer, cancellationToken) : new(ClientClosed.Task.WaitAsync(cancellationToken));
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default) =>
            Reply.WriteAsync(buffer, cancellationToken);
        protected override void Dispose(bool disposing) { if (disposing) Reply.Dispose(); base.Dispose(disposing); }
    }

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
