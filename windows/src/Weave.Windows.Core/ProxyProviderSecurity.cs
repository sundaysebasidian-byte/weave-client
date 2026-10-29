using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using YamlDotNet.RepresentationModel;

namespace Weave.Windows.Core;

/// <summary>
/// Closed provider contract for the pinned Mihomo v1.19.30. No caller-selected files,
/// interfaces, dialers or extension fields enter a potentially privileged runtime.
/// This validates data capabilities; the actual core still validates protocol semantics.
/// </summary>
internal static class ProxyProviderSecurity
{
    private const int MaxBytes = 5 * 1024 * 1024;
    private static readonly UTF8Encoding Utf8 = new(false, true);
    private delegate void Check(YamlNode node);
    private static InvalidDataException Unsupported() => new(L.T("节点包含尚未核验的协议或选项，已取消连接；请检查配置。"));
    private static InvalidDataException FileReference() => new(L.T("节点证书和密钥必须使用有效的内联内容，不能引用本机文件。"));

    internal static void CheckTotalSize(IEnumerable<SubscriptionRecord> records)
    {
        long total = 0;
        foreach (var record in records)
        {
            var bytes = Utf8.GetByteCount(record.ProviderYaml);
            if (bytes > MaxBytes) throw new InvalidDataException(L.T("节点配置超过安全处理上限。"));
            total += bytes;
            if (total > 32 * 1024 * 1024) throw new InvalidDataException(L.T("节点配置超过安全处理上限。"));
        }
    }

    internal static string Validate(SubscriptionRecord record)
    {
        if (Utf8.GetByteCount(record.ProviderYaml) > MaxBytes) throw new InvalidDataException(L.T("节点配置超过安全处理上限。"));
        var root = ClashPayloadParser.Read(record.ProviderYaml);
        if (root.Children.Count != 1 || ClashPayloadParser.Find(root, "proxies") is not YamlSequenceNode proxies) throw Unsupported();
        foreach (var item in proxies.Children)
        {
            if (item is not YamlMappingNode proxy || ClashPayloadParser.Scalar(proxy, "type") is not { } protocol ||
                !Protocols.TryGetValue(protocol, out var check)) throw Unsupported();
            check(proxy);
            if (protocol == "ss") CheckPlugin(proxy);
            if (protocol == "ssh" && ClashPayloadParser.Scalar(proxy, "private-key") is { Length: > 0 } sshKey)
            {
                if (!PemEncoding.TryFind(sshKey, out var pem) || !sshKey[pem.Label].EndsWith("PRIVATE KEY", StringComparison.Ordinal) ||
                    pem.DecodedDataLength == 0) throw FileReference();
            }
            if (protocol == "wireguard")
            {
                var key = ClashPayloadParser.Scalar(proxy, "private-key");
                if (key is null || !Convert.TryFromBase64String(key, new byte[32], out var length) || length != 32) throw FileReference();
            }
        }
        var parsed = ClashPayloadParser.Parse(root);
        // Stored metadata and client-supplied metadata cannot disagree with the runtime body.
        if (!parsed.Nodes.Select(n => (n.RawName, n.Protocol)).SequenceEqual(record.Nodes.Select(n => (n.RawName, n.Protocol))) ||
            record.Nodes.Select(n => n.Id).Distinct(StringComparer.Ordinal).Count() != record.Nodes.Count) throw Unsupported();
        return parsed.ProviderYaml;
    }

    private static void Scalar(YamlNode node)
    {
        if (node is not YamlScalarNode { Value: { } value } || value.Length > 65_536 || value.Contains('\0')) throw Unsupported();
    }
    private static Check List(Check item) => node =>
    {
        if (node is not YamlSequenceNode sequence || sequence.Children.Count > 1024) throw Unsupported();
        foreach (var child in sequence.Children) item(child);
    };
    private static Check Headers(bool lists = false) => node =>
    {
        if (node is not YamlMappingNode map || map.Children.Count > 128) throw Unsupported();
        foreach (var pair in map.Children)
        {
            if (pair.Key is not YamlScalarNode { Value: { Length: > 0 and <= 256 } key } ||
                key.Any(c => !char.IsAsciiLetterOrDigit(c) && c is not ('-' or '_'))) throw Unsupported();
            var values = lists && pair.Value is YamlSequenceNode sequence ? sequence.Children : [pair.Value];
            foreach (var value in values)
            {
                Scalar(value);
                if (((YamlScalarNode)value).Value!.Any(c => c is '\r' or '\n')) throw Unsupported();
            }
        }
    };
    private static void PluginMapping(YamlNode node)
    {
        // The closed field schema is selected by the sibling plugin name in CheckPlugin.
        if (node is not YamlMappingNode map || map.Children.Count > 64) throw Unsupported();
    }
    private static Check Options(string scalars = "", string lists = "", bool tls = true, params (string Key, Check Value)[] children)
    {
        var fields = scalars.Split(' ', StringSplitOptions.RemoveEmptyEntries).ToDictionary(key => key, _ => (Check)Scalar, StringComparer.Ordinal);
        foreach (var key in lists.Split(' ', StringSplitOptions.RemoveEmptyEntries)) fields.Add(key, List(Scalar));
        foreach (var (key, check) in children) fields.Add(key, check);
        return node =>
        {
            if (node is not YamlMappingNode map) throw Unsupported();
            foreach (var pair in map.Children)
            {
                if (pair.Key is not YamlScalarNode { Value: { } key } || !fields.TryGetValue(key, out var check)) throw Unsupported();
                check(pair.Value);
            }
            if (tls) ValidateTlsMaterial(map);
        };
    }

