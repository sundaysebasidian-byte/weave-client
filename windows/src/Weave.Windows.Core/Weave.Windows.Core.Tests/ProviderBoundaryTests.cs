using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace Weave.Windows.Core.Tests;

public sealed class ProviderBoundaryTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "weave-provider-boundary-" + Guid.NewGuid().ToString("N"));

    [Theory]
    [InlineData("ssh", "private-key: 'C:\\Users\\other\\.ssh\\id_rsa'")]
    [InlineData("http", "certificate: 'file.pem'")]
    [InlineData("socks5", "private-key: '../key.pem'")]
    [InlineData("trojan", "interface-name: Ethernet")]
    [InlineData("http", "dialer-proxy: DIRECT")]
    [InlineData("http", "future-core-option: {path: config.yaml}")]
    [InlineData("openvpn", "config: 'vpn.conf'")]
    [InlineData("ss", "plugin: external-command, plugin-opts: {path: dangerous.exe}")]
    [InlineData("ss", "plugin: v2ray-plugin, plugin-opts: {certificate: 'nested.pem'}")]
    [InlineData("vless", "xhttp-opts: {download-settings: {private-key: 'nested.pem'}}")]
    [InlineData("trojan", "certificate: '-----BEGIN CERTIFICATE----- fake -----END CERTIFICATE-----'")]
    [InlineData("ssh", "private-key: 'not-a-key PRIVATE KEY'")]
    public void UnreviewedOrFilesystemOptionsNeverReachRuntimeFiles(string protocol, string options)
    {
        using var importer = new SubscriptionImporter();
        var record = importer.ImportText("fixture", "inline://boundary",
            $"proxies: [{{name: fixture, type: {protocol}, server: example.com, port: 443, {options}}}]");
        Assert.Throws<InvalidDataException>(() => Build(record));
        Assert.False(Directory.Exists(_root));
    }

    [Fact]
    public void StoredProviderIsCheckedEvenWhenImportWasBypassed()
    {
        var record = new SubscriptionRecord { Id = "fixture", Name = "fixture", Source = "inline://fixture", Payload = "",
            ProviderYaml = "proxies: [{name: fixture, type: http, server: example.com, port: 443}]\nproxy-providers: {external: {type: file, path: secret.yaml}}",
            Nodes = [new() { Id = "node", Name = "fixture", RawName = "fixture", Protocol = "http" }] };
        Assert.Throws<InvalidDataException>(() => Build(record));
        Assert.False(Directory.Exists(_root));
    }

    [Fact]
    public async Task InlineClientCertificateAndKeyRemainUsable()
    {
        using var key = RSA.Create(2048);
        using var cert = new CertificateRequest("CN=isolated.invalid", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1)
            .CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(1));
        using var importer = new SubscriptionImporter();
        var record = importer.ImportText("fixture", "inline://fixture", "proxies:\n- name: fixture\n  type: http\n  server: example.com\n  port: 443\n  tls: true\n  certificate: |\n    " +
            cert.ExportCertificatePem().Replace("\n", "\n    ") + "\n  private-key: |\n    " + key.ExportPkcs8PrivateKeyPem().Replace("\n", "\n    "));
        var bundle = Build(record);
        Assert.True(File.Exists(bundle.ConfigPath));
        await ValidateInlineWithCore(File.ReadAllText(Directory.GetFiles(Path.Combine(_root, "providers")).Single()));
    }

    public static TheoryData<string, string> SupportedProtocols => new()
    {
        { "http", "username: fixture, password: fixture" },
        { "socks5", "username: fixture, password: fixture, udp: true" },
        { "ss", "cipher: aes-128-gcm, password: fixture, plugin: v2ray-plugin, plugin-opts: {mode: websocket, host: example.com, path: /transport}" },
        { "ssr", "cipher: aes-128-cfb, password: fixture, obfs: plain, protocol: origin" },
        { "snell", "psk: isolated-fixture, version: 3, udp: true" },
        { "vmess", "uuid: 11111111-1111-4111-8111-111111111111, alterId: 0, cipher: auto, tls: true, servername: example.com, network: ws, ws-opts: {path: /transport, headers: {Host: example.com}}" },
        { "vless", "uuid: 11111111-1111-4111-8111-111111111111, tls: true, servername: example.com, network: grpc, grpc-opts: {grpc-service-name: fixture}" },
        { "trojan", "password: fixture, sni: example.com, network: ws, ws-opts: {path: /transport}" },
        { "hysteria", "auth-str: fixture, up: 10, down: 50" },
        { "hysteria2", "password: fixture, ports: 443-445, up: 10, down: 50" },
        { "tuic", "uuid: 11111111-1111-4111-8111-111111111111, password: fixture, congestion-controller: bbr" },
        { "anytls", "password: fixture, sni: example.com" },
        { "ssh", "username: fixture, password: fixture" },
        { "wireguard", "ip: 10.9.0.1, private-key: AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=, public-key: AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=, udp: true" },
    };

    [Theory, MemberData(nameof(SupportedProtocols))]
    public async Task SupportedOptionsSurviveTheBoundaryAndCoreValidation(string protocol, string options)
    {
        using var importer = new SubscriptionImporter();
        var record = importer.ImportText("fixture", "inline://fixture",
            $"proxies: [{{name: fixture, type: {protocol}, server: example.com, port: 443, {options}}}]");
        Build(record);
        var actual = File.ReadAllText(Directory.GetFiles(Path.Combine(_root, "providers")).Single());
        Assert.Equal(record.ProviderYaml, actual);
        await ValidateInlineWithCore(actual);
    }

    private async Task ValidateInlineWithCore(string provider, bool expectedValid = true)
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrWhiteSpace(executable)) return;
        Directory.CreateDirectory(_root);
        var path = Path.Combine(_root, "inline-validation.yaml");
        // -t may defer reading file providers. Inline the exact validated provider here so
        // every adapter is parsed; the negative control proves this checks node semantics.
        File.WriteAllText(path, "mixed-port: 0\nmode: rule\n" + provider + "\nrules: ['MATCH,DIRECT']\n");
        await using var core = new MihomoProcess(executable);
        var result = await core.ValidateConfigAsync(new RuntimeBundle { Directory = _root, ConfigPath = path, MixedPort = 0 });
        Assert.Equal(expectedValid, result.IsValid);
    }

    [Fact]
    public async Task CoreValidationActuallyRejectsAnInvalidInlineAdapter() =>
        await ValidateInlineWithCore("proxies: [{name: fixture, type: vmess, server: example.com, port: 443, uuid: invalid, cipher: auto}]", expectedValid: false);

    [Fact]
    public void ProviderSizeIsBoundedBeforeParsingOrWritingFiles()
    {
        var record = new SubscriptionRecord { Id = "large", Name = "large", Source = "inline://large", Payload = "",
            ProviderYaml = new string('x', 5 * 1024 * 1024 + 1),
            Nodes = [new() { Id = "node", Name = "fixture", RawName = "fixture", Protocol = "http" }] };
        Assert.Throws<InvalidDataException>(() => Build(record));
        Assert.False(Directory.Exists(_root));
    }

    [Fact]
    public void CombinedProvidersCannotBypassTheTotalRuntimeLimit()
    {
        var payload = new string('x', 5 * 1024 * 1024);
        var records = Enumerable.Range(0, 7).Select(index => new SubscriptionRecord
        {
            Id = "fixture" + index, Name = "fixture", Source = "inline://fixture", Payload = "",
            ProviderYaml = payload,
            Nodes = [new() { Id = "node", Name = "fixture", RawName = "fixture", Protocol = "http" }],
        }).ToList();
        var routes = records.Select(record => new WindowsAppRoute
        {
            ProcessName = record.Id + ".exe", DisplayName = record.Id, Target = RouteTarget.Automatic(record.Id),
        }).ToList();
        var error = Assert.Throws<InvalidDataException>(() => new MihomoConfigBuilder().Build(records, routes,
            records[0].Id, null, new WindowsNetworkOptions { EnableTun = true }, _root));
        Assert.Equal(L.T("节点配置超过安全处理上限。"), error.Message);
        Assert.False(Directory.Exists(_root));
    }

    [Fact]
    public void MetadataCannotForgeANodeThatIsAbsentFromTheProvider()
    {
        using var importer = new SubscriptionImporter();
        var record = importer.ImportText("fixture", "inline://fixture", "proxies: [{name: actual, type: http, server: example.com, port: 443}]");
        record.Nodes.Add(new() { Id = "invented", Name = "invented", RawName = "invented", Protocol = "http" });
        Assert.Throws<InvalidDataException>(() => Build(record));
        Assert.False(Directory.Exists(_root));
    }

    [Fact]
    public void WebSocketPathsAndHeadersAreDataRatherThanFilesystemReferences()
    {
        using var importer = new SubscriptionImporter();
        var record = importer.ImportText("fixture", "inline://fixture", "proxies: [{name: fixture, type: vmess, server: example.com, port: 443, uuid: 11111111-1111-4111-8111-111111111111, cipher: auto, network: ws, ws-opts: {path: /private-key, headers: {certificate: header-data}}}]");
        Assert.True(File.Exists(Build(record).ConfigPath));
    }

    private RuntimeBundle Build(SubscriptionRecord record) => new MihomoConfigBuilder().Build([record], [], record.Id, null,
        new WindowsNetworkOptions { EnableTun = true }, _root);

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void AliasExpansionAndYamlEscapingCannotExceedTheProviderBudget(bool escaped)
    {
        using var importer = new SubscriptionImporter();
        var scalar = escaped ? new string('"', 4000) : new string('密', 1024);
        var count = escaped ? 900 : 2000;
        var yaml = new System.Text.StringBuilder("shared: &value " + System.Text.Json.JsonSerializer.Serialize(scalar) + "\nproxies:\n");
        for (var i = 0; i < count; i++)
            yaml.AppendLine($"- {{name: node-{i}, type: http, server: example.com, port: 443, password: *value}}");
        Assert.True(System.Text.Encoding.UTF8.GetByteCount(yaml.ToString()) < 256 * 1024);
        Assert.Throws<InvalidDataException>(() => importer.ImportText("fixture", "inline://bounded-expansion", yaml.ToString()));
    }

    public void Dispose() { if (Directory.Exists(_root)) Directory.Delete(_root, true); }
}
