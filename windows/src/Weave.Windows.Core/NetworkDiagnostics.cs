using System.Diagnostics;
using System.Net;
using System.Text.Json;

namespace Weave.Windows.Core;

public enum ProbeFailure { Timeout, Dns, Tls, Connection, InvalidResponse, Unknown }

public sealed record ProbeResult(string Name, string Result, long? Milliseconds = null, int? HttpStatus = null, ProbeFailure? Failure = null, bool Pending = false) : System.ComponentModel.INotifyPropertyChanged
{
    public DateTimeOffset MeasuredAt { get; } = DateTimeOffset.Now;
    public bool EndpointVerified => !Pending && HttpStatus is >= 200 and < 300;
    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
    public bool ExitVerified => !Pending && (Name == "代理出口 IPv4" ? NetworkDiagnostics.IsExpectedExitAddress(Result, false) :
        Name == "代理出口 IPv6" && NetworkDiagnostics.IsExpectedExitAddress(Result, true));
    public string DisplayTitle => L.T(Name);
    public string EvidenceLabel => L.T(Pending ? "尚未检测" : EndpointVerified || ExitVerified ? "已确认" : "需复核");
    public void RefreshLanguage()
    {
        foreach (var property in new[] { nameof(Summary), nameof(Details), nameof(DisplayTitle), nameof(EvidenceLabel) })
            PropertyChanged?.Invoke(this, new(property));
    }
    public string Summary => $"{DisplayTitle} · {Details}";
    public string Details
    {
        get
        {
            if (Pending) return "—";
            var result = HttpStatus is { } status ? EndpointVerified ? L.F($"可达 · HTTP {status}") :
                status is >= 300 and < 400 ? $"HTTP {status} · {L.T("发生重定向，最终服务尚未验证")}" :
                L.F($"服务器已响应 HTTP {status}，不代表解锁") : L.T(Result);
            return Milliseconds is { } ms ? $"{result} · {ms} ms" : result;
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

    public static async Task<IReadOnlyList<ProbeResult>> RunAsync(RuntimeBundle bundle, CancellationToken token,
        IProgress<ProbeResult>? progress = null)
    {
        using var handler = new SocketsHttpHandler
        {
            Proxy = new WebProxy($"http://127.0.0.1:{bundle.MixedPort}"), UseProxy = true,
            AllowAutoRedirect = false, UseCookies = false, ConnectTimeout = TimeSpan.FromSeconds(5),
        };
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(8), MaxResponseContentBufferSize = 32 * 1024 };
        return await RunUsingClientAsync(client, token, progress).ConfigureAwait(false);
    }

    public static async Task<IReadOnlyList<ProbeResult>> RunUsingClientAsync(HttpClient client, CancellationToken token,
        IProgress<ProbeResult>? progress = null)
    {
        using var gate = new SemaphoreSlim(3);
        async Task<ProbeResult> WebsiteAsync((string Name, string Url) target)
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
                var failure = ClassifyFailure(error);
                return new ProbeResult(target.Name, FailureText(failure), Failure: failure);
            }
            finally { gate.Release(); }
        }
        async Task<ProbeResult> IpAsync(bool ipv6)
        {
            var name = ipv6 ? "代理出口 IPv6" : "代理出口 IPv4";
            await gate.WaitAsync(token).ConfigureAwait(false);
            try
            {
                var text = await client.GetStringAsync(ipv6 ? "https://api6.ipify.org?format=json" : "https://api4.ipify.org?format=json", token).ConfigureAwait(false);
                using var json = JsonDocument.Parse(text);
                var value = json.RootElement.GetProperty("ip").GetString();
                return IsExpectedExitAddress(value, ipv6) ? new ProbeResult(name, IPAddress.Parse(value!).ToString()) :
                    new ProbeResult(name, "响应无效", Failure: ProbeFailure.InvalidResponse);
            }
            catch (Exception error) when (error is HttpRequestException or OperationCanceledException or JsonException or KeyNotFoundException or InvalidOperationException)
            {
                token.ThrowIfCancellationRequested();
                var failure = ClassifyFailure(error);
                return new ProbeResult(name, FailureText(failure), Failure: failure);
            }
            finally { gate.Release(); }
        }

        async Task<ProbeResult> PublishAsync(Task<ProbeResult> task)
        {
            var result = await task.ConfigureAwait(false);
            token.ThrowIfCancellationRequested();
            progress?.Report(result);
            return result;
        }
        // IP and website evidence settle independently; a broken IPv6 path cannot hide IPv4.
        var jobs = new[] { PublishAsync(IpAsync(false)), PublishAsync(IpAsync(true)) }
            .Concat(Targets.Select(target => PublishAsync(WebsiteAsync(target)))).ToArray();
        return await Task.WhenAll(jobs).ConfigureAwait(false);
    }

    public static ProbeFailure ClassifyFailure(Exception error) => error switch
    {
        OperationCanceledException => ProbeFailure.Timeout,
        HttpRequestException { HttpRequestError: HttpRequestError.NameResolutionError } => ProbeFailure.Dns,
        HttpRequestException { HttpRequestError: HttpRequestError.SecureConnectionError } => ProbeFailure.Tls,
        HttpRequestException { HttpRequestError: HttpRequestError.ConnectionError } => ProbeFailure.Connection,
        JsonException or KeyNotFoundException or InvalidOperationException => ProbeFailure.InvalidResponse,
        _ => ProbeFailure.Unknown,
    };

    private static string FailureText(ProbeFailure failure) => failure switch
    {
        ProbeFailure.Timeout => "连接超时",
        ProbeFailure.Dns => "域名解析失败",
        ProbeFailure.Tls => "TLS 握手失败",
        ProbeFailure.Connection => "连接失败",
        ProbeFailure.InvalidResponse => "响应无效",
        _ => "无法确认",
    };

    public static bool IsExpectedExitAddress(string? value, bool ipv6)
    {
        if (!IPAddress.TryParse(value, out var address) || IPAddress.IsLoopback(address)) return false;
        var bytes = address.GetAddressBytes();
        if (ipv6)
            return bytes.Length == 16 && (bytes[0] & 0xe0) == 0x20 &&
                !(bytes[0] == 0x20 && bytes[1] == 1 && bytes[2] == 0x0d && bytes[3] == 0xb8);
        if (bytes.Length != 4) return false;
        return !(bytes[0] is 0 or 10 or 127 || bytes[0] >= 224 ||
            bytes[0] == 100 && bytes[1] is >= 64 and <= 127 ||
            bytes[0] == 169 && bytes[1] == 254 || bytes[0] == 172 && bytes[1] is >= 16 and <= 31 ||
            bytes[0] == 192 && (bytes[1] == 168 || bytes[1] == 0 && bytes[2] is 0 or 2) ||
            bytes[0] == 198 && (bytes[1] is 18 or 19 || bytes[1] == 51 && bytes[2] == 100) ||
            bytes[0] == 203 && bytes[1] == 0 && bytes[2] == 113);
    }
}
