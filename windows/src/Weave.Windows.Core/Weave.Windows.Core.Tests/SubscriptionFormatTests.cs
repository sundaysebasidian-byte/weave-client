using System.Text;
using Xunit;

namespace Weave.Windows.Core.Tests;

public sealed class SubscriptionFormatTests
{
    private readonly SubscriptionImporter _importer = new();

    [Fact]
    public void ImportsUriListAndBase64Envelope()
    {
        var vmess = Convert.ToBase64String(Encoding.UTF8.GetBytes(
            """{"ps":"vmess-jp","add":"vmess.example","port":"443","id":"00000000-0000-0000-0000-000000000001","aid":0,"net":"ws","path":"/edge","tls":"tls"}"""));
        var payload = "# nodes\n" +
            "vless://00000000-0000-0000-0000-000000000002@vless.example:443?security=tls&sni=edge.example&type=ws&path=%2Fweave#vless-jp\n" +
            $"vmess://{vmess}";
        foreach (var source in new[] { payload, Convert.ToBase64String(Encoding.UTF8.GetBytes(payload)) })
        {
            var record = _importer.ImportText("main", "inline://nodes", source);
            Assert.Equal(new[] { "vless-jp", "vmess-jp" }, record.Nodes.Select(node => node.RawName));
            Assert.Contains("ws-opts:", record.ProviderYaml);
            Assert.Contains("server: 'vless.example'", record.ProviderYaml);
        }
    }

    [Fact]
    public void ImportsSingBoxJsonAndSkipsLocalOutbounds()
    {
        var payload = """
            {"outbounds":[
              {"type":"vless","tag":"edge","server":"edge.example","server_port":443,"uuid":"00000000-0000-0000-0000-000000000001","tls":{"enabled":true,"server_name":"edge.example"},"transport":{"type":"ws","path":"/v2","headers":{"Host":"front.example"}}},
              {"type":"selector","tag":"proxy","outbounds":["edge"]},
              {"type":"direct","tag":"direct"}]}
            """;
        var record = _importer.ImportText("main", "inline://sing-box", payload);
        Assert.Equal("edge", Assert.Single(record.Nodes).RawName);
        Assert.Contains("tls: true", record.ProviderYaml);
        Assert.Contains("Host: 'front.example'", record.ProviderYaml);
    }

    [Fact]
    public void ImportsBasicV2RayJsonAndBase64Envelope()
    {
        var payload = """
            {"outbounds":[{"protocol":"vmess","tag":"legacy","settings":{"vnext":[{"address":"edge.example","port":443,"users":[{"id":"00000000-0000-0000-0000-000000000003","alterId":0,"security":"auto"}]}]},"streamSettings":{"network":"ws","security":"tls","tlsSettings":{"serverName":"edge.example"},"wsSettings":{"path":"/v2"}}}]}
            """;
        foreach (var source in new[] { payload, Convert.ToBase64String(Encoding.UTF8.GetBytes(payload)) })
        {
            var record = _importer.ImportText("main", "inline://v2ray", source);
            Assert.Equal("legacy", Assert.Single(record.Nodes).RawName);
            Assert.Contains("network: 'ws'", record.ProviderYaml);
            Assert.Contains("servername: 'edge.example'", record.ProviderYaml);
        }
    }

    [Fact]
    public void ImportsSocksAndShadowsocksUris()
    {
        var credentials = Convert.ToBase64String(Encoding.UTF8.GetBytes("aes-128-gcm:secret"))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        var record = _importer.ImportText("main", "inline://mixed",
            $"ss://{credentials}@ss.example:8388#ss-edge\n" +
            "socks5://user:pass@socks.example:1080#socks-edge");
        Assert.Equal(new[] { "ss", "socks5" }, record.Nodes.Select(node => node.Protocol));
        Assert.Contains("cipher: 'aes-128-gcm'", record.ProviderYaml);
        Assert.Contains("username: 'user'", record.ProviderYaml);
    }

    [Fact]
    public void ImportsShadowsocksRAndHysteria2Uris()
    {
        var password = Convert.ToBase64String(Encoding.UTF8.GetBytes("secret")).TrimEnd('=');
        var remarks = Convert.ToBase64String(Encoding.UTF8.GetBytes("ssr-edge")).TrimEnd('=');
        var ssrBody = $"ssr.example:8388:auth_sha1_v4:aes-128-cfb:plain:{password}/?remarks={remarks}";
        var ssr = Convert.ToBase64String(Encoding.UTF8.GetBytes(ssrBody))
            .TrimEnd('=').Replace('+', '-').Replace('/', '_');
        var record = _importer.ImportText("main", "inline://mixed",
            $"ssr://{ssr}\nhy2://secret@hy2.example:443?sni=front.example&insecure=0#hy2-edge");
        Assert.Equal(new[] { "ssr", "hysteria2" }, record.Nodes.Select(node => node.Protocol));
        Assert.Contains("protocol: 'auth_sha1_v4'", record.ProviderYaml);
        Assert.Contains("sni: 'front.example'", record.ProviderYaml);
        Assert.DoesNotContain("tls: true", record.ProviderYaml);
    }

