using System.Net;
using System.Text;
using System.Text.Json;

namespace Weave.Windows.Core;

internal static class UriSubscriptionConverter
{
    private static readonly UTF8Encoding StrictUtf8 = new(false, true);

    public static IReadOnlyList<ProxySpec> Parse(string payload)
    {
        var specs = new List<ProxySpec>();
        foreach (var line in payload.Split('\n'))
        {
            var value = line.Trim();
            if (value.Length == 0 || value.StartsWith('#'))
            {
                continue;
            }

            if (specs.Count >= 2000)
            {
                throw new InvalidDataException("订阅节点数超过 2000 个限制");
            }

            specs.Add(ParseOne(value));
        }

        return specs;
    }

    private static ProxySpec ParseOne(string value)
    {
        var schemeEnd = value.IndexOf("://", StringComparison.Ordinal);
        if (schemeEnd <= 0)
        {
            throw new InvalidDataException("节点列表包含无法识别的内容");
        }

        var scheme = value[..schemeEnd].ToLowerInvariant();
        return scheme switch
        {
            "vmess" => ParseVmess(value),
            "ssr" => ParseShadowsocksR(value),
            "ss" => ParseShadowsocks(value),
            "vless" or "trojan" or "socks" or "socks5" or "http" or "anytls" or
                "hy2" or "hysteria2" or "tuic" => ParseStandard(value, scheme),
            "wireguard" => throw new InvalidDataException("WireGuard URI 缺少可验证的完整参数，请导入 Clash YAML"),
            _ => throw new InvalidDataException($"暂不支持 {scheme} 节点 URI"),
        };
    }

    private static ProxySpec ParseStandard(string value, string scheme)
    {
        if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
            string.IsNullOrWhiteSpace(uri.Host) || uri.Port is < 1 or > 65535 ||
            !uri.Authority.EndsWith($":{uri.Port}", StringComparison.Ordinal))
        {
            throw new InvalidDataException($"{scheme} 节点地址或端口无效");
        }

        var name = Uri.UnescapeDataString(uri.Fragment.TrimStart('#'));
        if (string.IsNullOrWhiteSpace(name))
        {
            name = $"{scheme} 节点";
        }

        var query = Query(uri.Query);
        var credentials = Uri.UnescapeDataString(uri.UserInfo);
        var type = scheme switch
        {
            "socks" => "socks5",
            "hy2" => "hysteria2",
            _ => scheme,
        };
        var spec = new ProxySpec(name, type, uri.Host, uri.Port);
        switch (type)
        {
            case "vless":
                RequireKeys(query, "security", "sni", "fp", "pbk", "sid", "type", "network",
                    "path", "host", "serviceName", "flow", "encryption", "insecure", "allowInsecure", "alpn");
                if (credentials.Contains(':')) throw new InvalidDataException($"{name} 的 VLESS 凭据格式无效");
                spec.Fields["uuid"] = Require(credentials, name, "UUID");
                if (query.TryGetValue("encryption", out var encryption) && encryption != "none")
                {
                    throw new InvalidDataException($"{name} 使用尚未支持的 VLESS 加密字段");
                }
                if (query.TryGetValue("flow", out var flow))
                {
                    if (query.GetValueOrDefault("security") is not ("tls" or "reality"))
                        throw new InvalidDataException($"{name} 的 VLESS flow 缺少 TLS");
                    spec.Fields["flow"] = flow;
                }
                AddTls(spec, query, allowReality: true, serverNameKey: "servername");
                AddTransport(spec, query);
                break;
            case "trojan":
                RequireKeys(query, "security", "sni", "fp", "pbk", "sid", "type", "network", "path", "host",
                    "serviceName", "insecure", "allowInsecure", "alpn");
                spec.Fields["password"] = Require(credentials, name, "密码");
                AddTls(spec, query, allowReality: true, serverNameKey: "sni", implicitTls: true);
                AddTransport(spec, query);
                break;
            case "socks5":
            case "http":
                RequireKeys(query);
                if (credentials.Length > 0)
                {
                    var parts = credentials.Split(':', 2);
                    spec.Fields["username"] = Require(parts[0], name, "用户名");
                    if (parts.Length == 2) spec.Fields["password"] = parts[1];
                }
                break;
            case "anytls":
                RequireKeys(query, "sni", "fp", "insecure", "allowInsecure", "alpn");
                spec.Fields["password"] = Require(credentials, name, "密码");
                AddTls(spec, query, allowReality: false, serverNameKey: "sni", implicitTls: true);
                break;
            case "hysteria2":
                RequireKeys(query, "sni", "insecure", "allowInsecure", "obfs", "obfs-password", "alpn");
                spec.Fields["password"] = Require(credentials, name, "密码");
                AddSniAndInsecure(spec, query);
                if (query.TryGetValue("obfs", out var obfs)) spec.Fields["obfs"] = obfs;
                if (query.TryGetValue("obfs-password", out var obfsPassword))
                    spec.Fields["obfs-password"] = obfsPassword;
                AddAlpn(spec, query);
                break;
            case "tuic":
                RequireKeys(query, "sni", "insecure", "allowInsecure", "congestion_control", "udp_relay_mode", "alpn");
                var tuicCredentials = credentials.Split(':', 2);
                if (tuicCredentials.Length != 2)
                    throw new InvalidDataException($"{name} 缺少 TUIC UUID 或密码");
                spec.Fields["uuid"] = Require(tuicCredentials[0], name, "UUID");
                spec.Fields["password"] = Require(tuicCredentials[1], name, "密码");
                AddSniAndInsecure(spec, query);
                if (query.TryGetValue("congestion_control", out var congestion))
                    spec.Fields["congestion-controller"] = congestion;
                if (query.TryGetValue("udp_relay_mode", out var udpMode))
                    spec.Fields["udp-relay-mode"] = udpMode;
                AddAlpn(spec, query);
                break;
        }

