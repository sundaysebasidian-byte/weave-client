using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using YamlDotNet.Core;
using YamlDotNet.RepresentationModel;

namespace Weave.Windows.Core;

public sealed class SubscriptionImporter
{
    private const int MaxPayloadBytes = 5 * 1024 * 1024;
    private readonly HttpClient _httpClient;

    public SubscriptionImporter(HttpClient? httpClient = null)
    {
        _httpClient = httpClient ?? new HttpClient(
            new SocketsHttpHandler
            {
                AllowAutoRedirect = false,
                UseProxy = false,
                ConnectCallback = ConnectPublicHostAsync,
            })
        {
            Timeout = TimeSpan.FromSeconds(20),
        };
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("Weave-Windows/0.1");
    }

    public SubscriptionRecord ImportText(string name, string source, string payload)
    {
        if (Encoding.UTF8.GetByteCount(payload) > MaxPayloadBytes)
        {
            throw new InvalidDataException("订阅文件超过 5 MiB 限制");
        }

        var normalized = SubscriptionFormatConverter.Normalize(payload);
        var parsed = ClashPayloadParser.Parse(normalized);
        var id = CreateId(source);
        return new SubscriptionRecord
        {
            Id = id,
            Name = string.IsNullOrWhiteSpace(name) ? "未命名订阅" : name.Trim(),
            Source = source.Trim(),
            Payload = normalized,
            ProviderYaml = parsed.ProviderYaml,
            Nodes = parsed.Nodes,
            UpdatedAt = DateTimeOffset.UtcNow,
        };
    }

