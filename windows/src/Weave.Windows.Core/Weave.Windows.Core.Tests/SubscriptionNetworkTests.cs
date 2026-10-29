using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Buffers.Binary;
using YamlDotNet.RepresentationModel;

namespace Weave.Windows.Core.Tests;

public sealed class SubscriptionNetworkTests
{
    [Fact]
    public void SharedAddressPolicyMatchesEveryPlatformFixture()
    {
        foreach (var line in File.ReadLines(Path.Combine(AppContext.BaseDirectory, "public-addresses.tsv")))
        {
            if (line.Length == 0 || line.StartsWith('#')) continue;
            var fields = line.Split('\t');
            Assert.Equal(bool.Parse(fields[1]), PublicSubscriptionAddress.IsPublic(IPAddress.Parse(fields[0])));
        }
    }

    [Fact]
    public async Task ResolvedPrivateAddressCannotOpenSocket()
    {
        using var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var lookups = 0;
        using var importer = new SubscriptionImporter(resolveHost: (_, _) =>
        {
            lookups++;
            return Task.FromResult(new[] { IPAddress.Loopback });
        });
        await Assert.ThrowsAsync<HttpRequestException>(() => importer.ImportUrlAsync("fixture",
            $"https://fixture.invalid:{((IPEndPoint)listener.LocalEndpoint).Port}/"));
        Assert.Equal(1, lookups);
        Assert.False(listener.Pending());
    }

