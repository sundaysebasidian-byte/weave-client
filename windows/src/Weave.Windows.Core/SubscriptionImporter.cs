using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using YamlDotNet.Core;
using YamlDotNet.RepresentationModel;

namespace Weave.Windows.Core;

public sealed class SubscriptionImporter : IDisposable
{
    internal const int MaxBytes = 5 * 1024 * 1024;
    private readonly HttpClient _http;
    private readonly bool _ownsClient;
    public SubscriptionImporter(HttpClient? httpClient = null)
    {
        _ownsClient = httpClient is null;
        _http = httpClient ?? new HttpClient(new SocketsHttpHandler
        {
            UseProxy = false, AllowAutoRedirect = false,
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate,
            ConnectTimeout = TimeSpan.FromSeconds(15),
            ConnectCallback = ConnectPublicAsync,
        }) { Timeout = Timeout.InfiniteTimeSpan };
    }

    public SubscriptionRecord ImportText(string name, string source, string payload)
    {
        var root = ClashPayloadParser.Read(Normalize(payload));
        if (ClashPayloadParser.Find(root, "proxy-providers") is YamlMappingNode { Children.Count: > 0 })
            throw new InvalidDataException("配置含外部节点提供器。请导入 HTTPS 订阅链接，或从原客户端导出包含全部节点的配置。");
        return MakeRecord(name, source, root);
    }

