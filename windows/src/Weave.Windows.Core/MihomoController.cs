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
        return root.ValueKind == JsonValueKind.Object && root.TryGetProperty("mixed-port", out var port) &&
            port.ValueKind == JsonValueKind.Number && port.TryGetInt32(out var portValue) && portValue == bundle.MixedPort &&
            (!bundle.RequiresTun || (root.TryGetProperty("tun", out var tun) &&
                tun.ValueKind == JsonValueKind.Object &&
                tun.TryGetProperty("enable", out var enabled) && enabled.ValueKind == JsonValueKind.True));
    }

    public async Task DisableTunAsync(CancellationToken cancellationToken)
    {
        using var response = await _client.PatchAsync("configs",
            new StringContent("{\"tun\":{\"enable\":false}}", Encoding.UTF8, "application/json"), cancellationToken).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
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
}
