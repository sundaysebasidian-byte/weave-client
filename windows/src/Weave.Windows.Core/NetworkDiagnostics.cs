using System.Diagnostics;
using System.Net;
using System.Text.Json;

namespace Weave.Windows.Core;

public sealed record ProbeResult(string Name, string Result, long? Milliseconds = null, int? HttpStatus = null) : System.ComponentModel.INotifyPropertyChanged
{
    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
    public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(nameof(Summary)));
    public string Summary
    {
        get
        {
            var result = HttpStatus is { } status ? status is >= 200 and < 400 ? L.F($"可达 · HTTP {status}") :
                L.F($"服务器已响应 HTTP {status}，不代表解锁") : L.T(Result);
            return Milliseconds is { } ms ? $"{L.T(Name)} · {result} · {ms} ms" : $"{L.T(Name)} · {result}";
        }
    }
}

/// <summary>User initiated only. Every request uses this session's proxy. No direct fallback.</summary>
public static class NetworkDiagnostics
{
    public static IReadOnlyList<(string Name, string Url)> Targets { get; } = new[]
    {
        ("Google", "https://www.google.com/generate_204"), ("YouTube", "https://www.youtube.com"),
        ("ChatGPT", "https://chatgpt.com"), ("Claude", "https://claude.ai"),
        ("X", "https://x.com"), ("TikTok", "https://www.tiktok.com"),
        ("Netflix", "https://www.netflix.com"), ("Facebook", "https://www.facebook.com"),
        ("Disney+", "https://www.disneyplus.com"),
    };

    public static async Task<IReadOnlyList<ProbeResult>> RunAsync(RuntimeBundle bundle, CancellationToken token)
    {
        using var handler = new SocketsHttpHandler
        {
            Proxy = new WebProxy($"http://127.0.0.1:{bundle.MixedPort}"), UseProxy = true,
            AllowAutoRedirect = false, ConnectTimeout = TimeSpan.FromSeconds(5),
        };
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(8), MaxResponseContentBufferSize = 32 * 1024 };
        using var gate = new SemaphoreSlim(3);
        var tasks = Targets.Select(async target =>
        {
            await gate.WaitAsync(token).ConfigureAwait(false);
            var clock = Stopwatch.StartNew();
            try
            {
                using var response = await client.GetAsync(target.Url, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
                var status = (int)response.StatusCode;
                return new ProbeResult(target.Name, "", clock.ElapsedMilliseconds, status);
            }
            catch (Exception error) when (error is HttpRequestException or OperationCanceledException)
            {
                token.ThrowIfCancellationRequested();
                return new ProbeResult(target.Name, "超时或连接失败");
            }
            finally { gate.Release(); }
        });
        var results = (await Task.WhenAll(tasks).ConfigureAwait(false)).ToList();
        try
        {
            var text = await client.GetStringAsync("https://api.ipify.org?format=json", token).ConfigureAwait(false);
            using var json = JsonDocument.Parse(text);
            var value = json.RootElement.GetProperty("ip").GetString();
            results.Insert(0, new ProbeResult("代理出口 IP", IPAddress.TryParse(value, out var ip) ? ip.ToString() : "响应无效"));
        }
        catch (Exception error) when (error is HttpRequestException or OperationCanceledException or JsonException or KeyNotFoundException)
        {
            token.ThrowIfCancellationRequested();
            results.Insert(0, new ProbeResult("代理出口 IP", "无法确认"));
        }
        return results;
    }
}
