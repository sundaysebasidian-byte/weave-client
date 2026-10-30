using System.Net;
using System.Net.Sockets;
using System.Text;
using Weave.Windows.TestFixtures;

namespace Weave.Windows.Core.Tests;

public sealed class ServiceFixtureTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task TcpHealthCheckDoesNotKillTheSyntheticUpstream(bool resetProbe)
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        var server = SyntheticUpstream.ServeAsync(listener, timeout.Token);
        try
        {
            using (var health = new TcpClient())
            {
                await health.ConnectAsync(IPAddress.Loopback, port, timeout.Token);
                if (resetProbe) health.Client.LingerState = new LingerOption(true, 0);
            }
            using var request = new TcpClient();
            await request.ConnectAsync(IPAddress.Loopback, port, timeout.Token);
            await using var stream = request.GetStream();
            await stream.WriteAsync("CONNECT 198.51.100.42:80 HTTP/1.1\r\nHost: 198.51.100.42\r\n\r\n"u8.ToArray(), timeout.Token);
            using var reader = new StreamReader(stream, Encoding.ASCII, leaveOpen: true);
            Assert.Equal("HTTP/1.1 200 Connection established", await reader.ReadLineAsync(timeout.Token));
            Assert.Equal("", await reader.ReadLineAsync(timeout.Token));
            await stream.WriteAsync("GET /check HTTP/1.1\r\nHost: 198.51.100.42\r\n\r\n"u8.ToArray(), timeout.Token);
            Assert.EndsWith("weave-service-upstream", await reader.ReadToEndAsync(timeout.Token));
            Assert.StartsWith("CONNECT 198.51.100.42:80 ", await server);
        }
        finally
        {
            timeout.Cancel();
            listener.Stop();
            await ((Task)server).ConfigureAwait(ConfigureAwaitOptions.SuppressThrowing);
        }
    }

    [Theory]
    [InlineData("", true, SocketError.ConnectionReset, true)]
    [InlineData("", true, SocketError.ConnectionAborted, true)]
    [InlineData("C", true, SocketError.ConnectionReset, false)]
    [InlineData("", false, SocketError.ConnectionReset, false)]
    [InlineData("", true, SocketError.ConnectionRefused, false)]
    public async Task OnlyEmptyInitialResetIsAnIgnorableProbe(string prefix, bool allowEmptyProbe, SocketError code, bool ignored)
    {
        var failure = new IOException("Injected socket failure", new SocketException((int)code));
        using var stream = new FaultAfterPrefixStream(Encoding.ASCII.GetBytes(prefix), failure);
        var reading = SyntheticUpstream.ReadHeaderAsync(stream, CancellationToken.None, allowEmptyProbe);
        if (ignored) Assert.Equal("", await reading);
        else Assert.Same(failure, await Assert.ThrowsAsync<IOException>(() => reading));
    }

    [Theory]
    [InlineData("", false)]
    [InlineData("GET /check HTTP/1.1\r\n", false)]
    [InlineData("", true)]
    public async Task IncompleteTargetRequestCannotProduceSuccess(string partialRequest, bool reset)
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var server = SyntheticUpstream.ServeAsync(listener, timeout.Token);
        try
        {
            using (var request = new TcpClient())
            {
                await request.ConnectAsync(IPAddress.Loopback, ((IPEndPoint)listener.LocalEndpoint).Port, timeout.Token);
                var stream = request.GetStream();
                await stream.WriteAsync("CONNECT 198.51.100.42:80 HTTP/1.1\r\n\r\n"u8.ToArray(), timeout.Token);
                using var reader = new StreamReader(stream, Encoding.ASCII, leaveOpen: true);
                Assert.Equal("HTTP/1.1 200 Connection established", await reader.ReadLineAsync(timeout.Token));
                Assert.Equal("", await reader.ReadLineAsync(timeout.Token));
                await stream.WriteAsync(Encoding.ASCII.GetBytes(partialRequest), timeout.Token);
                if (reset) request.Client.LingerState = new LingerOption(true, 0);
            }
            await Assert.ThrowsAnyAsync<IOException>(() => server.WaitAsync(timeout.Token));
        }
        finally
        {
            timeout.Cancel();
            listener.Stop();
            await ((Task)server).ConfigureAwait(ConfigureAwaitOptions.SuppressThrowing);
        }
    }

    private sealed class FaultAfterPrefixStream(byte[] prefix, Exception failure) : MemoryStream(prefix, writable: false)
    {
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default) =>
            Position == Length ? ValueTask.FromException<int>(failure) : base.ReadAsync(buffer, cancellationToken);
    }
}