    public async Task<SubscriptionRecord> ImportUrlAsync(string name, string source, CancellationToken cancellationToken = default)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(60));
        var uri = ValidateUri(source.Trim());
        var budget = new DownloadBudget();
        var sequence = await ResolveAsync(uri, new HashSet<string>(StringComparer.Ordinal), budget, 0, timeout.Token).ConfigureAwait(false);
        return MakeRecord(string.IsNullOrWhiteSpace(name) ? uri.Host : name, uri.AbsoluteUri,
            new YamlMappingNode(new YamlScalarNode("proxies"), sequence));
    }

    private async Task<YamlSequenceNode> ResolveAsync(Uri uri, HashSet<string> visited, DownloadBudget budget, int depth, CancellationToken token)
    {
        if (depth > 3 || !visited.Add(uri.AbsoluteUri) || visited.Count > 16)
            throw new InvalidDataException("节点提供器存在循环或超过安全上限");
        var text = await DownloadAsync(uri, budget, token).ConfigureAwait(false);
        var root = ClashPayloadParser.Read(Normalize(text));
        var result = new YamlSequenceNode();
        if (ClashPayloadParser.Find(root, "proxies") is YamlSequenceNode inline)
            foreach (var node in inline.Children) result.Add(node);
        if (ClashPayloadParser.Find(root, "proxy-providers") is YamlMappingNode providers)
        {
            foreach (var entry in providers.Children.Values)
            {
                if (entry is not YamlMappingNode provider)
                    throw new InvalidDataException("节点提供器格式无效");
                // Never silently discard filtering/overrides, which could change routing semantics.
                if (new[] { "filter", "exclude-filter", "exclude-type", "override", "header" }
                    .Any(key => ClashPayloadParser.Find(provider, key) is not null))
                    throw new InvalidDataException("此提供器包含过滤、覆盖或自订请求头，请导出完整节点配置后导入");
                var type = ClashPayloadParser.Scalar(provider, "type");
                YamlSequenceNode nodes;
                if (type == "inline" && ClashPayloadParser.Find(provider, "payload") is YamlSequenceNode embedded)
                    nodes = embedded;
                else if (type == "http" && ClashPayloadParser.Scalar(provider, "url") is { } url)
                    nodes = await ResolveAsync(ValidateUri(url), new HashSet<string>(visited), budget, depth + 1, token).ConfigureAwait(false);
                else throw new InvalidDataException("仅支持 HTTPS 或内嵌节点提供器；不会读取提供器指定的本地文件");
                foreach (var node in nodes.Children) result.Add(node);
            }
        }
        return result;
    }

    private async Task<string> DownloadAsync(Uri uri, DownloadBudget budget, CancellationToken token)
    {
        for (var redirect = 0; redirect <= 5; redirect++)
        {
            if (++budget.Requests > 24) throw new InvalidDataException("订阅请求数量超过上限");
            using var request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.UserAgent.ParseAdd("clash.meta/1.19.30 Weave-Windows/0.1");
            using var response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
            if ((int)response.StatusCode is >= 300 and < 400)
            {
                var location = response.Headers.Location ?? throw new InvalidDataException("订阅重定向缺少地址");
                uri = ValidateUri(new Uri(uri, location).AbsoluteUri);
                continue;
            }
            if (!response.IsSuccessStatusCode)
                throw new InvalidDataException($"订阅服务器返回 HTTP {(int)response.StatusCode}，请检查链接权限或稍后重试");
            if (response.Content.Headers.ContentLength > MaxBytes) throw new InvalidDataException("订阅超过 5 MiB");
            using var memory = new MemoryStream();
            await using var stream = await response.Content.ReadAsStreamAsync(token).ConfigureAwait(false);
            var buffer = new byte[16384];
            int count;
            while ((count = await stream.ReadAsync(buffer, token).ConfigureAwait(false)) > 0)
            {
                budget.Bytes += count;
                if (budget.Bytes > MaxBytes) throw new InvalidDataException("订阅及提供器总内容超过 5 MiB");
                memory.Write(buffer, 0, count);
            }
            return new UTF8Encoding(false, true).GetString(memory.ToArray());
        }
        throw new InvalidDataException("订阅重定向过多");
    }

    public SubscriptionRecord ImportFile(string name, string path)
    {
        var info = new FileInfo(path);
        if (!info.Exists || info.Length > MaxBytes) throw new InvalidDataException("文件不存在或超过 5 MiB");
        return ImportText(string.IsNullOrWhiteSpace(name) ? Path.GetFileNameWithoutExtension(path) : name,
            path, File.ReadAllText(path, new UTF8Encoding(false, true)));
    }

    private static SubscriptionRecord MakeRecord(string name, string source, YamlMappingNode root)
    {
        var parsed = ClashPayloadParser.Parse(root);
        // Source identity is stable when a remote subscription adds/removes/reorders nodes.
        var identity = source.StartsWith("clipboard:", StringComparison.Ordinal) ? source + name : source;
        return new SubscriptionRecord
        {
            Id = Hash(identity)[..16], Name = string.IsNullOrWhiteSpace(name) ? "未命名订阅" : name.Trim(),
            Source = source, Payload = parsed.ProviderYaml, ProviderYaml = parsed.ProviderYaml, Nodes = parsed.Nodes,
        };
    }

    private static string Normalize(string payload)
    {
        if (string.IsNullOrWhiteSpace(payload) || Encoding.UTF8.GetByteCount(payload) > MaxBytes)
            throw new InvalidDataException("订阅为空或超过 5 MiB");
        var value = payload.Trim().TrimStart('\uFEFF');
        if (value.StartsWith('{') || value.Contains("proxies", StringComparison.Ordinal) ||
            value.Contains("proxy-providers", StringComparison.Ordinal)) return value;
        try
        {
            var encoded = value.Replace("\r", "").Replace("\n", "").Replace('-', '+').Replace('_', '/');
            return new UTF8Encoding(false, true).GetString(Convert.FromBase64String(encoded.PadRight(encoded.Length + (4 - encoded.Length % 4) % 4, '=')));
        }
        catch (Exception error) when (error is FormatException or DecoderFallbackException)
        { throw new InvalidDataException("请导入 Clash/Mihomo YAML、JSON 或其 Base64 内容"); }
    }

    private static Uri ValidateUri(string source)
    {
        if (!Uri.TryCreate(source, UriKind.Absolute, out var uri) || uri.Scheme != "https" || !string.IsNullOrEmpty(uri.UserInfo))
            throw new InvalidDataException("远程订阅只接受无用户信息的 HTTPS 地址");
        return uri;
    }

    private static async ValueTask<Stream> ConnectPublicAsync(SocketsHttpConnectionContext context, CancellationToken token)
    {
        var addresses = await Dns.GetHostAddressesAsync(context.DnsEndPoint.Host, token).ConfigureAwait(false);
        if (addresses.Length == 0 || addresses.Any(IsPrivate)) throw new HttpRequestException("订阅地址解析到本机、内网或保留地址，已阻止");
        // Connect to the addresses just checked: no second DNS resolution/rebinding window.
        var socket = new Socket(SocketType.Stream, ProtocolType.Tcp);
        try
        {
            await socket.ConnectAsync(addresses, context.DnsEndPoint.Port, token).ConfigureAwait(false);
            return new NetworkStream(socket, ownsSocket: true);
        }
        catch { socket.Dispose(); throw; }
    }

    private static bool IsPrivate(IPAddress address)
    {
        if (address.IsIPv4MappedToIPv6) address = address.MapToIPv4();
        if (IPAddress.IsLoopback(address)) return true;
        var b = address.GetAddressBytes();
        if (b.Length == 4) return b[0] is 0 or 10 or 127 or >= 224 ||
            (b[0] == 172 && b[1] is >= 16 and <= 31) || (b[0] == 192 && b[1] == 168) ||
            (b[0] == 169 && b[1] == 254) || (b[0] == 100 && b[1] is >= 64 and <= 127);
        return address.Equals(IPAddress.IPv6Any) || b[0] == 0xff || (b[0] & 0xfe) == 0xfc ||
            (b[0] == 0xfe && (b[1] & 0xc0) == 0x80);
    }
    internal static string Hash(string text) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(text))).ToLowerInvariant();
    public void Dispose() { if (_ownsClient) _http.Dispose(); }
    private sealed class DownloadBudget { public int Bytes; public int Requests; }
}