    public async Task<SubscriptionRecord> ImportUrlAsync(
        string name,
        string source,
        CancellationToken cancellationToken = default)
    {
        if (!Uri.TryCreate(source.Trim(), UriKind.Absolute, out var uri) ||
            !uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException("Windows 版远程订阅只接受 HTTPS 地址");
        }

        await RejectPrivateHostAsync(uri, cancellationToken).ConfigureAwait(false);
        using var response = await _httpClient.GetAsync(
            uri,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken).ConfigureAwait(false);
        if ((int)response.StatusCode is >= 300 and < 400)
        {
            var location = response.Headers.Location;
            if (location is null || !location.IsAbsoluteUri ||
                !location.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase))
            {
                throw new InvalidDataException("订阅重定向必须继续使用 HTTPS");
            }

            await RejectPrivateHostAsync(location, cancellationToken).ConfigureAwait(false);
            using var redirected = await _httpClient.GetAsync(
                location,
                HttpCompletionOption.ResponseHeadersRead,
                cancellationToken).ConfigureAwait(false);
            redirected.EnsureSuccessStatusCode();
            return await ReadRemoteAsync(name, uri, redirected, cancellationToken).ConfigureAwait(false);
        }
        response.EnsureSuccessStatusCode();
        return await ReadRemoteAsync(name, uri, response, cancellationToken).ConfigureAwait(false);
    }

    private async Task<SubscriptionRecord> ReadRemoteAsync(
        string name,
        Uri source,
        HttpResponseMessage response,
        CancellationToken cancellationToken)
    {
        if (response.Content.Headers.ContentLength is > MaxPayloadBytes)
        {
            throw new InvalidDataException("订阅文件超过 5 MiB 限制");
        }

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
        using var memory = new MemoryStream();
        var buffer = new byte[16 * 1024];
        var total = 0;
        while (true)
        {
            var read = await stream.ReadAsync(buffer, cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                break;
            }

            total += read;
            if (total > MaxPayloadBytes)
            {
                throw new InvalidDataException("订阅文件超过 5 MiB 限制");
            }

            memory.Write(buffer, 0, read);
        }

        var content = Encoding.UTF8.GetString(memory.ToArray());
        return ImportText(
            string.IsNullOrWhiteSpace(name) ? source.Host : name,
            source.ToString(),
            content);
    }

    public SubscriptionRecord ImportFile(string name, string path)
    {
        var info = new FileInfo(path);
        if (!info.Exists)
        {
            throw new FileNotFoundException("找不到订阅文件", path);
        }

        if (info.Length > MaxPayloadBytes)
        {
            throw new InvalidDataException("订阅文件超过 5 MiB 限制");
        }

        return ImportText(
            string.IsNullOrWhiteSpace(name) ? Path.GetFileNameWithoutExtension(path) : name,
            path,
            File.ReadAllText(path, Encoding.UTF8));
    }

    private static string CreateId(string source)
    {
        var input = Encoding.UTF8.GetBytes(source.Trim());
        return Convert.ToHexString(SHA256.HashData(input)).ToLowerInvariant()[..16];
    }

    private static async ValueTask<Stream> ConnectPublicHostAsync(
        SocketsHttpConnectionContext context,
        CancellationToken cancellationToken)
    {
        var host = context.DnsEndPoint.Host;
        var addresses = IPAddress.TryParse(host, out var literal)
            ? new[] { literal }
            : await Dns.GetHostAddressesAsync(host, cancellationToken).ConfigureAwait(false);
        if (addresses.Length == 0 || addresses.Any(IsPrivate))
        {
            throw new InvalidDataException("为避免 SSRF，远程订阅不能连接本机或私有地址");
        }

        SocketException? lastFailure = null;
        foreach (var address in addresses)
        {
            var socket = new Socket(address.AddressFamily, SocketType.Stream, ProtocolType.Tcp);
            try
            {
                await socket.ConnectAsync(new IPEndPoint(address, context.DnsEndPoint.Port), cancellationToken)
                    .ConfigureAwait(false);
                return new NetworkStream(socket, ownsSocket: true);
            }
            catch (SocketException exception)
            {
                lastFailure = exception;
                socket.Dispose();
            }
            catch
            {
                socket.Dispose();
                throw;
            }
        }

        if (lastFailure is not null)
        {
            throw lastFailure;
        }

        throw new InvalidDataException("订阅主机没有可连接的公共地址");
    }

    private static async Task RejectPrivateHostAsync(Uri uri, CancellationToken cancellationToken)
    {
        if (IPAddress.TryParse(uri.Host, out var literal))
        {
            if (IsPrivate(literal))
            {
                throw new InvalidDataException("为避免 SSRF，远程订阅不能指向本机或私有地址");
            }

            return;
        }

        var addresses = await Dns.GetHostAddressesAsync(uri.Host, cancellationToken).ConfigureAwait(false);
        if (addresses.Any(IsPrivate))
        {
            throw new InvalidDataException("为避免 SSRF，远程订阅主机解析到了本机或私有地址");
        }
    }

    private static bool IsPrivate(IPAddress address)
    {
        if (address.IsIPv4MappedToIPv6)
        {
            return IsPrivate(address.MapToIPv4());
        }

        if (IPAddress.IsLoopback(address) || address.Equals(IPAddress.Any) || address.Equals(IPAddress.IPv6Any))
        {
            return true;
        }

        var bytes = address.GetAddressBytes();
        if (address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork)
        {
            return bytes[0] == 0 || bytes[0] == 10 || bytes[0] == 127 ||
                   (bytes[0] == 172 && bytes[1] is >= 16 and <= 31) ||
                   (bytes[0] == 192 && bytes[1] == 168) ||
                   (bytes[0] == 169 && bytes[1] == 254) ||
                   bytes[0] == 100 && bytes[1] is >= 64 and <= 127;
        }

        return (bytes[0] & 0xFE) == 0xFC || (bytes[0] == 0xFE && (bytes[1] & 0xC0) == 0x80);
    }
}

internal sealed record ParsedClashPayload(string ProviderYaml, List<ProxyNode> Nodes);

