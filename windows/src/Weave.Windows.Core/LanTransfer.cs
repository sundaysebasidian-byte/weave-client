using System.Buffers.Binary;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace Weave.Windows.Core;

public sealed record TransferSubscription(string Name, string Source, string Payload);

public sealed record LanTransferLink(string Host, int Port, string Token, byte[] Key)
{
    public string Encode() => $"weave://lan/v1/{Token}?host={Host}&port={Port}#{Convert.ToBase64String(Key).TrimEnd('=').Replace('+', '-').Replace('/', '_')}";
    public string ConfirmationCode()
    {
        var input = Encoding.ASCII.GetBytes(Token).Concat(Key).ToArray();
        var digest = SHA256.HashData(input);
        CryptographicOperations.ZeroMemory(input);
        return (((digest[0] << 16) | (digest[1] << 8) | digest[2]) % 1_000_000).ToString("D6", System.Globalization.CultureInfo.InvariantCulture);
    }
    public static LanTransferLink Parse(string raw)
    {
        if (raw.Length > 2048 || !Uri.TryCreate(raw.Trim(), UriKind.Absolute, out var uri) ||
            uri.Scheme != "weave" || uri.Host != "lan" || uri.UserInfo.Length != 0)
            throw new InvalidDataException("不是有效的 Weave 局域网链接");
        var match = Regex.Match(uri.AbsolutePath, "^/v1/([0-9a-f]{32})$");
        if (!match.Success) throw new InvalidDataException("传输链接版本或令牌无效");
        var query = uri.Query.TrimStart('?').Split('&').Select(part => part.Split('=', 2)).ToArray();
        if (query.Length != 2 || query.Any(pair => pair.Length != 2) || query.Select(pair => pair[0]).Distinct().Count() != 2)
            throw new InvalidDataException("传输地址参数无效");
        var fields = query.ToDictionary(pair => pair[0], pair => pair[1]);
        if (!fields.TryGetValue("host", out var host) || !IsPrivateIpv4(host) ||
            !fields.TryGetValue("port", out var portText) || !int.TryParse(portText, out var port) || port is < 1 or > 65535)
            throw new InvalidDataException("只允许明确的局域网 IPv4 传输地址");
        try
        {
            var text = uri.Fragment.TrimStart('#').Replace('-', '+').Replace('_', '/');
            var key = Convert.FromBase64String(text.PadRight(text.Length + (4 - text.Length % 4) % 4, '='));
            if (key.Length != 32) throw new FormatException();
            return new LanTransferLink(host, port, match.Groups[1].Value, key);
        }
        catch (FormatException) { throw new InvalidDataException("传输密钥无效"); }
    }
    public static bool IsPrivateIpv4(string host)
    {
        var parts = host.Split('.');
        if (parts.Length != 4 || parts.Any(part => !byte.TryParse(part, out _) || (part.Length > 1 && part[0] == '0'))) return false;
        var a = byte.Parse(parts[0]); var b = byte.Parse(parts[1]);
        return a is 10 or 127 || (a == 172 && b is >= 16 and <= 31) || (a == 192 && b == 168) || (a == 169 && b == 254);
    }
}