    [Fact]
    public void ImportsSingBoxHysteria2TlsAsSni()
    {
        var payload = """
            {"outbounds":[{"type":"hysteria2","tag":"hy2","server":"hy2.example","server_port":443,"password":"secret","tls":{"enabled":true,"server_name":"front.example","insecure":false,"alpn":["h3"]}}]}
            """;
        var record = _importer.ImportText("main", "inline://hy2", payload);
        Assert.Equal("hysteria2", Assert.Single(record.Nodes).Protocol);
        Assert.Contains("sni: 'front.example'", record.ProviderYaml);
        Assert.Contains("skip-cert-verify: false", record.ProviderYaml);
        Assert.DoesNotContain("tls: true", record.ProviderYaml);
    }

    [Fact]
    public void ImportsV2RayTrojanServerPassword()
    {
        var payload = """
            {"outbounds":[{"protocol":"trojan","tag":"trojan-edge","settings":{"servers":[{"address":"trojan.example","port":443,"password":"secret"}]},"streamSettings":{"security":"tls","tlsSettings":{"serverName":"front.example"}}}]}
            """;
        var record = _importer.ImportText("main", "inline://trojan", payload);
        Assert.Equal("trojan", Assert.Single(record.Nodes).Protocol);
        Assert.Contains("sni: 'front.example'", record.ProviderYaml);
    }

    [Fact]
    public void ImportsVmessGrpcServiceName()
    {
        var body = """{"ps":"grpc-edge","add":"edge.example","port":443,"id":"00000000-0000-0000-0000-000000000003","net":"grpc","path":"rpc","tls":"tls"}""";
        var uri = "vmess://" + Convert.ToBase64String(Encoding.UTF8.GetBytes(body));
        var record = _importer.ImportText("main", "inline://grpc", uri);
        Assert.Contains("network: 'grpc'", record.ProviderYaml);
        Assert.Contains("grpc-service-name: 'rpc'", record.ProviderYaml);
    }

    [Fact]
    public void ImportsTrojanRealityUriWithRequiredPublicKey()
    {
        var record = _importer.ImportText("main", "inline://reality",
            "trojan://secret@edge.example:443?security=reality&sni=front.example&pbk=public-key&sid=abcd#edge");
        Assert.Equal("trojan", Assert.Single(record.Nodes).Protocol);
        Assert.Contains("reality-opts:", record.ProviderYaml);
        Assert.Contains("public-key: 'public-key'", record.ProviderYaml);
    }

    [Theory]
    [InlineData("wireguard://private-key@example.com:51820#wg")]
    [InlineData("vless://id@example.com:443?type=kcp#bad")]
    [InlineData("vless://id@example.com:443?security=reality#bad")]
    [InlineData("trojan://secret@example.com:443?security=reality#bad")]
    [InlineData("vless://id@example.com:443?sni=front.example#bad")]
    [InlineData("http://example.com#missing-port")]
    [InlineData("<!doctype html><html><body>portal</body></html>")]
    public void RejectsUnsafeOrInvalidUriPayload(string payload)
    {
        Assert.Throws<InvalidDataException>(() => _importer.ImportText("main", "inline://bad", payload));
    }

    [Theory]
    [InlineData("""{"outbounds":[{"type":"vless","tag":"edge","server":"example.com","server_port":443,"uuid":"id","detour":"proxy"}]}""")]
    [InlineData("""{"outbounds":[{"protocol":"vmess","tag":"edge","settings":{"vnext":[{"address":"example.com","port":443,"users":[{"id":"id"},{"id":"other"}]}]}}]}""")]
    [InlineData("""{"outbounds":[{"type":"vless","tag":"edge","server":"example.com","server_port":443,"uuid":"id","transport":{"type":"quic"}}]}""")]
    public void RejectsJsonThatWouldLoseProxySettings(string payload)
    {
        Assert.Throws<InvalidDataException>(() => _importer.ImportText("main", "inline://unsafe", payload));
    }

    [Fact]
    public void FileImportRejectsInvalidUtf8InsteadOfCorruptingCredentials()
    {
        var path = Path.Combine(Path.GetTempPath(), $"weave-invalid-{Guid.NewGuid():N}.json");
        try
        {
            File.WriteAllBytes(path, new byte[] { (byte)'{', 0xFF, (byte)'}' });
            Assert.Throws<InvalidDataException>(() => _importer.ImportFile("main", path));
        }
        finally
        {
            File.Delete(path);
        }
    }
}
