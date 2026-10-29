using System.Net;
using System.Net.Sockets;
using System.Text;
using Weave.Windows.TestFixtures;

namespace Weave.Windows.Core.Tests;

public sealed class ServiceFixtureTests
{
    [Fact]
    public async Task TcpHealthCheckDoesNotKillTheSyntheticUpstream()
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        var server = SyntheticUpstream.ServeAsync(listener, timeout.Token);
        try
        {
            using (var health = new TcpClient()) await health.ConnectAsync(IPAddress.Loopback, port, timeout.Token);
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
            try { await server; }
            catch (OperationCanceledException) { }
        }
    }
}