        return spec;
    }

    private static ProxySpec ParseShadowsocks(string value)
    {
        var fragment = value.IndexOf('#');
        var name = fragment >= 0 ? Decode(value[(fragment + 1)..]) : "ss 节点";
        var body = value[5..(fragment >= 0 ? fragment : value.Length)];
        if (body.Contains('?'))
        {
            throw new InvalidDataException("Shadowsocks 插件 URI 暂不支持安全转换，请导入 Clash YAML");
        }

        if (!body.Contains('@'))
        {
            body = DecodeBase64(body, "Shadowsocks");
        }

        var separator = body.LastIndexOf('@');
        if (separator <= 0 || separator == body.Length - 1)
        {
            throw new InvalidDataException("Shadowsocks 节点缺少凭据或服务器");
        }

        var credentials = body[..separator];
        if (!credentials.Contains(':'))
        {
            credentials = DecodeBase64(credentials, "Shadowsocks");
        }
        else
        {
            credentials = Decode(credentials);
        }

        var parts = credentials.Split(':', 2);
        if (parts.Length != 2 || string.IsNullOrWhiteSpace(parts[0]) || parts[1].Length == 0 ||
            !Uri.TryCreate($"http://{body[(separator + 1)..]}", UriKind.Absolute, out var address) ||
            string.IsNullOrWhiteSpace(address.Host) || address.Port is < 1 or > 65535 ||
            !address.Authority.EndsWith($":{address.Port}", StringComparison.Ordinal))
        {
            throw new InvalidDataException("Shadowsocks 节点字段无效");
        }

        var spec = new ProxySpec(name, "ss", address.Host, address.Port);
        spec.Fields["cipher"] = parts[0];
        spec.Fields["password"] = parts[1];
        return spec;
    }

    private static ProxySpec ParseVmess(string value)
    {
        var fragment = value.IndexOf('#');
        var body = value[8..(fragment >= 0 ? fragment : value.Length)];
        var json = DecodeBase64(body, "VMess");
        try
        {
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
                throw new InvalidDataException("VMess 节点 JSON 结构无效");
            JsonSubscriptionConverter.RequireOnlyKeys(root, "VMess", "v", "ps", "add", "port", "id",
                "aid", "scy", "tls", "sni", "net", "path", "host", "type");
            var name = fragment >= 0 ? Decode(value[(fragment + 1)..]) :
                JsonSubscriptionConverter.OptionalText(root, "ps") ?? "vmess 节点";
            var spec = new ProxySpec(name, "vmess",
                JsonSubscriptionConverter.RequiredText(root, "add", name),
                JsonSubscriptionConverter.RequiredPort(root, "port", name));
            spec.Fields["uuid"] = JsonSubscriptionConverter.RequiredText(root, "id", name);
            spec.Fields["alterId"] = JsonSubscriptionConverter.OptionalInt(root, "aid") ?? 0;
            spec.Fields["cipher"] = JsonSubscriptionConverter.OptionalText(root, "scy") ?? "auto";
            var tls = JsonSubscriptionConverter.OptionalText(root, "tls");
            var headerType = JsonSubscriptionConverter.OptionalText(root, "type");
            if (headerType is not (null or "" or "none"))
                throw new InvalidDataException($"{name} 使用尚未支持的 VMess 头类型");
            if (tls is not null && tls.Length > 0)
            {
                if (!tls.Equals("tls", StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException($"{name} 使用尚未支持的 VMess 安全层");
                spec.Fields["tls"] = true;
            }
            var sni = JsonSubscriptionConverter.OptionalText(root, "sni");
            if (!string.IsNullOrWhiteSpace(sni) && string.IsNullOrWhiteSpace(tls))
                throw new InvalidDataException($"{name} 的 SNI 参数缺少 TLS");
            if (!string.IsNullOrWhiteSpace(sni)) spec.Fields["servername"] = sni;
            var network = JsonSubscriptionConverter.OptionalText(root, "net");
            var path = JsonSubscriptionConverter.OptionalText(root, "path");
            AddNetwork(spec, network, network == "grpc" ? null : path,
                JsonSubscriptionConverter.OptionalText(root, "host"), network == "grpc" ? path : null);
            return spec;
        }
        catch (JsonException exception)
        {
            throw new InvalidDataException("VMess 节点参数不是有效 JSON", exception);
        }
    }

    internal static void AddTls(ProxySpec spec, IReadOnlyDictionary<string, string> query,
        bool allowReality, string serverNameKey, bool implicitTls = false)
    {
        var security = query.GetValueOrDefault("security")?.ToLowerInvariant();
        if (security is not (null or "" or "none" or "tls" or "reality") ||
            (security == "reality" && !allowReality) ||
            (implicitTls && security == "none"))
        {
            throw new InvalidDataException($"{spec.Name} 使用尚未支持的安全层");
        }
        if (!implicitTls && (security is "tls" or "reality")) spec.Fields["tls"] = true;
        if (!implicitTls && (security is null or "" or "none") &&
            HasAny(query, "sni", "fp", "pbk", "sid", "insecure", "allowInsecure", "alpn"))
            throw new InvalidDataException($"{spec.Name} 的 TLS 参数缺少对应安全层");
        if (security != "reality" && HasAny(query, "pbk", "sid"))
            throw new InvalidDataException($"{spec.Name} 的 Reality 参数缺少对应安全层");
        if (query.TryGetValue("sni", out var sni)) spec.Fields[serverNameKey] = sni;
        if (query.TryGetValue("fp", out var fingerprint)) spec.Fields["client-fingerprint"] = fingerprint;
        AddInsecure(spec, query);
        AddAlpn(spec, query);
        if (security == "reality")
        {
            if (!query.TryGetValue("pbk", out var publicKey) || string.IsNullOrWhiteSpace(publicKey))
                throw new InvalidDataException($"{spec.Name} 缺少 Reality 公钥");
            var reality = spec.Nested("reality-opts");
            reality["public-key"] = publicKey;
            if (query.TryGetValue("sid", out var shortId)) reality["short-id"] = shortId;
        }
    }

    internal static void AddNetwork(ProxySpec spec, string? network, string? path, string? host, string? serviceName)
    {
        if (network is null or "" or "tcp")
        {
            if (path is not null || host is not null || serviceName is not null)
                throw new InvalidDataException($"{spec.Name} 的传输参数缺少对应网络类型");
            return;
        }

        switch (network.ToLowerInvariant())
        {
            case "ws":
                if (serviceName is not null) throw new InvalidDataException($"{spec.Name} 的 WebSocket 参数无效");
                spec.Fields["network"] = "ws";
                if (path is not null) spec.Nested("ws-opts")["path"] = path;
                if (host is not null) spec.Nested("ws-opts").GetOrAddMap("headers")["Host"] = host;
                break;
            case "grpc":
                if (path is not null || host is not null)
                    throw new InvalidDataException($"{spec.Name} 的 gRPC 参数无效");
                spec.Fields["network"] = "grpc";
                if (serviceName is not null) spec.Nested("grpc-opts")["grpc-service-name"] = serviceName;
                break;
            default:
                throw new InvalidDataException($"{spec.Name} 的 {network} 传输暂不支持安全转换");
        }
    }

    private static void AddTransport(ProxySpec spec, IReadOnlyDictionary<string, string> query)
    {
        if (query.TryGetValue("type", out var type) && query.TryGetValue("network", out var network) &&
            !type.Equals(network, StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException($"{spec.Name} 的传输类型设置不一致");
        AddNetwork(spec, query.GetValueOrDefault("type") ?? query.GetValueOrDefault("network"),
            query.GetValueOrDefault("path"), query.GetValueOrDefault("host"),
            query.GetValueOrDefault("serviceName"));
    }

    private static void AddSniAndInsecure(ProxySpec spec, IReadOnlyDictionary<string, string> query)
    {
        if (query.TryGetValue("sni", out var sni)) spec.Fields["sni"] = sni;
        AddInsecure(spec, query);
    }

    private static void AddInsecure(ProxySpec spec, IReadOnlyDictionary<string, string> query)
    {
        var raw = query.GetValueOrDefault("insecure") ?? query.GetValueOrDefault("allowInsecure");
        if (raw is null) return;
        spec.Fields["skip-cert-verify"] = raw.ToLowerInvariant() switch
        {
            "1" or "true" => true,
            "0" or "false" => false,
            _ => throw new InvalidDataException($"{spec.Name} 的证书校验选项无效"),
        };
    }

    private static void AddAlpn(ProxySpec spec, IReadOnlyDictionary<string, string> query)
    {
        if (query.TryGetValue("alpn", out var alpn))
        {
            var values = alpn.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            if (values.Length == 0) throw new InvalidDataException($"{spec.Name} 的 ALPN 列表为空");
            spec.Fields["alpn"] = values;
        }
    }

    private static Dictionary<string, string> Query(string raw)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var pair in raw.TrimStart('?').Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var pieces = pair.Split('=', 2);
            var key = WebUtility.UrlDecode(pieces[0]);
            if (!result.TryAdd(key, pieces.Length == 2 ? WebUtility.UrlDecode(pieces[1]) : string.Empty))
                throw new InvalidDataException("节点 URI 包含重复的参数");
        }
        return result;
    }

    private static void RequireKeys(IReadOnlyDictionary<string, string> query, params string[] supported)
    {
        var allowed = supported.ToHashSet(StringComparer.OrdinalIgnoreCase);
        if (query.Keys.Any(key => !allowed.Contains(key)))
            throw new InvalidDataException("节点 URI 包含尚未支持的参数，请导入 Clash YAML");
    }

    private static bool HasAny(IReadOnlyDictionary<string, string> query, params string[] keys) =>
        keys.Any(query.ContainsKey);

    private static string Require(string value, string name, string field) =>
        string.IsNullOrWhiteSpace(value)
            ? throw new InvalidDataException($"{name} 缺少{field}")
            : value;

    private static string Decode(string value) => Uri.UnescapeDataString(value);

    private static string DecodeBase64(string value, string label)
    {
        try
        {
            var compact = value.Replace('-', '+').Replace('_', '/');
            compact = compact.PadRight(compact.Length + ((4 - compact.Length % 4) % 4), '=');
            return StrictUtf8.GetString(Convert.FromBase64String(compact));
        }
        catch (Exception exception) when (exception is FormatException or DecoderFallbackException)
        {
            throw new InvalidDataException($"{label} 节点参数不是有效 Base64 UTF-8", exception);
        }
    }

    private static ProxySpec ParseShadowsocksR(string value)
    {
        var encoded = value[6..].Split('#', 2)[0];
        var decoded = DecodeBase64(encoded, "SSR");
        var sections = decoded.Split("/?", 2, StringSplitOptions.None);
        var fields = sections[0].Split(':', 6);
        if (fields.Length != 6 || !int.TryParse(fields[1], out var port) || port is < 1 or > 65535 ||
            fields.Take(5).Any(string.IsNullOrWhiteSpace))
            throw new InvalidDataException("SSR 节点字段不完整");
        var parameters = sections.Length == 2 ? Query('?' + sections[1]) : new Dictionary<string, string>();
        RequireKeys(parameters, "remarks", "protoparam", "obfsparam", "group");
        var name = parameters.TryGetValue("remarks", out var remarks) ? DecodeBase64(remarks, "SSR 备注") : "ssr 节点";
        if (string.IsNullOrWhiteSpace(name)) name = "ssr 节点";
        var spec = new ProxySpec(name, "ssr", fields[0], port);
        spec.Fields["protocol"] = fields[2];
        spec.Fields["cipher"] = fields[3];
        spec.Fields["obfs"] = fields[4];
        spec.Fields["password"] = Require(DecodeBase64(fields[5], "SSR 密码"), name, "密码");
        if (parameters.TryGetValue("protoparam", out var protocolParam))
            spec.Fields["protocol-param"] = DecodeBase64(protocolParam, "SSR 协议参数");
        if (parameters.TryGetValue("obfsparam", out var obfsParam))
            spec.Fields["obfs-param"] = DecodeBase64(obfsParam, "SSR 混淆参数");
        return spec;
    }
}

internal static class ProxySpecMapExtensions
{
    public static Dictionary<string, object> GetOrAddMap(this Dictionary<string, object> fields, string key)
    {
        if (!fields.TryGetValue(key, out var existing))
        {
            existing = new Dictionary<string, object>(StringComparer.Ordinal);
            fields[key] = existing;
        }
        return (Dictionary<string, object>)existing;
    }
}