    private static void ValidateTlsMaterial(YamlMappingNode map)
    {
        var cert = ClashPayloadParser.Scalar(map, "certificate");
        var key = ClashPayloadParser.Scalar(map, "private-key");
        if (string.IsNullOrEmpty(cert) && string.IsNullOrEmpty(key)) return;
        if (string.IsNullOrEmpty(cert)) throw FileReference();
        try
        {
            // Mihomo tries file loading if parsing inline material fails. Validate the pair
            // here, rather than merely checking a PEM-looking prefix and allowing fallback.
            using var parsed = string.IsNullOrEmpty(key) ? X509Certificate2.CreateFromPem(cert) : X509Certificate2.CreateFromPem(cert, key);
        }
        catch (Exception error) when (error is CryptographicException or ArgumentException) { throw FileReference(); }
    }

    private const string Common = "name type server port tfo mptcp ip-version";
    private const string Tls = "skip-cert-verify name-cert-verify fingerprint certificate private-key";
    private static readonly Check Ech = Options("enable config query-server-name");
    private static readonly Check Reality = Options("public-key short-id support-x25519mlkem768");
    private static readonly Check ShadowTls = Options("password version");
    private static readonly Check Restls = Options("password version-hint restls-script");
    private static readonly Check Jls = Options("username password");
    private static readonly Check Ws = Options("path max-early-data early-data-header-name v2ray-http-upgrade v2ray-http-upgrade-fast-open",
        children: [("headers", Headers())]);
    private static readonly Check Http = Options("method", "path", children: [("headers", Headers(lists: true))]);
    private static readonly Check H2 = Options("path", "host");
    private static readonly Check Grpc = Options("grpc-service-name grpc-user-agent ping-interval max-connections min-streams max-streams");
    private static readonly Check Reuse = Options("max-concurrency max-connections c-max-reuse-times h-max-request-times h-max-reusable-secs h-keep-alive-period");
    private static readonly Check XHttpDownload = Options("path host server port tls servername client-fingerprint " + Tls, "alpn",
        children: [("headers", Headers()), ("reuse-settings", Reuse), ("ech-opts", Ech), ("reality-opts", Reality),
            ("shadow-tls-opts", ShadowTls), ("restls-opts", Restls), ("jls-opts", Jls)]);
    private static readonly Check XHttp = Options("path host mode no-grpc-header x-padding-bytes x-padding-obfs-mode x-padding-key x-padding-header x-padding-placement x-padding-method uplink-http-method session-placement session-key session-table session-length seq-placement seq-key uplink-data-placement uplink-data-key uplink-chunk-size sc-max-each-post-bytes sc-min-posts-interval-ms",
        children: [("headers", Headers()), ("reuse-settings", Reuse), ("download-settings", XHttpDownload)]);
    private static readonly Check Smux = Options("enabled protocol max-connections min-streams max-streams padding statistic only-tcp",
        children: [("brutal-opts", Options("enabled up down"))]);
    private static readonly Check Mkcp = Options("mtu tti uplink-capacity downlink-capacity congestion write-buffer read-buffer seed header");
    private static readonly Check Mekya = Options("url h2-pool-size max-write-delay max-request-size polling-interval-initial max-write-size max-write-duration-ms max-simultaneous-write-connection packet-writing-buffer",
        children: [("kcp", Mkcp)]);
    private static readonly Check Peer = Options("server port public-key pre-shared-key", "reserved allowed-ips");
    private static readonly Check Amnezia = Options("version jc jmin jmax s1 s2 s3 s4 h1 h2 h3 h4 i1 i2 i3 i4 i5 j1 j2 j3 itime header-protection-key content-padding-addition rekey-after-time rekey-timeout reject-after-time keepalive-timeout max-handshake-attempts random-trailers disable-cookies");

