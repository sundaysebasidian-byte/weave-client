using System.Net;
using System.Text;
using Xunit;

namespace Weave.Windows.Core.Tests;

public sealed class CoreTests
{
    [Fact]
    public void NodeNameRemovesDecorativePrefixButKeepsCoreName()
    {
        Assert.Equal("de-n1 (0.3x)", NodeName.Core("🇩🇪 de-n1 (0.3x)"));
        Assert.Equal("de-n1 (0.3x)", NodeName.Core(@"\uD83C\uDDE9\uD83C\uDDEA de-n1 (0.3x)"));
    }

    [Fact]
    public void ProcessRuleCompilerRejectsMissingNode()
    {
        var subscription = new SubscriptionRecord
        {
            Id = "sub1",
            Name = "main",
            Source = "inline://test",
            Payload = "proxies: []",
            ProviderYaml = "proxies: []",
            Nodes = new List<ProxyNode>
            {
                new() { Id = "node1", Name = "de-n1", RawName = "de-n1", Protocol = "vless" },
            },
        };

        var route = new WindowsAppRoute
        {
            ProcessName = "chrome.exe",
            DisplayName = "Chrome",
            Target = RouteTarget.Fixed("sub1", "missing"),
        };

        Assert.Throws<InvalidDataException>(() => ProcessRuleCompiler.Compile(
            new[] { route },
            new Dictionary<string, SubscriptionRecord> { ["sub1"] = subscription }));
    }