/// <summary>Wire compatible with Android LanTransferCodec v1 (big endian, AES-256-GCM).</summary>
public static class LanTransferCodec
{
    public const int MaxPlaintext = 20 * 1024 * 1024;
    public const int MaxPacket = MaxPlaintext + 64;
    private static readonly UTF8Encoding Utf8 = new(false, true);
    public static byte[] Encode(IReadOnlyList<TransferSubscription> items)
    {
        if (items.Count is < 1 or > 64) throw new InvalidDataException("请选择 1–64 份订阅");
        using var stream = new MemoryStream();
        stream.Write("WVLAN001"u8); WriteInt(stream, items.Count);
        foreach (var item in items)
        {
            WriteString(stream, item.Name, 320); WriteString(stream, item.Source, 8192);
            WriteString(stream, item.Payload, 5 * 1024 * 1024);
        }
        return stream.ToArray();
    }
    public static IReadOnlyList<TransferSubscription> Decode(byte[] data)
    {
        if (data.Length > MaxPlaintext || data.Length < 12 || !data.AsSpan(0, 8).SequenceEqual("WVLAN001"u8))
            throw new InvalidDataException("传输内容无效");
        var offset = 8;
        var count = ReadInt(data, ref offset);
        if (count is < 1 or > 64) throw new InvalidDataException("订阅数量无效");
        var result = new List<TransferSubscription>();
        for (var i = 0; i < count; i++)
            result.Add(new TransferSubscription(ReadString(data, ref offset, 320),
                ReadString(data, ref offset, 8192), ReadString(data, ref offset, 5 * 1024 * 1024)));
        if (offset != data.Length) throw new InvalidDataException("传输内容有多余数据");
        return result;
    }
    public static byte[] Seal(byte[] data, byte[] key)
    {
        if (key.Length != 32 || data.Length > MaxPlaintext) throw new InvalidDataException("传输密钥或内容长度无效");
        var packet = new byte[8 + 12 + data.Length + 16];
        "WVENC001"u8.CopyTo(packet);
        RandomNumberGenerator.Fill(packet.AsSpan(8, 12));
        using var aes = new AesGcm(key, 16);
        aes.Encrypt(packet.AsSpan(8, 12), data, packet.AsSpan(20, data.Length), packet.AsSpan(20 + data.Length, 16), "weave-lan-transfer-v1"u8);
        return packet;
    }
    public static byte[] Open(byte[] packet, byte[] key)
    {
        if (key.Length != 32 || packet.Length is < 36 or > MaxPacket || !packet.AsSpan(0, 8).SequenceEqual("WVENC001"u8))
            throw new InvalidDataException("加密传输包无效");
        var data = new byte[packet.Length - 36];
        using var aes = new AesGcm(key, 16);
        try { aes.Decrypt(packet.AsSpan(8, 12), packet.AsSpan(20, data.Length), packet.AsSpan(20 + data.Length, 16), data, "weave-lan-transfer-v1"u8); }
        catch (CryptographicException) { CryptographicOperations.ZeroMemory(data); throw new InvalidDataException("密钥错误或传输包已被篡改"); }
        return data;
    }
    private static void WriteInt(Stream stream, int value) { Span<byte> b = stackalloc byte[4]; BinaryPrimitives.WriteInt32BigEndian(b, value); stream.Write(b); }
    private static void WriteString(MemoryStream stream, string value, int max)
    {
        var bytes = Utf8.GetBytes(value);
        try
        {
            if (bytes.Length > max || stream.Length + bytes.Length + 4 > MaxPlaintext) throw new InvalidDataException("传输内容超过限制");
            WriteInt(stream, bytes.Length); stream.Write(bytes);
        }
        finally { CryptographicOperations.ZeroMemory(bytes); }
    }
    private static int ReadInt(byte[] data, ref int offset)
    {
        if (data.Length - offset < 4) throw new InvalidDataException("传输内容截断");
        var result = BinaryPrimitives.ReadInt32BigEndian(data.AsSpan(offset, 4)); offset += 4; return result;
    }
    private static string ReadString(byte[] data, ref int offset, int max)
    {
        var length = ReadInt(data, ref offset);
        if (length < 0 || length > max || length > data.Length - offset) throw new InvalidDataException("传输字段长度无效");
        try { var value = Utf8.GetString(data, offset, length); offset += length; return value; }
        catch (DecoderFallbackException) { throw new InvalidDataException("传输文字编码无效"); }
    }
}

