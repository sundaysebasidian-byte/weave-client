using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core.Tests;

public sealed class BrokerClientTests
{
    [Fact]
    public async Task ConcurrentRepliesAreMatchedByIdAndQueryFailuresDoNotCloseTheLease()
    {
        await using var pair = await Pair.CreateAsync();
        var ready = pair.Client.CheckReadyAsync(pair.Token);
        var traffic = pair.Client.ReadTrafficAsync(pair.Token);
        var dns = pair.Client.ResolveHostAsync("example.com", pair.Token);
        var requests = new[] { await pair.ReadAsync(), await pair.ReadAsync(), await pair.ReadAsync() };
        foreach (var request in requests.Reverse())
            await pair.ReplyAsync(request switch
            {
                BrokerStatus => new(request.Id, Ready: true),
                BrokerTraffic => new(request.Id, Down: 42, Up: 17),
                _ => new(request.Id, Error: "query-failed"),
            });
        Assert.True(await ready);
        Assert.Equal((42L, 17L), await traffic);
        Assert.Equal("query-failed", (await Assert.ThrowsAsync<BrokerRequestException>(() => dns)).Code);
        var next = pair.Client.ResolveHostAsync("example.com", pair.Token);
        var resolve = Assert.IsType<BrokerResolve>(await pair.ReadAsync());
        await pair.ReplyAsync(new(resolve.Id, Addresses: ["1.1.1.1", "2606:4700:4700::1111"]));
        Assert.Equal(2, (await next).Length);
    }

