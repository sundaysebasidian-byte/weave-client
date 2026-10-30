using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace Weave.Windows.Core;

/// <summary>Per-session authenticated loopback API, never exposed to the LAN.</summary>
public sealed class MihomoController : IDisposable
{
    private readonly HttpClient _client;
    public MihomoController(RuntimeBundle bundle, HttpMessageHandler? handler = null)
    {
        _client = new HttpClient(handler ?? new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false })
        {
            BaseAddress = new Uri($"http://127.0.0.1:{bundle.ControllerPort}/"),
            Timeout = Timeout.InfiniteTimeSpan,
        };
        _client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", bundle.ControllerSecret);
    }

    public async Task<bool> IsReadyAsync(RuntimeBundle bundle, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(3));
        using var response = await _client.GetAsync("configs", timeout.Token).ConfigureAwait(false);
        if (response.StatusCode != HttpStatusCode.OK) return false;
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
        var root = json.RootElement;
        var configured = root.ValueKind == JsonValueKind.Object && root.TryGetProperty("mixed-port", out var port) &&
            port.ValueKind == JsonValueKind.Number && port.TryGetInt32(out var portValue) && portValue == bundle.MixedPort &&
            (!bundle.RequiresTun || (root.TryGetProperty("tun", out var tun) &&
                tun.ValueKind == JsonValueKind.Object &&
                tun.TryGetProperty("enable", out var enabled) && enabled.ValueKind == JsonValueKind.True));
        if (!configured || bundle.ProviderNodeCounts.Count == 0) return configured;
        using var providersResponse = await _client.GetAsync("providers/proxies", timeout.Token).ConfigureAwait(false);
        if (!providersResponse.IsSuccessStatusCode) return false;
        using var providersJson = JsonDocument.Parse(await providersResponse.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
        if (providersJson.RootElement.ValueKind != JsonValueKind.Object ||
            !providersJson.RootElement.TryGetProperty("providers", out var providers) || providers.ValueKind != JsonValueKind.Object) return false;
        foreach (var expected in bundle.ProviderNodeCounts)
        {
            if (!providers.TryGetProperty(expected.Key, out var provider) || provider.ValueKind != JsonValueKind.Object ||
                !provider.TryGetProperty("proxies", out var nodes) || nodes.ValueKind != JsonValueKind.Array ||
                nodes.GetArrayLength() != expected.Value) return false;
        }
        if (bundle.RequiredSelections.Count > 0)
        {
            using var proxiesResponse = await _client.GetAsync("proxies", timeout.Token).ConfigureAwait(false);
            if (!proxiesResponse.IsSuccessStatusCode) return false;
            using var proxyJson = JsonDocument.Parse(await proxiesResponse.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
            if (proxyJson.RootElement.ValueKind != JsonValueKind.Object ||
                !proxyJson.RootElement.TryGetProperty("proxies", out var proxies) || proxies.ValueKind != JsonValueKind.Object) return false;
            foreach (var expected in bundle.RequiredSelections)
            {
                if (!proxies.TryGetProperty(expected.Key, out var group) || group.ValueKind != JsonValueKind.Object ||
                    !group.TryGetProperty("now", out var selected) || selected.ValueKind != JsonValueKind.String ||
                    selected.GetString() != expected.Value) return false;
            }
        }
        return true;
    }

    public async Task DisableTunAsync(CancellationToken cancellationToken)
    {
        using var response = await _client.PatchAsync("configs",
            new StringContent("{\"tun\":{\"enable\":false}}", Encoding.UTF8, "application/json"), cancellationToken).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
    }

    public async Task<IPAddress[]> ResolveHostAsync(string host, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(5));
        var families = await Task.WhenAll(QueryAsync("A", 1), QueryAsync("AAAA", 28)).ConfigureAwait(false);
        cancellationToken.ThrowIfCancellationRequested();
        var addresses = families.SelectMany(value => value).Distinct().ToArray();
        if (addresses.Length == 0) throw new HttpRequestException(L.T("核心 DNS 没有返回可用地址，请检查网络或节点"));
        return addresses;

        async Task<IPAddress[]> QueryAsync(string type, int code)
        {
            try
            {
                using var response = await _client.GetAsync($"dns/query?name={Uri.EscapeDataString(host)}&type={type}",
                    HttpCompletionOption.ResponseHeadersRead, timeout.Token).ConfigureAwait(false);
                response.EnsureSuccessStatusCode();
                await response.Content.LoadIntoBufferAsync(65_536, timeout.Token).ConfigureAwait(false);
                using var json = JsonDocument.Parse(await response.Content.ReadAsByteArrayAsync(timeout.Token).ConfigureAwait(false));
                var root = json.RootElement;
                if (root.ValueKind != JsonValueKind.Object || !root.TryGetProperty("Status", out var status) ||
                    status.ValueKind != JsonValueKind.Number || !status.TryGetInt32(out var statusCode) || statusCode != 0 ||
                    !root.TryGetProperty("Answer", out var answer) || answer.ValueKind != JsonValueKind.Array) return [];
                if (answer.GetArrayLength() > 64) return [];
                var values = new List<IPAddress>();
                foreach (var item in answer.EnumerateArray())
                    if (item.ValueKind == JsonValueKind.Object && item.TryGetProperty("type", out var recordType) &&
                        recordType.ValueKind == JsonValueKind.Number && recordType.TryGetInt32(out var recordCode) && recordCode == code &&
                        item.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.String && IPAddress.TryParse(data.GetString(), out var ip))
                        values.Add(ip);
                return values.ToArray();
            }
            catch (Exception error) when (error is HttpRequestException or JsonException or OperationCanceledException or ObjectDisposedException)
            {
                return []; // The other address family may still succeed; never fall back to OS DNS.
            }
        }
    }

    public async Task<int?> ProbeNodeAsync(string nodeName, CancellationToken cancellationToken)
    {
        var path = $"proxies/{Uri.EscapeDataString(nodeName)}/delay?timeout=5000&url={Uri.EscapeDataString("https://www.gstatic.com/generate_204")}";
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(7));
        // Per-request client timeout is bounded by this operation's token, never by a retry loop.
        using var response = await _client.GetAsync(path, timeout.Token).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode) return null;
        using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
        return json.RootElement.TryGetProperty("delay", out var delay) && delay.TryGetInt32(out var value) &&
            value is > 0 and <= 10_000 ? value : null;
    }

    public void Dispose() => _client.Dispose();

    public async Task<(long Down, long Up)> ReadTrafficAsync(CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(3));
        using var response = await _client.GetAsync("traffic", HttpCompletionOption.ResponseHeadersRead, timeout.Token).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        await using var stream = await response.Content.ReadAsStreamAsync(timeout.Token).ConfigureAwait(false);
        var buffer = new byte[4096];
        var size = 0;
        while (size < buffer.Length)
        {
            var count = await stream.ReadAsync(buffer.AsMemory(size, 1), timeout.Token).ConfigureAwait(false);
            if (count == 0) break;
            if (buffer[size++] == 10) break;
        }
        using var json = JsonDocument.Parse(buffer.AsMemory(0, size));
        return (json.RootElement.GetProperty("down").GetInt64(), json.RootElement.GetProperty("up").GetInt64());
    }
}
