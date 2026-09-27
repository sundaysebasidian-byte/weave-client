using System.Net.Http.Headers;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;

namespace Weave.Windows.Core;

public sealed record TrafficSnapshot(
    long UploadBytesPerSecond,
    long DownloadBytesPerSecond,
    long? UploadTotalBytes,
    long? DownloadTotalBytes);

public sealed class MihomoControllerClient : IDisposable
{
    private readonly HttpClient _client;
    private readonly bool _ownsClient;
    private readonly Uri _baseAddress;
    private readonly string _secret;

    public MihomoControllerClient(int controlPort, string controlSecret, HttpClient? client = null)
    {
        if (controlPort is < 1 or > 65535 || string.IsNullOrWhiteSpace(controlSecret))
            throw new ArgumentException("Mihomo 控制接口参数无效");

        _baseAddress = new Uri($"http://127.0.0.1:{controlPort}/");
        _secret = controlSecret;
        _client = client ?? new HttpClient(new SocketsHttpHandler
        {
            AllowAutoRedirect = false,
            ConnectTimeout = TimeSpan.FromSeconds(5),
            UseProxy = false,
        });
        _ownsClient = client is null;
    }

    public async IAsyncEnumerable<TrafficSnapshot> WatchTrafficAsync(
        [EnumeratorCancellation] CancellationToken cancellationToken = default)
    {
        using var request = CreateRequest("traffic");
        HttpResponseMessage response;
        using (var headersTimeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken))
        {
            headersTimeout.CancelAfter(TimeSpan.FromSeconds(10));
            response = await _client.SendAsync(
                request, HttpCompletionOption.ResponseHeadersRead, headersTimeout.Token).ConfigureAwait(false);
        }
        using (response)
        {
            response.EnsureSuccessStatusCode();
            await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken).ConfigureAwait(false);
            using var reader = new StreamReader(stream, new UTF8Encoding(false, true),
                detectEncodingFromByteOrderMarks: true, bufferSize: 4096, leaveOpen: true);

            while (true)
            {
                var line = await reader.ReadLineAsync(cancellationToken).ConfigureAwait(false);
                if (line is null) yield break;
                if (string.IsNullOrWhiteSpace(line)) continue;
                if (line.Length > 4096) throw new InvalidDataException("Mihomo 流量响应过长");
                yield return ParseTraffic(line);
            }
        }
    }

    public async Task<NodeHealthSnapshot> ProbeNodeAsync(
        string providerName,
        ProxyNode node,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(providerName) || string.IsNullOrWhiteSpace(node.RawName))
            throw new ArgumentException("测速目标无效");

        var samples = new int?[3];
        var path = $"providers/proxies/{Uri.EscapeDataString(providerName)}/" +
                   $"{Uri.EscapeDataString(node.RawName)}/healthcheck" +
                   "?url=https%3A%2F%2Fwww.gstatic.com%2Fgenerate_204&timeout=5000";
        for (var round = 0; round < samples.Length; round++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            using var request = CreateRequest(path);
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(8));
            try
            {
                using var response = await _client.SendAsync(request, timeout.Token).ConfigureAwait(false);
                if (response.StatusCode is System.Net.HttpStatusCode.GatewayTimeout or System.Net.HttpStatusCode.RequestTimeout)
                {
                    samples[round] = null;
                }
                else
                {
                    response.EnsureSuccessStatusCode();
                    var payload = await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false);
                    samples[round] = ParseDelay(payload);
                }
            }
            catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
            {
                throw new TimeoutException("Mihomo 控制接口未返回测速结果");
            }
            if (round < samples.Length - 1)
                await Task.Delay(250, cancellationToken).ConfigureAwait(false);
        }
        return NodeHealthSnapshot.FromSamples(node, samples);
    }

    public void Dispose()
    {
        if (_ownsClient) _client.Dispose();
    }

    private HttpRequestMessage CreateRequest(string path)
    {
        var request = new HttpRequestMessage(HttpMethod.Get, new Uri(_baseAddress, path));
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _secret);
        return request;
    }

    internal static TrafficSnapshot ParseTraffic(string payload)
    {
        try
        {
            using var document = JsonDocument.Parse(payload);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
                throw new InvalidDataException("Mihomo 流量响应结构无效");
            return new TrafficSnapshot(
                RequiredNonnegative(root, "up"),
                RequiredNonnegative(root, "down"),
                OptionalNonnegative(root, "upTotal"),
                OptionalNonnegative(root, "downTotal"));
        }
        catch (JsonException exception)
        {
            throw new InvalidDataException("Mihomo 流量响应不是有效 JSON", exception);
        }
    }

    internal static int? ParseDelay(string payload)
    {
        try
        {
            using var document = JsonDocument.Parse(payload);
            if (document.RootElement.ValueKind != JsonValueKind.Object ||
                !document.RootElement.TryGetProperty("delay", out var delay) ||
                delay.ValueKind != JsonValueKind.Number || !delay.TryGetInt32(out var value))
                throw new InvalidDataException("Mihomo 测速响应缺少有效延迟");
            return value is >= 1 and <= 10000 ? value : null;
        }
        catch (JsonException exception)
        {
            throw new InvalidDataException("Mihomo 测速响应不是有效 JSON", exception);
        }
    }

    private static long RequiredNonnegative(JsonElement root, string key) =>
        OptionalNonnegative(root, key) ?? throw new InvalidDataException($"Mihomo 流量响应缺少 {key}");

    private static long? OptionalNonnegative(JsonElement root, string key)
    {
        if (!root.TryGetProperty(key, out var value)) return null;
        if (value.ValueKind != JsonValueKind.Number || !value.TryGetInt64(out var number) || number < 0)
            throw new InvalidDataException($"Mihomo 流量响应的 {key} 无效");
        return number;
    }
}