internal sealed record ParsedClashPayload(string ProviderYaml, List<ProxyNode> Nodes);
internal static class ClashPayloadParser
{
    internal static YamlMappingNode Read(string text)
    {
        try
        {
            var stream = new YamlStream();
            stream.Load(new StringReader(text));
            if (stream.Documents.Count != 1 || stream.Documents[0].RootNode is not YamlMappingNode root)
                throw new InvalidDataException("Clash 配置必须只有一个对象文档");
            var budget = 100_000;
            return (YamlMappingNode)Clone(root, new HashSet<YamlNode>(ReferenceEqualityComparer.Instance), 0, ref budget);
        }
        catch (YamlException) { throw new InvalidDataException("Clash 配置格式无效，请检查缩进、引号及重复字段"); }
    }

    internal static ParsedClashPayload Parse(YamlMappingNode root)
    {
        if (Find(root, "proxies") is not YamlSequenceNode sequence || sequence.Children.Count == 0)
            throw new InvalidDataException("订阅中没有节点；请使用包含完整节点的 Clash 配置");
        var nodes = new List<ProxyNode>();
        var names = new HashSet<string>(StringComparer.Ordinal);
        foreach (var item in sequence.Children)
        {
            if (item is not YamlMappingNode proxy || Scalar(proxy, "name") is not { Length: > 0 } name ||
                Scalar(proxy, "type") is not { Length: > 0 } protocol)
                throw new InvalidDataException("存在缺少名称或协议的节点，已取消导入，不会丢弃部分节点");
            if (!names.Add(name)) throw new InvalidDataException("存在同名节点，请在原配置中改为不同名称再导入");
            nodes.Add(new ProxyNode { Id = SubscriptionImporter.Hash(name + "\n" + protocol)[..12],
                Name = NodeName.Core(name), RawName = name, Protocol = protocol.ToLowerInvariant(), Index = nodes.Count });
        }
        var clean = new YamlMappingNode(new YamlScalarNode("proxies"), sequence);
        using var writer = new StringWriter();
        new YamlStream(new YamlDocument(clean)).Save(writer, assignAnchors: false);
        return new ParsedClashPayload(writer.ToString(), nodes);
    }

    // Expand aliases and YAML merge keys with a depth/size/cycle budget. Save only actual
    // proxy data, never the subscription's controller, TUN, rules or filesystem paths.
    private static YamlNode Clone(YamlNode node, HashSet<YamlNode> path, int depth, ref int budget)
    {
        if (depth > 40 || --budget < 0 || !path.Add(node)) throw new InvalidDataException("配置过于复杂或存在循环引用");
        try
        {
            if (node is YamlScalarNode scalar) return new YamlScalarNode(scalar.Value) { Style = scalar.Style };
            if (node is YamlSequenceNode sequence)
            {
                var copy = new YamlSequenceNode();
                foreach (var child in sequence.Children) copy.Add(Clone(child, path, depth + 1, ref budget));
                return copy;
            }
            if (node is YamlMappingNode mapping)
            {
                var copy = new YamlMappingNode();
                var merge = Find(mapping, "<<");
                if (merge is not null)
                {
                    var expanded = Clone(merge, path, depth + 1, ref budget);
                    IEnumerable<YamlNode> maps = expanded is YamlSequenceNode list ? list.Children : new[] { expanded };
                    foreach (var entry in maps)
                    {
                        if (entry is not YamlMappingNode inherited) throw new InvalidDataException("YAML 合并对象无效");
                        foreach (var pair in inherited.Children) if (!copy.Children.ContainsKey(pair.Key)) copy.Add(pair.Key, pair.Value);
                    }
                }
                foreach (var pair in mapping.Children)
                {
                    if (pair.Key is YamlScalarNode { Value: "<<" }) continue;
                    copy.Children[Clone(pair.Key, path, depth + 1, ref budget)] = Clone(pair.Value, path, depth + 1, ref budget);
                }
                return copy;
            }
            throw new InvalidDataException("不支持的 YAML 节点");
        }
        finally { path.Remove(node); }
    }
    internal static YamlNode? Find(YamlMappingNode mapping, string key) =>
        mapping.Children.TryGetValue(new YamlScalarNode(key), out var value) ? value : null;
    internal static string? Scalar(YamlMappingNode mapping, string key) => (Find(mapping, key) as YamlScalarNode)?.Value;
}
