using System.Net;
using System.Net.Sockets;
using System.Text;
using YamlDotNet.RepresentationModel;

namespace Weave.Windows.Core.Tests;

public sealed class DataPathTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task ImportedHttpNodeActuallyForwardsTrafficWithAuthentication(bool automatic)
    {
        var core = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrEmpty(core)) return;
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(35));
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        var received = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        var server = ServeProxyAsync(listener, received, timeout.Token);
        using var importer = new SubscriptionImporter();
        var subscription = importer.ImportText("wire-test", "inline://wire-test",
            $"proxies:\n- {{name: '東京 HTTP', type: http, server: 127.0.0.1, port: {port}, username: test-user, password: test-pass}}\n");
        var folder = Path.Combine(Path.GetTempPath(), "weave-wire-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build([subscription], [], subscription.Id,
                automatic ? null : subscription.Nodes[0].Id,
                new WindowsNetworkOptions { EnableTun = false, RoutingMode = RoutingMode.Global }, folder);
            await using var process = new MihomoProcess(core);
            Assert.True((await process.ValidateConfigAsync(bundle, timeout.Token)).IsValid);
            await process.StartAsync(bundle, timeout.Token);
            using var handler = new SocketsHttpHandler { UseProxy = true, Proxy = new WebProxy($"http://127.0.0.1:{bundle.MixedPort}") };
            using var client = new HttpClient(handler);
            // .invalid cannot resolve or succeed through a direct fallback. Only the
            // imported upstream test proxy can provide this unique body.
            var body = await client.GetStringAsync("http://weave-data-path.invalid/check", timeout.Token);
            Assert.Equal("weave-upstream-reached", body);
            var request = await received.Task.WaitAsync(timeout.Token);
            Assert.Contains("weave-data-path.invalid:80", request);
            Assert.Contains("Proxy-Authorization: Basic " + Convert.ToBase64String(Encoding.ASCII.GetBytes("test-user:test-pass")), request, StringComparison.OrdinalIgnoreCase);
        }
        finally
        {
            timeout.Cancel(); listener.Stop();
            await server;
            await NativeSessionCleanup.DeleteAsync(folder);
        }
    }

    private static async Task ServeProxyAsync(TcpListener listener, TaskCompletionSource<string> received, CancellationToken token)
    {
        // Mihomo's HTTP outbound uses CONNECT even for an HTTP destination.
        // Handle health checks too; only capture the explicit .invalid request.
        try
        {
            while (!token.IsCancellationRequested)
            {
                using var socket = await listener.AcceptTcpClientAsync(token);
                await using var stream = socket.GetStream();
                var header = await ReadHeaderAsync(stream, token);
                if (!header.Contains("weave-data-path.invalid", StringComparison.Ordinal)) continue;
                received.TrySetResult(header);
                if (header.StartsWith("CONNECT ", StringComparison.Ordinal))
                {
                    await stream.WriteAsync(Encoding.ASCII.GetBytes("HTTP/1.1 200 Connection established\r\n\r\n"), token);
                    await ReadHeaderAsync(stream, token);
                }
                var body = "weave-upstream-reached";
                await stream.WriteAsync(Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n{body}"), token);
            }
        }
        catch (Exception error) when (token.IsCancellationRequested && error is OperationCanceledException or SocketException or IOException) { }
    }

    private static async Task<string> ReadHeaderAsync(Stream stream, CancellationToken token)
    {
        var buffer = new byte[8192]; var length = 0;
        while (length < buffer.Length)
        {
            if (await stream.ReadAsync(buffer.AsMemory(length, 1), token) == 0) break;
            length++;
            if (length >= 4 && buffer[length - 4] == 13 && buffer[length - 3] == 10 && buffer[length - 2] == 13 && buffer[length - 1] == 10) break;
        }
        return Encoding.ASCII.GetString(buffer, 0, length);
    }

    [Theory]
    [InlineData(DnsProfile.AdBlock)]
    [InlineData(DnsProfile.Family)]
    public void FilteringCannotBlockNodeBootstrap(DnsProfile profile)
    {
        var folder = Path.Combine(Path.GetTempPath(), "weave-dns-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build([], [], "", null,
                new WindowsNetworkOptions { DnsProfile = profile, RoutingMode = RoutingMode.Direct }, folder);
            var yaml = new YamlStream(); yaml.Load(new StringReader(File.ReadAllText(bundle.ConfigPath)));
            var root = (YamlMappingNode)yaml.Documents[0].RootNode;
            var dns = (YamlMappingNode)root.Children[new YamlScalarNode("dns")];
            Assert.NotEqual(dns.Children[new YamlScalarNode("nameserver")].ToString(), dns.Children[new YamlScalarNode("proxy-server-nameserver")].ToString());
            Assert.Equal("true", ((YamlScalarNode)dns.Children[new YamlScalarNode("respect-rules")]).Value);
            Assert.Contains("mtu: 1500", File.ReadAllText(bundle.ConfigPath));
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }

    [Theory]
    [InlineData(204, true)]
    [InlineData(302, false)]
    [InlineData(403, false)]
    [InlineData(502, false)]
    public async Task FailedProxyResponsesNeverConfirmConnectivity(int status, bool expected)
    {
        using var client = new HttpClient(new ReplyHandler(status));
        Assert.Equal(expected, await ConnectionHealth.AnyReachableAsync(client, ["https://example.com/"], CancellationToken.None));
    }
    private sealed class ReplyHandler(int status) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage((HttpStatusCode)status));
    }
}
