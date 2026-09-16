using System.Security.Cryptography;

namespace Weave.Windows.Core.Tests;

public sealed class TransferAndRoutingTests
{
    private static readonly TransferSubscription Item = new("测试订阅", "https://example.com/sub",
        "proxies: [{name: test, type: http, server: example.com, port: 443}]");

    [Fact]
    public void AuthenticatedTransferRoundTripAndTamperRejection()
    {
        var key = RandomNumberGenerator.GetBytes(32);
        var plain = LanTransferCodec.Encode(new[] { Item });
        var packet = LanTransferCodec.Seal(plain, key);
        Assert.Equal(Item, Assert.Single(LanTransferCodec.Decode(LanTransferCodec.Open(packet, key))));
        packet[^1] ^= 1;
        Assert.Throws<InvalidDataException>(() => LanTransferCodec.Open(packet, key));
        Assert.Throws<InvalidDataException>(() => LanTransferCodec.Open(packet, RandomNumberGenerator.GetBytes(32)));
    }
    [Fact]
    public void AndroidJavaWireFormatIsCompatible()
    {
        var path = Environment.GetEnvironmentVariable("WEAVE_INTEROP_FIXTURE");
        if (string.IsNullOrEmpty(path)) return;
        var packet = File.ReadAllBytes(path);
        var key = Enumerable.Range(0, 32).Select(i => (byte)i).ToArray();
        Assert.Equal(Item, Assert.Single(LanTransferCodec.Decode(LanTransferCodec.Open(packet, key))));
    }
    [Fact]
    public void LinkRoundTripAndCodeAreStable()
    {
        var original = new LanTransferLink("192.168.1.2", 4321, new string('a', 32), Enumerable.Range(0, 32).Select(i => (byte)i).ToArray());
        var decoded = LanTransferLink.Parse(original.Encode());
        Assert.Equal(original.Encode(), decoded.Encode());
        Assert.Equal(original.ConfirmationCode(), decoded.ConfirmationCode());
        Assert.Equal(6, decoded.ConfirmationCode().Length);
    }
    [Theory]
    [InlineData("8.8.8.8")]
    [InlineData("localhost")]
    [InlineData("127.0.0.1.evil.example")]
    [InlineData("192.168.01.1")]
    [InlineData("0x7f000001")]
    [InlineData("+192.168.1.2")]
    [InlineData("192.168.1.2 ")]
    public void TransferNeverConnectsToPublicOrAmbiguousAddresses(string host)
    {
        Assert.False(LanTransferLink.IsPrivateIpv4(host));
        Assert.Throws<InvalidDataException>(() => LanTransferLink.Parse(new LanTransferLink(host, 80, new string('a', 32), new byte[32]).Encode()));
    }
    [Fact]
    public async Task OneTimeServerIsCompatibleAndCannotBeFetchedTwice()
    {
        await using var server = new OneTimeLanTransferServer("127.0.0.1", new[] { Item });
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        Assert.Equal(Item, Assert.Single(await LanTransferClient.FetchAsync(server.Link, timeout.Token)));
        await server.Completion;
        await Assert.ThrowsAnyAsync<HttpRequestException>(() => LanTransferClient.FetchAsync(server.Link, timeout.Token));
    }
    [Fact]
    public void TruncatedAndTrailingPacketsAreRejected()
    {
        var plain = LanTransferCodec.Encode(new[] { Item });
        Assert.Throws<InvalidDataException>(() => LanTransferCodec.Decode(plain[..^1]));
        Assert.Throws<InvalidDataException>(() => LanTransferCodec.Decode(plain.Concat(new byte[1]).ToArray()));
        Assert.Throws<InvalidDataException>(() => LanTransferCodec.Encode(Array.Empty<TransferSubscription>()));
    }

    [Theory]
    [InlineData("DOMAIN-SUFFIX,example.com,DIRECT")]
    [InlineData("IP-CIDR,192.168.0.0/16,DIRECT")]
    [InlineData("IP-CIDR6,fc00::/7,REJECT")]
    public void RulesAcceptOnlyStructuredTargets(string text) => Assert.Single(DomainRoute.Parse(text));

    [Theory]
    [InlineData("DOMAIN,example.com,DIRECT\nMATCH,DIRECT")]
    [InlineData("IP-CIDR,192.168.0.0/55,DIRECT")]
    [InlineData("DOMAIN,example.com,unknown")]
    [InlineData("IP-CIDR6,127.0.0.1/16,DIRECT")]
    public void RulesRejectInvalidOrInjectedContent(string text) => Assert.Throws<InvalidDataException>(() => DomainRoute.Parse(text));

