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
}