    [Fact]
    public async Task CancelledProbeIsDrainedWithoutDisconnectingOtherRequests()
    {
        await using var pair = await Pair.CreateAsync();
        using var cancellation = new CancellationTokenSource();
        var probe = pair.Client.ProbeNodeAsync("fixture", cancellation.Token);
        var request = await pair.ReadAsync();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => probe);
        var ready = pair.Client.CheckReadyAsync(pair.Token);
        var status = await pair.ReadAsync();
        await pair.ReplyAsync(new(status.Id, Ready: true));
        await pair.ReplyAsync(new(request.Id, Delay: 19));
        Assert.True(await ready);
        var next = pair.Client.CheckReadyAsync(pair.Token);
        await pair.ReplyAsync(new((await pair.ReadAsync()).Id, Ready: true));
        Assert.True(await next);
    }

    [Fact]
    public async Task StopHasAReservedSlotAndConcurrentStopsWaitForTheSameAcknowledgement()
    {
        await using var pair = await Pair.CreateAsync();
        var pending = Enumerable.Range(0, 3).Select(_ => pair.Client.ReadTrafficAsync(pair.Token)).ToArray();
        for (var i = 0; i < 3; i++) Assert.IsType<BrokerTraffic>(await pair.ReadAsync());
        using var queuedCancellation = new CancellationTokenSource();
        var queued = pair.Client.CheckReadyAsync(queuedCancellation.Token);
        Assert.False(queued.IsCompleted);
        queuedCancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => queued);
        var stop = pair.Client.StopAsync(pair.Token);
        var alsoStop = pair.Client.StopAsync(pair.Token);
        var request = Assert.IsType<BrokerStop>(await pair.ReadAsync());
        Assert.False(stop.IsCompleted);
        Assert.False(alsoStop.IsCompleted);
        await pair.ReplyAsync(new(request.Id, Ready: false));
        await Task.WhenAll(stop, alsoStop);
        foreach (var query in pending) await Assert.ThrowsAsync<IOException>(() => query);
        Assert.Null(await BrokerWire.ReadAsync<BrokerRequest>(pair.Server.GetStream(), pair.Token));
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task UnknownResponseOrPeerEofFailsPendingWorkAndNotifiesExactlyOnce(bool eof)
    {
        await using var pair = await Pair.CreateAsync();
        var ended = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var events = 0;
        pair.Client.Exited += (_, _) => { Interlocked.Increment(ref events); ended.TrySetResult(); };
        var query = pair.Client.CheckReadyAsync(pair.Token);
        var request = await pair.ReadAsync();
        if (eof) pair.Server.Dispose();
        else await pair.ReplyAsync(new(request.Id + 100, Error: "secret-must-not-be-echoed"));
        var error = await Assert.ThrowsAsync<IOException>(() => query);
        await ended.Task.WaitAsync(pair.Token);
        await pair.Client.DisposeAsync();
        Assert.Equal(1, events);
        Assert.DoesNotContain("secret", error.Message);
        Assert.False(pair.Client.IsReady);
    }

    [Fact]
    public async Task MalformedDnsResponseClosesConnectionWithoutReturningPartialAddresses()
    {
        await using var pair = await Pair.CreateAsync();
        var dns = pair.Client.ResolveHostAsync("example.com", pair.Token);
        var request = await pair.ReadAsync();
        await pair.ReplyAsync(new(request.Id, Addresses: ["1.1.1.1", "not-an-ip"]));
        await Assert.ThrowsAsync<InvalidDataException>(() => dns);
        Assert.Null(await BrokerWire.ReadAsync<BrokerRequest>(pair.Server.GetStream(), pair.Token));
    }

    [Fact]
    public async Task CancellingStartRevokesTheConnectionInsteadOfLeavingTheCoreStarting()
    {
        await using var pair = await Pair.CreateAsync();
        using var cancellation = new CancellationTokenSource();
        var start = pair.Client.StartAsync(Profile(), cancellation.Token);
        Assert.IsType<BrokerStart>(await pair.ReadAsync());
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => start);
        Assert.Null(await BrokerWire.ReadAsync<BrokerRequest>(pair.Server.GetStream(), pair.Token));
        Assert.False(pair.Client.IsReady);
    }

    [Fact]
    public async Task SuccessfulStartBecomesNotReadyWhenTheServiceExitsWhileIdle()
    {
        await using var pair = await Pair.CreateAsync();
        var start = pair.Client.StartAsync(Profile(), pair.Token);
        var request = await pair.ReadAsync();
        await pair.ReplyAsync(new(request.Id, Runtime: new(12345, false, new Dictionary<string, int>()), Ready: true));
        Assert.Equal(12345, (await start).MixedPort);
        Assert.True(pair.Client.IsReady);
        var ended = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        pair.Client.Exited += (_, _) => ended.TrySetResult();
        pair.Server.Dispose();
        await ended.Task.WaitAsync(pair.Token);
        Assert.False(pair.Client.IsReady);
    }

    private static BrokerProfile Profile() => new([], [], null, null,
        new(RoutingMode.Direct, false, false, false, DnsProfile.Privacy, null, "", null, null));

    private sealed class Pair(TcpClient socket, TcpClient server) : IAsyncDisposable
    {
        private readonly CancellationTokenSource _timeout = new(TimeSpan.FromSeconds(15));
        public CancellationToken Token => _timeout.Token;
        public BrokerClient Client { get; } = new(socket.GetStream());
        public TcpClient Server { get; } = server;
        public async Task<BrokerRequest> ReadAsync() => Assert.IsAssignableFrom<BrokerRequest>(
            await BrokerWire.ReadAsync<BrokerRequest>(Server.GetStream(), Token));
        public Task ReplyAsync(BrokerReply reply) => BrokerWire.WriteAsync(Server.GetStream(), reply, Token);
        public static async Task<Pair> CreateAsync()
        {
            using var listener = new TcpListener(IPAddress.Loopback, 0);
            listener.Start();
            var accept = listener.AcceptTcpClientAsync();
            var client = new TcpClient();
            await client.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port);
            return new(client, await accept);
        }
        public async ValueTask DisposeAsync()
        {
            await Client.DisposeAsync().AsTask().WaitAsync(TimeSpan.FromSeconds(5));
            socket.Dispose();
            Server.Dispose();
            _timeout.Dispose();
        }
    }
}