    private static Check Node(string scalars, string lists = "", bool tls = true, params (string Key, Check Value)[] children) =>
        Options(Common + " " + scalars, lists, tls, [("smux", Smux), .. children]);
    private static readonly Dictionary<string, Check> Protocols = new(StringComparer.Ordinal)
    {
        ["http"] = Node("username password tls sni " + Tls, children: [("headers", Headers())]),
        ["socks5"] = Node("username password tls udp " + Tls),
        ["ss"] = Node("password cipher udp plugin udp-over-tcp udp-over-tcp-version client-fingerprint", children: [("plugin-opts", PluginMapping)]),
        ["ssr"] = Node("password cipher obfs obfs-param protocol protocol-param udp"),
        ["snell"] = Node("psk udp version reuse client-fingerprint", children: [("obfs-opts", Options("mode host"))]),
        ["vmess"] = Node("uuid alterId cipher udp network tls servername packet-addr xudp packet-encoding global-padding authenticated-length client-fingerprint " + Tls, "alpn",
            children: [("ech-opts", Ech), ("reality-opts", Reality), ("shadow-tls-opts", ShadowTls), ("restls-opts", Restls), ("jls-opts", Jls),
                ("ws-opts", Ws), ("http-opts", Http), ("h2-opts", H2), ("grpc-opts", Grpc), ("mkcp-opts", Mkcp), ("mekya-opts", Mekya)]),
        ["vless"] = Node("uuid flow tls udp packet-addr xudp packet-encoding encryption network servername client-fingerprint " + Tls, "alpn",
            children: [("ech-opts", Ech), ("reality-opts", Reality), ("shadow-tls-opts", ShadowTls), ("restls-opts", Restls), ("jls-opts", Jls),
                ("ws-opts", Ws), ("ws-headers", Headers()), ("http-opts", Http), ("h2-opts", H2), ("grpc-opts", Grpc), ("xhttp-opts", XHttp)]),
        ["trojan"] = Node("password sni udp network client-fingerprint " + Tls, "alpn",
            children: [("ech-opts", Ech), ("reality-opts", Reality), ("shadow-tls-opts", ShadowTls), ("restls-opts", Restls), ("jls-opts", Jls),
                ("ws-opts", Ws), ("grpc-opts", Grpc), ("ss-opts", Options("enabled method password"))]),
        ["hysteria"] = Node("ports protocol obfs-protocol up up-speed down down-speed auth auth-str obfs sni recv-window-conn recv-window disable-mtu-discovery fast-open hop-interval " + Tls,
            "alpn", children: [("ech-opts", Ech)]),
        ["hysteria2"] = Node("ports hop-interval up down password obfs obfs-password obfs-min-packet-size obfs-max-packet-size sni cwnd bbr-profile udp-mtu handshake-timeout initial-stream-receive-window max-stream-receive-window initial-connection-receive-window max-connection-receive-window " + Tls,
            "alpn", children: [("ech-opts", Ech), ("realm-opts", Options("enable server-url token realm-id sni " + Tls, "stun-servers alpn"))]),
        ["tuic"] = Node("token uuid password ip heartbeat-interval reduce-rtt request-timeout udp-relay-mode congestion-controller disable-sni max-udp-relay-packet-size fast-open max-open-streams cwnd bbr-profile recv-window-conn recv-window disable-mtu-discovery max-datagram-frame-size sni udp-over-stream udp-over-stream-version " + Tls,
            "alpn", children: [("ech-opts", Ech)]),
        ["anytls"] = Node("password sni client-fingerprint udp client-metadata idle-session-check-interval idle-session-timeout min-idle-session disable-reuse " + Tls,
            "alpn", children: [("ech-opts", Ech), ("shadow-tls-opts", ShadowTls), ("restls-opts", Restls), ("jls-opts", Jls)]),
        ["ssh"] = Node("username password private-key private-key-passphrase", "host-key host-key-algorithms", tls: false),
        ["wireguard"] = Node("ip ipv6 private-key workers mtu udp persistent-keepalive remote-dns-resolve refresh-server-ip-interval public-key pre-shared-key", "dns reserved allowed-ips", tls: false,
            children: [("peers", List(Peer)), ("ip-stack", Options("mode congestion-controller")), ("amnezia-wg-option", Amnezia)]),
    };

    private static readonly Dictionary<string, Check> Plugins = new(StringComparer.Ordinal)
    {
        ["obfs"] = Options("mode host"),
        ["v2ray-plugin"] = Options("mode host path tls mux v2ray-http-upgrade v2ray-http-upgrade-fast-open " + Tls,
            children: [("headers", Headers()), ("ech-opts", Ech)]),
        ["gost-plugin"] = Options("mode host path tls mux " + Tls, children: [("headers", Headers()), ("ech-opts", Ech)]),
        ["shadow-tls"] = Options("password host version " + Tls, "alpn"),
        ["restls"] = Options("password host version-hint restls-script fingerprint skip-cert-verify name-cert-verify force-tls12"),
        ["jls"] = Options("host username password", "alpn"),
        ["kcptun"] = Options("key crypt mode conn autoexpire scavengettl mtu ratelimit sndwnd rcvwnd datashard parityshard dscp nocomp acknodelay nodelay interval resend nc sockbuf smuxver smuxbuf framesize streambuf keepalive"),
    };
    private static void CheckPlugin(YamlMappingNode proxy)
    {
        var plugin = ClashPayloadParser.Scalar(proxy, "plugin");
        var options = ClashPayloadParser.Find(proxy, "plugin-opts");
        if (string.IsNullOrEmpty(plugin)) { if (options is not null) throw Unsupported(); return; }
        if (!Plugins.TryGetValue(plugin, out var check)) throw Unsupported();
        if (options is not null) check(options);
    }
}