    [Fact]
    public async Task CoreDNSUsesAuthenticatedAPIAndKeepsOneWorkingAddressFamily()
    {
        using var controller = new MihomoController(Bundle(), new Reply(request =>
        {
            Assert.Equal("Bearer", request.Headers.Authorization?.Scheme);
            Assert.Equal("fixture-secret", request.Headers.Authorization?.Parameter);
            Assert.Equal("127.0.0.1", request.RequestUri!.Host);
            Assert.Contains("name=fixture.invalid", request.RequestUri.Query);
            return request.RequestUri.Query.Contains("type=AAAA")
                ? new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
                : Json("{\"Status\":0,\"Answer\":[{\"type\":5,\"data\":\"alias.invalid.\"},{\"type\":1,\"data\":\"8.8.8.8\"}]}");
        }));
        var addresses = await controller.ResolveHostAsync("fixture.invalid", CancellationToken.None);
        Assert.Equal(IPAddress.Parse("8.8.8.8"), Assert.Single(addresses));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => controller.ResolveHostAsync("fixture.invalid", new CancellationToken(true)));
    }

    [Theory]
    [InlineData("[]")]
    [InlineData("{\"Status\":\"invalid\"}")]
    [InlineData("{\"Status\":3}")]
    [InlineData("{\"Status\":0,\"Answer\":[{\"type\":1,\"data\":\"not-an-address.invalid\"}]}")]
    public async Task UnusableCoreDNSNeverReturnsAnAddress(string body)
    {
        using var controller = new MihomoController(Bundle(), new Reply(_ => Json(body)));
        await Assert.ThrowsAsync<HttpRequestException>(() => controller.ResolveHostAsync("fixture.invalid", CancellationToken.None));
    }

    [Fact]
    public async Task OversizedCoreResponseIsRejected()
    {
        using var controller = new MihomoController(Bundle(), new Reply(_ => Json(new string(' ', 65_537))));
        await Assert.ThrowsAsync<HttpRequestException>(() => controller.ResolveHostAsync("fixture.invalid", CancellationToken.None));
    }

    [Fact]
    public async Task ActualCoreDNSReturnsUpstreamAddressesInFakeIPMode()
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrEmpty(executable)) return;
        using var server = new UdpClient(new IPEndPoint(IPAddress.Loopback, 0));
        using var stop = new CancellationTokenSource();
        var answering = AnswerDNSAsync(server, stop.Token);
        var folder = Path.Combine(Path.GetTempPath(), "weave-dns-core-" + Guid.NewGuid().ToString("N"));
        try
        {
            using var importer = new SubscriptionImporter();
            var subscription = importer.ImportText("fixture", "inline://fixture",
                "proxies: [{name: fixture, type: socks5, server: 127.0.0.1, port: 9}]");
            var bundle = new MihomoConfigBuilder().Build([subscription], [], subscription.Id, subscription.Nodes[0].Id,
                new WindowsNetworkOptions { EnableTun = false, Ipv6Enabled = true }, folder);
            var yaml = new YamlStream();
            using (var input = File.OpenText(bundle.ConfigPath)) yaml.Load(input);
            var root = (YamlMappingNode)yaml.Documents[0].RootNode;
            var dns = (YamlMappingNode)root.Children[new YamlScalarNode("dns")];
            var endpoint = $"127.0.0.1:{((IPEndPoint)server.Client.LocalEndPoint!).Port}";
            foreach (var key in new[] { "default-nameserver", "nameserver", "proxy-server-nameserver" })
                dns.Children[new YamlScalarNode(key)] = new YamlSequenceNode(new YamlScalarNode(endpoint));
            using (var output = File.CreateText(bundle.ConfigPath)) yaml.Save(output, assignAnchors: false);
            await using var process = new MihomoProcess(executable);
            await process.StartAsync(bundle);
            var addresses = await process.ResolveHostAsync("resolved.fixture.invalid", CancellationToken.None);
            Assert.Equal(new[] { "2606:4700:4700::1111", "8.8.8.8" }, addresses.Select(ip => ip.ToString()).Order().ToArray());
            await process.StopAsync();
            await Assert.ThrowsAsync<HttpRequestException>(() => process.ResolveHostAsync("resolved.fixture.invalid", CancellationToken.None));
        }
        finally
        {
            stop.Cancel();
            await answering;
            await NativeSessionCleanup.DeleteAsync(folder);
        }
    }

    private static async Task AnswerDNSAsync(UdpClient server, CancellationToken token)
    {
        try
        {
            while (true)
            {
                var packet = await server.ReceiveAsync(token);
                var query = packet.Buffer;
                var end = 12;
                while (end < query.Length && query[end] != 0) end += query[end] + 1;
                if (end + 5 > query.Length) continue;
                var kind = BinaryPrimitives.ReadUInt16BigEndian(query.AsSpan(end + 1));
                if (kind is not (1 or 28)) continue;
                var address = IPAddress.Parse(kind == 1 ? "8.8.8.8" : "2606:4700:4700::1111").GetAddressBytes();
                var reply = new byte[end + 5 + 12 + address.Length];
                query.AsSpan(0, end + 5).CopyTo(reply);
                BinaryPrimitives.WriteUInt16BigEndian(reply.AsSpan(2), 0x8180);
                BinaryPrimitives.WriteUInt16BigEndian(reply.AsSpan(6), 1);
                reply.AsSpan(8, 4).Clear();
                var offset = end + 5;
                BinaryPrimitives.WriteUInt16BigEndian(reply.AsSpan(offset), 0xc00c);
                BinaryPrimitives.WriteUInt16BigEndian(reply.AsSpan(offset + 2), kind);
                BinaryPrimitives.WriteUInt16BigEndian(reply.AsSpan(offset + 4), 1);
                BinaryPrimitives.WriteUInt32BigEndian(reply.AsSpan(offset + 6), 60);
                BinaryPrimitives.WriteUInt16BigEndian(reply.AsSpan(offset + 10), (ushort)address.Length);
                address.CopyTo(reply, offset + 12);
                await server.SendAsync(reply, packet.RemoteEndPoint, token);
            }
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested) { }
    }

    private static RuntimeBundle Bundle() => new()
    {
        Directory = "unused", ConfigPath = "unused", MixedPort = 42, ControllerPort = 43, ControllerSecret = "fixture-secret",
    };
    private static HttpResponseMessage Json(string body) => new(HttpStatusCode.OK)
    {
        Content = new StringContent(body, Encoding.UTF8, "application/json"),
    };
    private sealed class Reply(Func<HttpRequestMessage, HttpResponseMessage> response) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(response(request));
    }
}
