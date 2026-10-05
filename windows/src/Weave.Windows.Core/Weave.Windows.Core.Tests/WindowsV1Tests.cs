using System.Net;
using System.Text;
using System.Text.Json;

namespace Weave.Windows.Core.Tests;

public sealed class WindowsV1Tests
{
    private const string Proxy = "proxies:\n- {name: test, type: http, server: example.com, port: 443, tls: true}\n";

    [Fact]
    public async Task BundledCoreLoadsDuplicateNamesAcrossSubscriptionsAndStopsCleanly()
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrEmpty(executable)) return;
        using var importer = new SubscriptionImporter();
        var payload = Proxy.Replace("example.com", "127.0.0.1");
        var one = importer.ImportText("one", "inline://one", payload);
        var two = importer.ImportText("two", "inline://two", payload);
        var folder = Path.Combine(Path.GetTempPath(), "weave-live-core-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build(new[] { one, two },
                new[] { new WindowsAppRoute { ProcessName = "chrome.exe", DisplayName = "Chrome", Target = RouteTarget.Fixed(two.Id, two.Nodes[0].Id) } },
                one.Id, one.Nodes[0].Id, new WindowsNetworkOptions { EnableTun = false }, folder);
            await using var process = new MihomoProcess(executable);
            var validation = await process.ValidateConfigAsync(bundle);
            Assert.True(validation.IsValid, validation.Diagnostics);
            await process.StartAsync(bundle);
            Assert.True(process.IsReady);
            using var controller = new MihomoController(bundle);
            Assert.True(await controller.IsReadyAsync(bundle, CancellationToken.None));
            await process.StopAsync();
            Assert.False(process.IsRunning);
            Assert.False(process.IsReady);
        }
        finally { await NativeSessionCleanup.DeleteAsync(folder); }
    }

    [Fact]
    public void UnindentedSequenceRetainsAll65Nodes()
    {
        var text = "proxies:\n" + string.Join("\n", Enumerable.Range(0, 65).Select(i =>
            $"- {{name: node-{i}, type: {(i % 2 == 0 ? "http" : "hysteria2")}, server: example.com, port: 443, password: test}}"));
        using var importer = new SubscriptionImporter();
        var first = importer.ImportText("test", "https://example.com/sub", text);
        var second = importer.ImportText("test", "https://example.com/sub", first.ProviderYaml);
        Assert.Equal(65, first.Nodes.Count);
        Assert.Equal(65, second.Nodes.Count);
        Assert.Equal(first.Nodes.Select(n => n.RawName), second.Nodes.Select(n => n.RawName));
    }

    [Fact]
    public void JsonAndBase64RetainNodes()
    {
        var json = JsonSerializer.Serialize(new { proxies = new[] { new { name = "http", type = "http", server = "example.com", port = 443, tls = true } } });
        using var importer = new SubscriptionImporter();
        Assert.Single(importer.ImportText("test", "inline", json).Nodes);
        Assert.Single(importer.ImportText("test", "inline", Convert.ToBase64String(Encoding.UTF8.GetBytes(json))).Nodes);
    }

    [Fact]
    public void AliasMergeKeepsProtocolAndCredentials()
    {
        using var importer = new SubscriptionImporter();
        var parsed = importer.ImportText("test", "inline", "base: &b {type: http, server: example.com, port: 443, username: user, password: test}\nproxies:\n- <<: *b\n  name: first\n- <<: *b\n  name: second");
        Assert.Equal(2, parsed.Nodes.Count);
        Assert.All(parsed.Nodes, n => Assert.Equal("http", n.Protocol));
        Assert.Contains("password: test", parsed.ProviderYaml);
        Assert.DoesNotContain("*b", parsed.ProviderYaml);
    }

    [Fact]
    public void NodeIdsDoNotChangeWithOrdering()
    {
        using var importer = new SubscriptionImporter();
        var a = importer.ImportText("test", "https://example.com/sub", Proxy);
        var b = importer.ImportText("test", "https://example.com/sub", Proxy.Replace("proxies:\n", "proxies:\n- {name: new, type: socks5, server: example.com, port: 80}\n"));
        Assert.Equal(a.Id, b.Id);
        Assert.Equal(a.Nodes[0].Id, b.Nodes[1].Id);
    }

    [Theory]
    [InlineData("proxies:\n- {name: no-type}")]
    [InlineData("proxies: [oops]")]
    [InlineData("proxies: []")]
    [InlineData("proxies: [{name: duplicate, type: http}, {name: duplicate, type: ss}]")]
    [InlineData("a: &a {loop: *a}\nproxies: []")]
    public void BadDocumentsAreNotPartiallyImported(string text)
    {
        using var importer = new SubscriptionImporter();
        Assert.Throws<InvalidDataException>(() => importer.ImportText("test", "inline", text));
    }

    [Fact]
    public async Task RedirectAndProviderPreserveOriginalSource()
    {
        using var client = new HttpClient(new ReplyHandler(request =>
        {
            if (request.RequestUri!.AbsolutePath == "/sub") return new HttpResponseMessage(HttpStatusCode.Found) { Headers = { Location = new Uri("/redirected", UriKind.Relative) } };
            return new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(request.RequestUri.AbsolutePath == "/redirected"
                ? "proxy-providers:\n  remote: {type: http, url: 'https://example.com/nodes'}" : Proxy) };
        }));
        using var importer = new SubscriptionImporter(client);
        var record = await importer.ImportUrlAsync("test", "https://example.com/sub");
        Assert.Equal("https://example.com/sub", record.Source);
        Assert.Single(record.Nodes);
    }

    [Theory]
    [InlineData("{\"mixed-port\":42,\"tun\":{\"enable\":true}}", true)]
    [InlineData("{\"mixed-port\":43,\"tun\":{\"enable\":true}}", false)]
    [InlineData("{\"mixed-port\":\"42\",\"tun\":{\"enable\":true}}", false)]
    [InlineData("{\"mixed-port\":42,\"tun\":{\"enable\":false}}", false)]
    [InlineData("[]", false)]
    public async Task ReadinessChecksSessionPortAndTun(string json, bool expected)
    {
        var bundle = new RuntimeBundle { Directory = "unused", ConfigPath = "unused", MixedPort = 42, ControllerPort = 43, ControllerSecret = "secret", RequiresTun = true };
        using var controller = new MihomoController(bundle, new ReplyHandler(request =>
        {
            Assert.Equal("Bearer secret", request.Headers.Authorization!.ToString());
            return new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(json) };
        }));
        Assert.Equal(expected, await controller.IsReadyAsync(bundle, CancellationToken.None));
    }

    [Fact]
    public void SessionConfigUsesPrivateControllerAndNeverDefaultsToDirect()
    {
        using var importer = new SubscriptionImporter();
        var record = importer.ImportText("test", "inline", Proxy);
        var folder = Path.Combine(Path.GetTempPath(), "weave-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build(new[] { record }, Array.Empty<WindowsAppRoute>(), record.Id,
                record.Nodes[0].Id, new WindowsNetworkOptions { BlockUdpStun = true }, folder);
            var text = File.ReadAllText(bundle.ConfigPath);
            Assert.NotEqual(bundle.MixedPort, bundle.ControllerPort);
            Assert.Equal(64, bundle.ControllerSecret.Length);
            Assert.Contains($"external-controller: 127.0.0.1:{bundle.ControllerPort}", text);
            Assert.Contains("MATCH,DEFAULT", text);
            Assert.DoesNotContain("      - DIRECT", text);
            Assert.Contains("additional-prefix:", text);
            Assert.Throws<InvalidDataException>(() => new MihomoConfigBuilder().Build(new[] { record }, Array.Empty<WindowsAppRoute>(), record.Id, null, new(), folder));
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }

    [Fact]
    public void EightPalettesAndNineDiagnosticTargets()
    {
        Assert.Equal(8, AppearancePalette.All.Count);
        Assert.Equal(8, AppearancePalette.All.Select(p => p.Name).Distinct().Count());
        Assert.Equal(9, NetworkDiagnostics.Targets.Count);
        Assert.All(NetworkDiagnostics.Targets, target => Assert.StartsWith("https://", target.Url));
    }

    private sealed class ReplyHandler(Func<HttpRequestMessage, HttpResponseMessage> reply) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(reply(request));
    }
}