    [Fact]
    public void ImporterKeepsOnlyProxyEntries()
    {
        var payload = """
            mixed-port: 7890
            external-controller: 0.0.0.0:9090
            proxies:
              - name: "🇩🇪 de-n1 (0.3x)"
                type: ss
                server: example.com
                port: 443
                cipher: aes-128-gcm
                password: secret
            """;

        var record = new SubscriptionImporter().ImportText("main", "inline://test", payload);
        Assert.Single(record.Nodes);
        Assert.Equal("de-n1 (0.3x)", record.Nodes[0].Name);
        Assert.DoesNotContain("external-controller", record.ProviderYaml, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("proxies:", record.ProviderYaml, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void NodeIdsAndSubscriptionIdSurviveNodeReordering()
    {
        var importer = new SubscriptionImporter();
        var before = importer.ImportText("main", "https://example.com/sub", Payload("one", "two"));
        var after = importer.ImportText("main", "https://example.com/sub", Payload("two", "one"));

        Assert.Equal(before.Id, after.Id);
        foreach (var node in before.Nodes)
        {
            Assert.Equal(node.Id, after.Nodes.Single(item => item.RawName == node.RawName).Id);
        }
    }

    [Fact]
    public void RefreshPreservesLegacyIdsAndFixedRoute()
    {
        var importer = new SubscriptionImporter();
        var old = importer.ImportText("main", "https://example.com/sub", Payload("one", "two"));
        var current = new SubscriptionRecord
        {
            Id = "legacy-subscription-id",
            Name = old.Name,
            Source = old.Source,
            Payload = old.Payload,
            ProviderYaml = old.ProviderYaml,
            Nodes = old.Nodes.Select(node => new ProxyNode
            {
                Id = $"legacy-{node.RawName}",
                Name = node.Name,
                RawName = node.RawName,
                Protocol = node.Protocol,
                Index = node.Index,
            }).ToList(),
        };
        var route = new WindowsAppRoute
        {
            ProcessName = "browser.exe",
            DisplayName = "Browser",
            Target = RouteTarget.Fixed(current.Id, "legacy-one"),
        };

        var candidate = importer.ImportText("main", current.Source, Payload("two", "one", "three"));
        var update = SubscriptionUpdateGuard.Prepare(current, candidate, new[] { route });

        Assert.Equal(current.Id, update.Record.Id);
        Assert.Equal("legacy-one", update.Record.Nodes.Single(node => node.RawName == "one").Id);
        Assert.Equal((1, 0, 2), (update.AddedNodes, update.RemovedNodes, update.RetainedNodes));
    }

    [Fact]
    public void RefreshRejectsMissingFixedTargetAndLargeDrop()
    {
        var importer = new SubscriptionImporter();
        var current = importer.ImportText("main", "https://example.com/sub", Payload("one", "two"));
        var route = new WindowsAppRoute
        {
            ProcessName = "browser.exe",
            DisplayName = "Browser",
            Target = RouteTarget.Fixed(current.Id, current.Nodes[0].Id),
        };
        var missingTarget = importer.ImportText("main", current.Source, Payload("two"));
        Assert.Throws<InvalidDataException>(() =>
            SubscriptionUpdateGuard.Prepare(current, missingTarget, new[] { route }));

        var larger = importer.ImportText("main", current.Source, Payload("one", "two", "three", "four", "five"));
        Assert.Throws<InvalidDataException>(() =>
            SubscriptionUpdateGuard.Prepare(larger, missingTarget, Array.Empty<WindowsAppRoute>()));
    }

    [Fact]
    public void RuntimeConfigUsesAuthenticatedLoopbackController()
    {
        var subscription = new SubscriptionImporter().ImportText(
            "main", "https://example.com/sub", Payload("one"));
        var runtime = Path.Combine(Path.GetTempPath(), $"weave-test-{Guid.NewGuid():N}");
        try
        {
            var bundle = new MihomoConfigBuilder().Build(
                new[] { subscription }, Array.Empty<WindowsAppRoute>(), subscription.Id, null,
                new WindowsNetworkOptions(), runtime);
            var yaml = File.ReadAllText(bundle.ConfigPath);
            Assert.Contains($"external-controller: 127.0.0.1:{bundle.ControlPort}", yaml);
            Assert.Contains($"secret: '{bundle.ControlSecret}'", yaml);
            Assert.Contains($"mixed-port: {bundle.MixedPort}", yaml);
            Assert.NotEqual(bundle.ControlPort, bundle.MixedPort);
        }
        finally
        {
            if (Directory.Exists(runtime))
            {
                Directory.Delete(runtime, recursive: true);
            }
        }
    }

    [Theory]
    [InlineData("http://example.com/dns-query")]
    [InlineData("https://user:pass@example.com/dns-query")]
    [InlineData("tls://example.com#fragment")]
    public void CustomDnsRejectsUnsupportedEndpoints(string endpoint)
    {
        Assert.Throws<InvalidDataException>(() => MihomoConfigBuilder.ValidateOptions(
            new WindowsNetworkOptions
            {
                DnsProfile = DnsProfile.Custom,
                CustomDnsEndpoint = endpoint,
            }));
    }

    [Fact]
    public async Task UrlImportKeepsOriginalSourceAfterHttpsRedirect()
    {
        var handler = new RedirectHandler();
        var importer = new SubscriptionImporter(new HttpClient(handler));

        var record = await importer.ImportUrlAsync("main", "https://1.1.1.1/sub");

        Assert.Equal("https://1.1.1.1/sub", record.Source);
        Assert.Equal(2, handler.RequestCount);
    }

    [Fact]
    public async Task UrlImportRejectsLoopbackBeforeRequest()
    {
        var handler = new RedirectHandler();
        var importer = new SubscriptionImporter(new HttpClient(handler));

        await Assert.ThrowsAsync<InvalidDataException>(() =>
            importer.ImportUrlAsync("main", "https://127.0.0.1/sub"));
        Assert.Equal(0, handler.RequestCount);
    }

    private static string Payload(params string[] names) =>
        "proxies:\n" + string.Concat(names.Select(name =>
            $"  - name: {name}\n    type: ss\n    server: {name}.example.com\n    port: 443\n    cipher: aes-128-gcm\n    password: secret\n"));

    private sealed class RedirectHandler : HttpMessageHandler
    {
        public int RequestCount { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            RequestCount++;
            if (RequestCount == 1)
            {
                return Task.FromResult(new HttpResponseMessage(HttpStatusCode.Redirect)
                {
                    Headers = { Location = new Uri("https://1.1.1.2/redirected") },
                });
            }

            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(Payload("one"), Encoding.UTF8),
            });
        }
    }
}
