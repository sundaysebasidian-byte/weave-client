using System.Text.Json;

namespace Weave.Windows.Core;

internal static class JsonSubscriptionConverter
{
    public static IReadOnlyList<ProxySpec> Parse(string payload)
    {
        try
        {
            using var document = JsonDocument.Parse(payload);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object || !root.TryGetProperty("outbounds", out var outbounds) ||
                outbounds.ValueKind != JsonValueKind.Array)
                throw new InvalidDataException("JSON 订阅缺少 outbounds 数组");

            if (outbounds.GetArrayLength() > 2000)
                throw new InvalidDataException("订阅节点数超过 2000 个限制");

            var specs = new List<ProxySpec>();
            foreach (var outbound in outbounds.EnumerateArray())
            {
                if (outbound.ValueKind != JsonValueKind.Object)
                    throw new InvalidDataException("JSON 出站结构无效");
                var isSingBox = outbound.TryGetProperty("type", out _);
                var type = RequiredText(outbound, isSingBox ? "type" : "protocol", "JSON 出站").ToLowerInvariant();
                if (type is "selector" or "urltest" or "direct" or "block" or "dns" or "freedom" or "blackhole")
                    continue;
                specs.Add(isSingBox ? SingBox(outbound, type) : V2Ray(outbound, type));
            }
            return specs;
        }
        catch (JsonException exception)
        {
            throw new InvalidDataException("订阅不是有效 JSON", exception);
        }
    }

    private static ProxySpec SingBox(JsonElement outbound, string type)
    {
        var mapped = type switch
        {
            "shadowsocks" => "ss",
            "socks" => "socks5",
            "vless" or "vmess" or "trojan" or "http" or "hysteria2" or "tuic" => type,
            _ => throw new InvalidDataException($"sing-box {type} 出站暂不支持安全转换"),
        };
        var name = OptionalText(outbound, "tag") ?? $"{type} 节点";
        var fields = new List<string> { "type", "tag", "server", "server_port" };
        fields.AddRange(mapped switch
        {
            "vless" => new[] { "uuid", "flow", "tls", "transport" },
            "vmess" => new[] { "uuid", "alter_id", "security", "tls", "transport" },
            "trojan" => new[] { "password", "tls", "transport" },
            "hysteria2" => new[] { "password", "tls", "up_mbps", "down_mbps", "obfs", "obfs_password" },
            "tuic" => new[] { "uuid", "password", "tls", "congestion_control" },
            "ss" => new[] { "method", "password" },
            "socks5" or "http" => new[] { "username", "password" },
            _ => Array.Empty<string>(),
        });
        RequireOnlyKeys(outbound, name, fields.ToArray());
        var spec = new ProxySpec(name, mapped, RequiredText(outbound, "server", name),
            RequiredPort(outbound, "server_port", name));
        switch (mapped)
        {
            case "vless":
                spec.Fields["uuid"] = RequiredText(outbound, "uuid", name);
                CopyText(outbound, "flow", spec, "flow");
                break;
            case "vmess":
                spec.Fields["uuid"] = RequiredText(outbound, "uuid", name);
                spec.Fields["alterId"] = OptionalInt(outbound, "alter_id") ?? 0;
                spec.Fields["cipher"] = OptionalText(outbound, "security") ?? "auto";
                break;
            case "trojan" or "hysteria2":
                spec.Fields["password"] = RequiredText(outbound, "password", name);
                break;
            case "tuic":
                spec.Fields["uuid"] = RequiredText(outbound, "uuid", name);
                spec.Fields["password"] = RequiredText(outbound, "password", name);
                CopyText(outbound, "congestion_control", spec, "congestion-controller");
                break;
            case "ss":
                spec.Fields["cipher"] = RequiredText(outbound, "method", name);
                spec.Fields["password"] = RequiredText(outbound, "password", name);
                break;
            case "socks5" or "http":
                CopyText(outbound, "username", spec, "username");
                CopyText(outbound, "password", spec, "password");
                break;
        }
        CopyInt(outbound, "up_mbps", spec, "up");
        CopyInt(outbound, "down_mbps", spec, "down");
        CopyText(outbound, "obfs", spec, "obfs");
        CopyText(outbound, "obfs_password", spec, "obfs-password");
        if (Object(outbound, "tls") is { } tls) AddSingBoxTls(spec, tls);
        if (Object(outbound, "transport") is { } transport) AddSingBoxTransport(spec, transport);
        if (mapped == "vless" && spec.Fields.ContainsKey("flow") && !spec.Fields.ContainsKey("tls"))
            throw new InvalidDataException($"{name} 的 VLESS flow 缺少 TLS");
        return spec;
    }

    private static ProxySpec V2Ray(JsonElement outbound, string protocol)
    {
        var mapped = protocol switch
        {
            "shadowsocks" => "ss",
            "socks" => "socks5",
            "vmess" or "vless" or "trojan" or "http" => protocol,
            _ => throw new InvalidDataException($"V2Ray {protocol} 出站暂不支持安全转换"),
        };
        var name = OptionalText(outbound, "tag") ?? $"{protocol} 节点";
        RequireOnlyKeys(outbound, name, "protocol", "tag", "settings", "streamSettings");
        var settings = Object(outbound, "settings") ?? throw new InvalidDataException($"{name} 缺少 settings");
        RequireOnlyKeys(settings, name, "vnext", "servers");
        var server = SingleObject(settings, protocol is "vmess" or "vless" ? "vnext" : "servers", name);
        RequireOnlyKeys(server, name, "address", "port", "users", "method", "password");
        var spec = new ProxySpec(name, mapped, RequiredText(server, "address", name), RequiredPort(server, "port", name));
        switch (mapped)
        {
            case "vmess" or "vless":
                var user = SingleObject(server, "users", name);
                RequireOnlyKeys(user, name, "id", "alterId", "security", "encryption", "flow");
                spec.Fields["uuid"] = RequiredText(user, "id", name);
                if (mapped == "vmess")
                {
                    spec.Fields["alterId"] = OptionalInt(user, "alterId") ?? 0;
                    spec.Fields["cipher"] = OptionalText(user, "security") ?? "auto";
                }
                else
                {
                    var encryption = OptionalText(user, "encryption");
                    if (encryption is not null && encryption != "none")
                        throw new InvalidDataException($"{name} 使用尚未支持的 VLESS 加密字段");
                    CopyText(user, "flow", spec, "flow");
                }
                break;
            case "trojan":
                if (ObjectArray(server, "users") is { } trojanUsers)
                {
                    if (server.TryGetProperty("password", out _))
                        throw new InvalidDataException($"{name} 的密码来源不唯一");
                    var trojanUser = SingleObject(server, "users", name);
                    RequireOnlyKeys(trojanUser, name, "password");
                    spec.Fields["password"] = RequiredText(trojanUser, "password", name);
                }
                else spec.Fields["password"] = RequiredText(server, "password", name);
                break;
            case "ss":
                spec.Fields["cipher"] = RequiredText(server, "method", name);
                spec.Fields["password"] = RequiredText(server, "password", name);
                break;
            case "socks5" or "http":
                if (ObjectArray(server, "users") is { } users)
                {
                    if (users.GetArrayLength() != 1) throw new InvalidDataException($"{name} 需要恰好一个用户");
                    var credentials = users[0];
                    RequireOnlyKeys(credentials, name, "user", "pass");
                    CopyText(credentials, "user", spec, "username");
                    CopyText(credentials, "pass", spec, "password");
                }
                break;
        }
        if (Object(outbound, "streamSettings") is { } stream) AddV2RayStream(spec, stream);
        if (mapped == "vless" && spec.Fields.ContainsKey("flow") && !spec.Fields.ContainsKey("tls"))
            throw new InvalidDataException($"{name} 的 VLESS flow 缺少 TLS");
        return spec;
    }

    private static void AddSingBoxTls(ProxySpec spec, JsonElement tls)
    {
        RequireOnlyKeys(tls, spec.Name, "enabled", "server_name", "insecure", "alpn", "reality");
        var enabled = OptionalBool(tls, "enabled") ?? false;
        if (!enabled)
        {
            if (spec.Type is "trojan" or "hysteria2" or "tuic")
                throw new InvalidDataException($"{spec.Name} 的 TLS 必须启用");
            if (tls.EnumerateObject().Any(property => property.Name != "enabled"))
                throw new InvalidDataException($"{spec.Name} 的 TLS 参数未启用");
            return;
        }
        if (spec.Type is "vless" or "vmess") spec.Fields["tls"] = true;
        CopyText(tls, "server_name", spec,
            spec.Type is "trojan" or "hysteria2" or "tuic" ? "sni" : "servername");
        CopyBool(tls, "insecure", spec, "skip-cert-verify");
        CopyStringArray(tls, "alpn", spec, "alpn");
        if (Object(tls, "reality") is { } reality)
        {
            if (spec.Type is not ("vless" or "vmess"))
                throw new InvalidDataException($"{spec.Name} 的 Reality 传输暂不支持安全转换");
            RequireOnlyKeys(reality, spec.Name, "enabled", "public_key", "short_id");
            if (OptionalBool(reality, "enabled") == false)
                throw new InvalidDataException($"{spec.Name} 的 Reality 参数未启用");
            spec.Nested("reality-opts")["public-key"] = RequiredText(reality, "public_key", spec.Name);
            CopyText(reality, "short_id", spec.Nested("reality-opts"), "short-id");
        }
    }

    private static void AddSingBoxTransport(ProxySpec spec, JsonElement transport)
    {
        var type = RequiredText(transport, "type", spec.Name);
        RequireOnlyKeys(transport, spec.Name, "type", "path", "headers", "service_name");
        string? host = null;
        if (Object(transport, "headers") is { } headers)
        {
            RequireOnlyKeys(headers, spec.Name, "Host");
            host = OptionalText(headers, "Host");
        }
        UriSubscriptionConverter.AddNetwork(spec, type, OptionalText(transport, "path"),
            host, OptionalText(transport, "service_name"));
    }

    private static void AddV2RayStream(ProxySpec spec, JsonElement stream)
    {
        RequireOnlyKeys(stream, spec.Name, "network", "security", "tlsSettings", "realitySettings",
            "wsSettings", "grpcSettings");
        var security = OptionalText(stream, "security") ?? "none";
        if (security is not ("none" or "tls" or "reality"))
            throw new InvalidDataException($"{spec.Name} 使用尚未支持的安全层");
        if (security == "none" && (Object(stream, "tlsSettings") is not null || Object(stream, "realitySettings") is not null))
            throw new InvalidDataException($"{spec.Name} 的安全层参数与设置不一致");
        if ((security is "tls" or "reality") && spec.Type is not ("vless" or "vmess" or "trojan"))
            throw new InvalidDataException($"{spec.Name} 的 TLS 传输暂不支持安全转换");
        if ((security is "tls" or "reality") && spec.Type is not "trojan") spec.Fields["tls"] = true;
        if (Object(stream, "tlsSettings") is { } tls)
        {
            if (security != "tls") throw new InvalidDataException($"{spec.Name} 的 TLS 设置与安全层不一致");
            RequireOnlyKeys(tls, spec.Name, "serverName", "allowInsecure", "alpn");
            CopyText(tls, "serverName", spec, spec.Type == "trojan" ? "sni" : "servername");
            CopyBool(tls, "allowInsecure", spec, "skip-cert-verify");
            CopyStringArray(tls, "alpn", spec, "alpn");
        }
        if (security == "reality")
        {
            var reality = Object(stream, "realitySettings") ?? throw new InvalidDataException($"{spec.Name} 缺少 Reality 设置");
            RequireOnlyKeys(reality, spec.Name, "serverName", "publicKey", "shortId", "fingerprint");
            CopyText(reality, "serverName", spec, spec.Type == "trojan" ? "sni" : "servername");
            CopyText(reality, "fingerprint", spec, "client-fingerprint");
            spec.Nested("reality-opts")["public-key"] = RequiredText(reality, "publicKey", spec.Name);
            CopyText(reality, "shortId", spec.Nested("reality-opts"), "short-id");
        }
        else if (Object(stream, "realitySettings") is not null)
            throw new InvalidDataException($"{spec.Name} 的 Reality 设置与安全层不一致");

        var network = OptionalText(stream, "network");
        if (network is not (null or "" or "tcp") && spec.Type is not ("vless" or "vmess" or "trojan"))
            throw new InvalidDataException($"{spec.Name} 的传输类型暂不支持安全转换");
        var ws = Object(stream, "wsSettings");
        var grpc = Object(stream, "grpcSettings");
        if (ws is not null && network != "ws" || grpc is not null && network != "grpc")
            throw new InvalidDataException($"{spec.Name} 的传输设置与网络类型不一致");
        string? host = null;
        if (ws is { } wsValue)
        {
            RequireOnlyKeys(wsValue, spec.Name, "path", "headers");
            if (Object(wsValue, "headers") is { } headers)
            {
                RequireOnlyKeys(headers, spec.Name, "Host");
                host = OptionalText(headers, "Host");
            }
        }
        if (grpc is { } grpcValue) RequireOnlyKeys(grpcValue, spec.Name, "serviceName");
        UriSubscriptionConverter.AddNetwork(spec, network, ws is { } w ? OptionalText(w, "path") : null,
            host, grpc is { } g ? OptionalText(g, "serviceName") : null);
    }

    private static JsonElement SingleObject(JsonElement parent, string key, string name)
    {
        var array = ObjectArray(parent, key);
        if (array is null || array.Value.GetArrayLength() != 1 || array.Value[0].ValueKind != JsonValueKind.Object)
            throw new InvalidDataException($"{name} 的 {key} 需要恰好一个对象");
        return array.Value[0];
    }

    private static JsonElement? ObjectArray(JsonElement parent, string key)
    {
        if (!parent.TryGetProperty(key, out var value)) return null;
        if (value.ValueKind != JsonValueKind.Array) throw new InvalidDataException($"{key} 必须是数组");
        return value;
    }

    private static JsonElement? Object(JsonElement parent, string key)
    {
        if (!parent.TryGetProperty(key, out var value)) return null;
        if (value.ValueKind != JsonValueKind.Object) throw new InvalidDataException($"{key} 必须是对象");
        return value;
    }

    internal static void RequireOnlyKeys(JsonElement value, string name, params string[] allowed)
    {
        if (value.ValueKind != JsonValueKind.Object) throw new InvalidDataException($"{name} 结构无效");
        var keys = allowed.ToHashSet(StringComparer.Ordinal);
        if (value.EnumerateObject().Any(property => !keys.Contains(property.Name)))
            throw new InvalidDataException($"{name} 含有暂不支持的参数，请导入 Clash YAML");
    }

    internal static string? OptionalText(JsonElement value, string key)
    {
        if (!value.TryGetProperty(key, out var property)) return null;
        if (property.ValueKind != JsonValueKind.String) throw new InvalidDataException($"{key} 必须是文本");
        return property.GetString();
    }

    internal static string RequiredText(JsonElement value, string key, string name) =>
        OptionalText(value, key) is { Length: > 0 } text && !string.IsNullOrWhiteSpace(text)
            ? text : throw new InvalidDataException($"{name} 缺少 {key}");

    internal static int RequiredPort(JsonElement value, string key, string name)
    {
        var port = OptionalInt(value, key);
        return port is > 0 and <= 65535 ? port.Value : throw new InvalidDataException($"{name} 的端口无效");
    }

    internal static int? OptionalInt(JsonElement value, string key)
    {
        if (!value.TryGetProperty(key, out var property)) return null;
        if (property.ValueKind == JsonValueKind.Number && property.TryGetInt32(out var number)) return number;
        if (property.ValueKind == JsonValueKind.String && int.TryParse(property.GetString(), out number)) return number;
        throw new InvalidDataException($"{key} 必须是整数");
    }

    private static bool? OptionalBool(JsonElement value, string key)
    {
        if (!value.TryGetProperty(key, out var property)) return null;
        return property.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            _ => throw new InvalidDataException($"{key} 必须是布尔值"),
        };
    }

    private static void CopyText(JsonElement source, string sourceKey, ProxySpec spec, string targetKey) =>
        CopyText(source, sourceKey, spec.Fields, targetKey);

    private static void CopyText(JsonElement source, string sourceKey, Dictionary<string, object> target, string targetKey)
    {
        if (OptionalText(source, sourceKey) is { } value) target[targetKey] = value;
    }

    private static void CopyInt(JsonElement source, string sourceKey, ProxySpec spec, string targetKey)
    {
        if (OptionalInt(source, sourceKey) is { } value) spec.Fields[targetKey] = value;
    }

    private static void CopyBool(JsonElement source, string sourceKey, ProxySpec spec, string targetKey)
    {
        if (OptionalBool(source, sourceKey) is { } value) spec.Fields[targetKey] = value;
    }

    private static void CopyStringArray(JsonElement source, string sourceKey, ProxySpec spec, string targetKey)
    {
        if (!source.TryGetProperty(sourceKey, out var array)) return;
        if (array.ValueKind != JsonValueKind.Array || array.GetArrayLength() == 0 ||
            array.EnumerateArray().Any(item => item.ValueKind != JsonValueKind.String || string.IsNullOrWhiteSpace(item.GetString())))
            throw new InvalidDataException($"{sourceKey} 必须是非空文本数组");
        spec.Fields[targetKey] = array.EnumerateArray().Select(item => item.GetString()!).ToArray();
    }
}
