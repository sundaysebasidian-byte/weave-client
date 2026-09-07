namespace Weave.Windows.Core.Tests;

public sealed class CoreTests
{
    [Fact]
    public async Task BundledCoreAcceptsGeneratedConfiguration()
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrWhiteSpace(executable)) return;
        var record = new SubscriptionImporter().ImportText("test", "inline://test", "proxies:\n  - name: test\n    type: ss\n    server: example.com\n    port: 443\n    cipher: aes-128-gcm\n    password: test-only\n");
        var folder = Path.Combine(Path.GetTempPath(), "weave-core-test-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build(new[] { record }, Array.Empty<WindowsAppRoute>(), record.Id, null, new WindowsNetworkOptions(), folder);
            await using var process = new MihomoProcess(executable);
            var result = await process.ValidateConfigAsync(bundle);
            Assert.True(result.IsValid, result.Diagnostics);
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }

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