public sealed class OneTimeLanTransferServer : IAsyncDisposable
{
    private readonly TcpListener _listener;
    private readonly CancellationTokenSource _stop = new(TimeSpan.FromMinutes(5));
    private readonly Task _task;
    public LanTransferLink Link { get; }
    public Task Completion => _task;
    public OneTimeLanTransferServer(string host, IReadOnlyList<TransferSubscription> items)
    {
        if (!LanTransferLink.IsPrivateIpv4(host)) throw new InvalidDataException("请选择局域网地址");
        var key = RandomNumberGenerator.GetBytes(32);
        var plain = LanTransferCodec.Encode(items);
        byte[] packet;
        try { packet = LanTransferCodec.Seal(plain, key); }
        finally { CryptographicOperations.ZeroMemory(plain); }
        _listener = new TcpListener(IPAddress.Parse(host), 0);
        try { _listener.Start(); }
        catch { CryptographicOperations.ZeroMemory(key); _stop.Dispose(); throw; }
        Link = new(host, ((IPEndPoint)_listener.LocalEndpoint).Port, Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant(), key);
        _task = ServeAsync(packet, _stop.Token);
    }
    private async Task ServeAsync(byte[] packet, CancellationToken token)
    {
        try
        {
            for (var attempt = 0; attempt < 50; attempt++)
            {
                using var client = await _listener.AcceptTcpClientAsync(token).ConfigureAwait(false);
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
                timeout.CancelAfter(TimeSpan.FromSeconds(8));
                try
                {
                    await using var stream = client.GetStream();
                    var header = new List<byte>();
                    var value = new byte[1];
                    while (header.Count < 8192)
                    {
                        if (await stream.ReadAsync(value, timeout.Token).ConfigureAwait(false) == 0) break;
                        header.Add(value[0]);
                        if (header.Count >= 4 && header.TakeLast(4).SequenceEqual(new byte[] { 13, 10, 13, 10 })) break;
                    }
                    var line = Encoding.ASCII.GetString(header.ToArray()).Split("\r\n")[0];
                    var allowed = line == $"GET /v1/{Link.Token} HTTP/1.1" || line == $"GET /v1/{Link.Token} HTTP/1.0";
                    var response = allowed
                        ? $"HTTP/1.1 200 OK\r\nContent-Type: application/vnd.weave.transfer\r\nContent-Length: {packet.Length}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                        : "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                    await stream.WriteAsync(Encoding.ASCII.GetBytes(response), timeout.Token).ConfigureAwait(false);
                    if (allowed)
                    {
                        // Consume before sending: even an interrupted authorized fetch cannot be replayed.
                        _listener.Stop();
                        await stream.WriteAsync(packet, timeout.Token).ConfigureAwait(false);
                        return;
                    }
                }
                catch (Exception error) when (error is IOException or OperationCanceledException) { token.ThrowIfCancellationRequested(); }
            }
        }
        catch (Exception error) when (error is OperationCanceledException or SocketException or ObjectDisposedException) { }
        finally { _listener.Stop(); CryptographicOperations.ZeroMemory(packet); }
    }
    public async ValueTask DisposeAsync()
    {
        await _stop.CancelAsync(); _listener.Stop();
        await _task.ConfigureAwait(false);
        CryptographicOperations.ZeroMemory(Link.Key);
        _stop.Dispose();
    }
    public static IReadOnlyList<string> LocalAddresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback &&
            n.NetworkInterfaceType != NetworkInterfaceType.Tunnel && !n.Name.Contains("Weave", StringComparison.OrdinalIgnoreCase))
        .SelectMany(n => n.GetIPProperties().UnicastAddresses).Select(a => a.Address.ToString())
        .Where(LanTransferLink.IsPrivateIpv4).Distinct().ToArray();
}

public static class LanTransferClient
{
    public static async Task<IReadOnlyList<TransferSubscription>> FetchAsync(LanTransferLink link, CancellationToken token)
    {
        // Revalidate objects constructed by callers, not just pasted links.
        _ = LanTransferLink.Parse(link.Encode());
        using var handler = new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false };
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(20), MaxResponseContentBufferSize = LanTransferCodec.MaxPacket };
        using var response = await client.GetAsync($"http://{link.Host}:{link.Port}/v1/{link.Token}", HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
        if (response.StatusCode != HttpStatusCode.OK ||
            response.Content.Headers.ContentType?.MediaType != "application/vnd.weave.transfer" ||
            response.Content.Headers.ContentLength is not > 0 or > LanTransferCodec.MaxPacket)
            throw new InvalidDataException("传输已过期、已使用或响应格式不正确");
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(20));
        var length = (int)response.Content.Headers.ContentLength.Value;
        var packet = new byte[length];
        await using var stream = await response.Content.ReadAsStreamAsync(timeout.Token).ConfigureAwait(false);
        await stream.ReadExactlyAsync(packet, timeout.Token).ConfigureAwait(false);
        if (await stream.ReadAsync(new byte[1], timeout.Token).ConfigureAwait(false) != 0) throw new InvalidDataException("传输长度不匹配");
        var plain = LanTransferCodec.Open(packet, link.Key);
        try { return LanTransferCodec.Decode(plain); }
        finally { CryptographicOperations.ZeroMemory(plain); }
    }
}