    [Theory]
    [InlineData(RoutingMode.Rule, true)]
    [InlineData(RoutingMode.Global, false)]
    [InlineData(RoutingMode.Direct, false)]
    public void RoutingModesHaveExplicitPrecedence(RoutingMode mode, bool hasRules)
    {
        using var importer = new SubscriptionImporter();
        var subscription = importer.ImportText(Item.Name, Item.Source, Item.Payload);
        var folder = Path.Combine(Path.GetTempPath(), "weave-mode-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build(new[] { subscription },
                new[] { new WindowsAppRoute { ProcessName = "chrome.exe", DisplayName = "Chrome", Target = RouteTarget.Direct() } },
                subscription.Id, null, new WindowsNetworkOptions { RoutingMode = mode, DomainRules = DomainRoute.Parse("DOMAIN,example.com,REJECT") }, folder);
            var yaml = File.ReadAllText(bundle.ConfigPath);
            Assert.Equal(hasRules, yaml.Contains("PROCESS-NAME,chrome.exe,DIRECT"));
            Assert.Equal(hasRules, yaml.Contains("DOMAIN,example.com,REJECT"));
            Assert.Contains(mode == RoutingMode.Direct ? "MATCH,DIRECT" : "MATCH,DEFAULT", yaml);
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }
    [Fact]
    public void DirectModeNeedsNoSubscriptionAndGlobalIgnoresStaleAppRules()
    {
        using var importer = new SubscriptionImporter();
        var subscription = importer.ImportText(Item.Name, Item.Source, Item.Payload);
        foreach (var mode in new[] { RoutingMode.Direct, RoutingMode.Global })
        {
            var folder = Path.Combine(Path.GetTempPath(), "weave-isolated-mode-" + Guid.NewGuid().ToString("N"));
            try
            {
                var bundle = new MihomoConfigBuilder().Build(mode == RoutingMode.Direct ? Array.Empty<SubscriptionRecord>() : new[] { subscription },
                    new[] { new WindowsAppRoute { ProcessName = "chrome.exe", DisplayName = "Chrome", Target = RouteTarget.Fixed("removed", "removed") } },
                    mode == RoutingMode.Direct ? null : subscription.Id, null, new WindowsNetworkOptions { RoutingMode = mode, EnableTun = false }, folder);
                var yaml = File.ReadAllText(bundle.ConfigPath);
                Assert.DoesNotContain("chrome.exe", yaml);
                Assert.Contains(mode == RoutingMode.Direct ? "MATCH,DIRECT" : "MATCH,DEFAULT", yaml);
                if (mode == RoutingMode.Direct) Assert.Empty(bundle.ProviderNodeCounts);
            }
            finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
        }
    }

    [Fact]
    public void ChainRejectsLoopAndHttpEntryForUdpExit()
    {
        using var importer = new SubscriptionImporter();
        var entry = importer.ImportText("entry", "inline://entry", Item.Payload);
        var exit = importer.ImportText("exit", "inline://exit", "proxies: [{name: udp, type: hysteria2, server: example.com, port: 443, password: test}]");
        var records = new[] { entry, exit }.ToDictionary(item => item.Id);
        var options = new WindowsNetworkOptions { ChainEntrySubscriptionId = entry.Id, ChainEntryNodeId = entry.Nodes[0].Id };
        Assert.Throws<InvalidDataException>(() => ProxyChain.Build(records, entry, entry.Nodes[0].Id, options));
        Assert.Throws<InvalidDataException>(() => ProxyChain.Build(records, exit, exit.Nodes[0].Id, options));
    }

    [Fact]
    public async Task NativeCoreAcceptsChainAndPinnedChinaRules()
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrEmpty(executable)) return;
        using var importer = new SubscriptionImporter();
        var entry = importer.ImportText("entry", "inline://entry", Item.Payload.Replace("example.com", "127.0.0.1"));
        var exit = importer.ImportText("exit", "inline://exit", Item.Payload.Replace("example.com", "127.0.0.1"));
        var folder = Path.Combine(Path.GetTempPath(), "weave-chain-" + Guid.NewGuid().ToString("N"));
        try
        {
            var bundle = new MihomoConfigBuilder().Build(new[] { entry, exit }, Array.Empty<WindowsAppRoute>(), exit.Id, exit.Nodes[0].Id,
                new WindowsNetworkOptions { EnableTun = false, ChainEntrySubscriptionId = entry.Id, ChainEntryNodeId = entry.Nodes[0].Id,
                    ChinaDirect = true, GeoDataDirectory = Environment.GetEnvironmentVariable("WEAVE_TEST_GEODATA") }, folder);
            var yaml = File.ReadAllText(bundle.ConfigPath);
            Assert.Contains("dialer-proxy: WEAVE-CHAIN-ENTRY", yaml);
            Assert.Contains("GEOSITE,cn,DIRECT", yaml);
            await using var process = new MihomoProcess(executable);
            var result = await process.ValidateConfigAsync(bundle);
            Assert.True(result.IsValid, result.Diagnostics);
            await process.StartAsync(bundle);
            Assert.True(process.IsReady);
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }
}