internal static class ClashPayloadParser
{
    public static ParsedClashPayload Parse(string payload)
    {
        try
        {
            var stream = new YamlStream();
            using var reader = new StringReader(payload);
            stream.Load(reader);
            if (stream.Documents.Count == 0 || stream.Documents[0].RootNode is not YamlMappingNode root)
            {
                throw new InvalidDataException("Clash YAML 根节点不是对象");
            }

            var proxyNode = Find(root, "proxies");
            if (proxyNode is not YamlSequenceNode proxySequence)
            {
                throw new InvalidDataException("Clash YAML 缺少 proxies 节点列表");
            }

            var nodes = new List<ProxyNode>();
            var nodeNames = new HashSet<string>(StringComparer.Ordinal);
            foreach (var item in proxySequence.Children.OfType<YamlMappingNode>())
            {
                var rawName = Scalar(item, "name")?.Trim() ?? string.Empty;
                if (rawName.Length == 0)
                {
                    continue;
                }

                var protocol = Scalar(item, "type") ?? "unknown";
                var index = nodes.Count;
                var key = $"{rawName}\n{protocol}\n{Scalar(item, "server")}\n{Scalar(item, "port")}";
                if (!nodeNames.Add(rawName))
                {
                    throw new InvalidDataException($"订阅包含重复节点：{rawName}");
                }

                var digest = SHA256.HashData(Encoding.UTF8.GetBytes(key));
                nodes.Add(new ProxyNode
                {
                    Id = Convert.ToHexString(digest).ToLowerInvariant()[..12],
                    Name = NodeName.Core(rawName),
                    RawName = rawName,
                    Protocol = protocol.Trim().ToLowerInvariant(),
                    Index = index,
                });
            }

            if (nodes.Count == 0)
            {
                throw new InvalidDataException("订阅中没有可用节点");
            }

            return new ParsedClashPayload(SanitizeProvider(payload), nodes);
        }
        catch (YamlException exception)
        {
            throw new InvalidDataException($"Clash YAML 解析失败：{exception.Message}", exception);
        }
    }

    private static YamlNode? Find(YamlMappingNode mapping, string key)
    {
        foreach (var pair in mapping.Children)
        {
            if (pair.Key is YamlScalarNode scalar &&
                string.Equals(scalar.Value, key, StringComparison.OrdinalIgnoreCase))
            {
                return pair.Value;
            }
        }

        return null;
    }

    private static string? Scalar(YamlMappingNode mapping, string key)
    {
        return Find(mapping, key) is YamlScalarNode scalar ? scalar.Value : null;
    }

    private static string SanitizeProvider(string payload)
    {
        var referencedAnchors = System.Text.RegularExpressions.Regex.Matches(payload, @"\*([A-Za-z0-9_.-]+)")
            .Select(match => match.Groups[1].Value)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var anchorRoots = System.Text.RegularExpressions.Regex.Matches(
                payload,
                @"(?m)^([A-Za-z0-9_.-]+)\s*:\s*&([A-Za-z0-9_.-]+)")
            .Where(match => referencedAnchors.Contains(match.Groups[2].Value))
            .Select(match => match.Groups[1].Value)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        anchorRoots.Add("proxies");

        var retained = new List<string>();
        var keepRoot = false;
        foreach (var line in payload.Trim().Split('\n'))
        {
            var withoutCr = line.TrimEnd('\r');
            var trimmed = withoutCr.Trim();
            var indent = withoutCr.TakeWhile(char.IsWhiteSpace).Count();
            if (indent == 0 && trimmed.Length > 0 && !trimmed.StartsWith('#'))
            {
                var colon = trimmed.IndexOf(':');
                var key = colon > 0 ? trimmed[..colon].Trim() : string.Empty;
                keepRoot = anchorRoots.Contains(key);
            }

            if (keepRoot)
            {
                retained.Add(withoutCr);
            }
        }

        var result = string.Join(Environment.NewLine, retained).Trim();
        if (result.Length == 0 || !result.Contains("proxies:", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException("Clash 订阅缺少 proxies 节点列表");
        }

        return result + Environment.NewLine;
    }
}
