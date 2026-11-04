using System.Buffers.Binary;
using System.IO.Pipes;
using System.Runtime.Versioning;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using System.Text.Json;

namespace Weave.Windows.Core.Tests;

public sealed class BrokerBoundaryTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "weave-broker-boundary-" + Guid.NewGuid().ToString("N"));

    private static BrokerProfile Fixture(RoutingMode mode = RoutingMode.Rule)
    {
        using var importer = new SubscriptionImporter();
        var selected = importer.ImportText("private-display", "https://subscription.invalid/secret-source",
            "proxies: [{name: fixture, type: http, server: example.com, port: 443}]");
        var unused = importer.ImportText("unused", "https://unused.invalid",
            "proxies: [{name: unused, type: http, server: unused.invalid, port: 443, password: unused-secret}]");
        return BrokerProfile.FromLocal([selected, unused], [], selected.Id, selected.Nodes[0].Id,
            new() { EnableTun = true, RoutingMode = mode, GeoDataDirectory = "caller-path-must-not-cross" });
    }

    [Fact]
    public async Task ProfileAndReplyDoNotExposeUnneededSecretsOrFilesystemPaths()
    {
        var profile = Fixture();
        using var wire = new MemoryStream();
        await BrokerWire.WriteAsync<BrokerRequest>(wire, new BrokerStart(1, profile), CancellationToken.None);
        var json = Encoding.UTF8.GetString(wire.ToArray().AsSpan(8));
        foreach (var excluded in new[] { "private-display", "secret-source", "unused-secret", "caller-path", "geoDataDirectory", "controller" })
            Assert.DoesNotContain(excluded, json, StringComparison.OrdinalIgnoreCase);
        wire.Position = 0;
        var decoded = Assert.IsType<BrokerStart>(await BrokerWire.ReadAsync<BrokerRequest>(wire, CancellationToken.None));
        var bundle = decoded.Profile.Build(_root, "installed-geodata");
        Assert.True(bundle.RequiresTun);
        Assert.Single(bundle.ProviderNodeCounts);
        Assert.True(File.Exists(bundle.ConfigPath));

        using var response = new MemoryStream();
        await BrokerWire.WriteAsync(response, new BrokerReply(1, Runtime: BrokerRuntimeInfo.FromBundle(bundle, true)), CancellationToken.None);
        var reply = Encoding.UTF8.GetString(response.ToArray().AsSpan(8));
        foreach (var excluded in new[] { bundle.ControllerSecret, bundle.Directory, "controllerPort", "controllerSecret", "configPath" })
            Assert.DoesNotContain(excluded, reply, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void DirectModeCarriesNoSubscriptionMaterial()
    {
        var profile = Fixture(RoutingMode.Direct);
        Assert.Empty(profile.Subscriptions);
        var bundle = profile.Build(_root, "installed-geodata");
        Assert.Empty(bundle.ProviderNodeCounts);
        Assert.True(bundle.RequiresTun);
    }

    [Theory]
    [InlineData("https://example.com/dns-query#interface=Ethernet")]
    [InlineData("https://name:password@example.com/dns-query")]
    [InlineData("tls://example.com/file")]
    [InlineData("file:///private/config")]
    public void ServiceDnsCannotSelectLocalFilesOrCallerInterfaces(string dns)
    {
        var profile = Fixture();
        Reject(profile with { Settings = profile.Settings with { DnsProfile = DnsProfile.Custom, CustomDnsEndpoint = dns } });
    }

    [Theory]
    [InlineData("DOMAIN-SUFFIX,example.com,DIRECT\nRULE-SET,local-file,DIRECT")]
    [InlineData("DOMAIN-SUFFIX,example.com,missing-group")]
    public void RawRulesCannotBypassStructuredRuleValidation(string rules)
    {
        var profile = Fixture();
        Reject(profile with { Settings = profile.Settings with { DomainRules = rules } });
    }

    [Fact]
    public void AStoredLocalKeyReferenceIsStillRejectedAtTheServiceBoundary()
    {
        var profile = Fixture();
        var item = profile.Subscriptions[0];
        Reject(profile with { Subscriptions = [item with { ProviderYaml = item.ProviderYaml + "\nproxy-providers: {other: {path: secret.yaml}}" }] });
    }

    [Fact]
    public void MalformedOrUnreferencedMetadataFailsBeforeRuntimeCreation()
    {
        var profile = Fixture();
        Reject(profile with { Subscriptions = null! });
        Reject(profile with { SelectedNodeId = "../../file" });
        Reject(profile with { Settings = profile.Settings with { RoutingMode = (RoutingMode)99 } });
        Reject(profile with { Subscriptions = [.. profile.Subscriptions, profile.Subscriptions[0] with { Id = "unused" }] });
        Reject(profile with { AppRoutes = [new("browser.exe,DIRECT", RouteKind.Direct, null, null)] });
        Reject(profile with { AppRoutes = [new("browser.exe", RouteKind.Automatic, "absent", null)] });
    }

    private void Reject(BrokerProfile profile)
    {
        Assert.Throws<InvalidDataException>(() => profile.Build(_root, "installed-geodata"));
        Assert.False(Directory.Exists(_root));
    }

    [Theory]
    [InlineData("{\"operation\":\"reload\",\"id\":1,\"path\":\"config.yaml\"}")]
    [InlineData("{\"operation\":\"stop\",\"id\":1,\"executable\":\"cmd.exe\"}")]
    [InlineData("{\"operation\":\"status\",\"id\":1,\"id\":2}")]
    [InlineData("{\"operation\":\"resolve\",\"id\":1}")]
    [InlineData("{\"operation\":\"start\",\"id\":1,\"profile\":null}")]
    [InlineData("{\"operation\":\"start\",\"id\":1,\"profile\":{\"geoDataDirectory\":\"other\"}}")]
    public async Task UnknownCommandsFieldsDuplicateKeysAndMissingParametersAreRejected(string json)
    {
        using var frame = Frame(Encoding.UTF8.GetBytes(json));
        await Assert.ThrowsAsync<JsonException>(() => BrokerWire.ReadAsync<BrokerRequest>(frame, CancellationToken.None));
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-1)]
    [InlineData(40 * 1024 * 1024 + 1)]
    public async Task InvalidLengthIsRejectedWithoutWaitingForPayload(int length)
    {
        using var frame = Frame([], length);
        await Assert.ThrowsAsync<InvalidDataException>(() => BrokerWire.ReadAsync<BrokerRequest>(frame, CancellationToken.None));
    }

    [Fact]
    public async Task UnsupportedVersionAndMalformedUtf8AreRejected()
    {
        using var wrongVersion = Frame("{}"u8.ToArray(), version: 2);
        await Assert.ThrowsAsync<InvalidDataException>(() => BrokerWire.ReadAsync<BrokerRequest>(wrongVersion, CancellationToken.None));
        using var invalid = Frame([0xff, 0xfe]);
        await Assert.ThrowsAsync<JsonException>(() => BrokerWire.ReadAsync<BrokerRequest>(invalid, CancellationToken.None));
    }

    [Fact]
    public async Task OversizeOutgoingFrameSendsNothing()
    {
        using var destination = new MemoryStream();
        await Assert.ThrowsAsync<InvalidDataException>(() => BrokerWire.WriteAsync<BrokerRequest>(destination,
            new BrokerResolve(1, new string('x', BrokerWire.MaxFrameBytes)), CancellationToken.None));
        Assert.Equal(0, destination.Length);
    }

    [Fact]
    public async Task TruncatedFrameIsAnErrorButBoundaryEofIsClean()
    {
        using var partial = Frame("{}"u8.ToArray(), 3);
        await Assert.ThrowsAsync<EndOfStreamException>(() => BrokerWire.ReadAsync<BrokerRequest>(partial, CancellationToken.None));
        using var empty = new MemoryStream();
        Assert.Null(await BrokerWire.ReadAsync<BrokerRequest>(empty, CancellationToken.None));
    }

    [Fact]
    public async Task CancelledPartialFrameDoesNotWaitForTheFullWireDeadline()
    {
        using var cancellation = new CancellationTokenSource(TimeSpan.FromMilliseconds(100));
        using var stream = new StalledFrame();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => BrokerWire.ReadAsync<BrokerRequest>(stream, cancellation.Token));
        Assert.True(cancellation.IsCancellationRequested);
    }

    [WindowsPipeFact, SupportedOSPlatform("windows")]
    public async Task WindowsPipeAuthenticatesOsIdentityAndRejectsUnexpectedExecutable()
    {
        var name = "weave-broker-test-" + Guid.NewGuid().ToString("N");
        using var server = BrokerWindowsPipe.CreateServer(name);
        var collision = Record.Exception(() => { using var duplicate = BrokerWindowsPipe.CreateServer(name); });
        Assert.True(collision is IOException or UnauthorizedAccessException);
        var acl = server.GetAccessControl();
        var authenticated = new SecurityIdentifier(WellKnownSidType.AuthenticatedUserSid, null);
        var rules = acl.GetAccessRules(true, true, typeof(SecurityIdentifier)).Cast<PipeAccessRule>()
            .Where(rule => rule.IdentityReference.Equals(authenticated) && rule.AccessControlType == AccessControlType.Allow);
        Assert.Equal(0, (int)(Assert.Single(rules).PipeAccessRights & PipeAccessRights.CreateNewInstance));
        using var client = BrokerWindowsPipe.CreateClient(name);
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var accepted = server.WaitForConnectionAsync(timeout.Token);
        await client.ConnectAsync(timeout.Token);
        await accepted;
        await client.WriteAsync("WEAVE001"u8.ToArray(), timeout.Token);
        var peer = await BrokerWindowsPipe.AuthenticateClientAsync(server, Environment.ProcessPath!, timeout.Token);
        Assert.Equal(Environment.ProcessId, peer.ProcessId);
        using var identity = WindowsIdentity.GetCurrent();
        Assert.Equal(identity.User!.Value, peer.UserSid);
        var hello = new byte[8];
        await client.ReadExactlyAsync(hello, timeout.Token);
        Assert.Equal("WEAVE001", Encoding.UTF8.GetString(hello));
        await client.WriteAsync(hello, timeout.Token);
        await Assert.ThrowsAsync<UnauthorizedAccessException>(() => BrokerWindowsPipe.AuthenticateClientAsync(server,
            Path.Combine(Path.GetTempPath(), "not-the-client.exe"), timeout.Token));
    }

    [WindowsPipeFact, SupportedOSPlatform("windows")]
    public async Task SlowPipeReaderReceivesStopBeforeTheInstanceIsReused()
    {
        var name = "weave-stop-ack-" + Guid.NewGuid().ToString("N");
        using var server = BrokerWindowsPipe.CreateServer(name);
        using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        for (var cycle = 0; cycle < 20; cycle++)
        {
            using var client = BrokerWindowsPipe.CreateClient(name);
            var accepted = server.WaitForConnectionAsync(timeout.Token);
            await client.ConnectAsync(timeout.Token);
            await accepted;
            await using var session = new BrokerSession("unused", "not-executed", "not-used");
            var serving = ServeAsync();
            try
            {
                await BrokerWire.WriteAsync<BrokerRequest>(client, new BrokerStop(1), timeout.Token);
                await Task.Delay(25, timeout.Token); // Deliberately leave the reply unread.
                Assert.False(serving.IsCompleted);
                var reply = await BrokerWire.ReadAsync<BrokerReply>(client, timeout.Token);
                Assert.Equal(1, reply!.Id);
                Assert.False(reply.Ready);
            }
            finally { client.Dispose(); await serving; }

            async Task ServeAsync()
            {
                try { await BrokerConnection.RunAsync(server, session, timeout.Token); }
                finally { server.Disconnect(); }
            }
        }
    }

    private static MemoryStream Frame(byte[] payload, int? length = null, int version = BrokerWire.Version)
    {
        var bytes = new byte[8 + payload.Length];
        BinaryPrimitives.WriteInt32BigEndian(bytes, version);
        BinaryPrimitives.WriteInt32BigEndian(bytes.AsSpan(4), length ?? payload.Length);
        payload.CopyTo(bytes, 8);
        return new MemoryStream(bytes);
    }

    private sealed class StalledFrame : MemoryStream
    {
        private bool _started;
        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            if (!_started) { _started = true; buffer.Span[0] = 0; return 1; }
            await Task.Delay(Timeout.Infinite, cancellationToken);
            return 0;
        }
    }

    public void Dispose() { if (Directory.Exists(_root)) Directory.Delete(_root, true); }

    private sealed class WindowsPipeFactAttribute : FactAttribute
    {
        public WindowsPipeFactAttribute() { if (!OperatingSystem.IsWindows()) Skip = "Requires native Windows pipe identity and ACLs."; }
    }
}
