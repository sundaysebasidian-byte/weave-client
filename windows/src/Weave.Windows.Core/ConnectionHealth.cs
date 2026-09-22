using System.Net;

namespace Weave.Windows.Core;

public enum ConnectionHealthState { Unchecked, Checking, Reachable, ProxyUnconfirmed, TunUnconfirmed }

/// <summary>Bounded checks after explicit connection/recheck or a debounced network/resume event.
/// No cookies, identifiers, direct fallback for the proxy check, periodic polling, or TLS bypass.</summary>
public static class ConnectionHealth
{
    private static readonly string[] Targets = ["https://www.google.com/generate_204", "https://www.youtube.com/generate_204"];

    public static async Task<ConnectionHealthState> CheckAsync(RuntimeBundle bundle, CancellationToken token)
    {
        using var proxyHandler = new SocketsHttpHandler
        {
            UseProxy = true, Proxy = new WebProxy($"http://127.0.0.1:{bundle.MixedPort}"),
            AllowAutoRedirect = false, UseCookies = false, ConnectTimeout = TimeSpan.FromSeconds(5),
        };
        using var proxyClient = new HttpClient(proxyHandler) { Timeout = Timeout.InfiniteTimeSpan };
        if (!await AnyReachableAsync(proxyClient, Targets, token).ConfigureAwait(false)) return ConnectionHealthState.ProxyUnconfirmed;
        if (!bundle.RequiresTun) return ConnectionHealthState.Reachable;
        // This deliberately bypasses the HTTP proxy, NOT the OS route: it tests the
        // actual TUN + system DNS path. Failure never silently downgrades to DIRECT.
        using var tunHandler = new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false,
            UseCookies = false, ConnectTimeout = TimeSpan.FromSeconds(5) };
        using var tunClient = new HttpClient(tunHandler) { Timeout = Timeout.InfiniteTimeSpan };
        return await AnyReachableAsync(tunClient, Targets, token).ConfigureAwait(false)
            ? ConnectionHealthState.Reachable : ConnectionHealthState.TunUnconfirmed;
    }

    public static async Task<bool> AnyReachableAsync(HttpClient client, IEnumerable<string> targets, CancellationToken token)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        timeout.CancelAfter(TimeSpan.FromSeconds(8));
        var pending = targets.Select(async url =>
        {
            try
            {
                using var response = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, timeout.Token).ConfigureAwait(false);
                // Only successful HTTPS responses confirm the check. A proxy-generated
                // 502, captive portal redirect, or 403 is not proof of working Internet.
                return response.IsSuccessStatusCode;
            }
            catch (Exception error) when (error is HttpRequestException or OperationCanceledException) { return false; }
        }).ToList();
        var success = false;
        while (pending.Count > 0)
        {
            var completed = await Task.WhenAny(pending).ConfigureAwait(false);
            pending.Remove(completed);
            if (await completed.ConfigureAwait(false)) { success = true; timeout.Cancel(); break; }
        }
        await Task.WhenAll(pending).ConfigureAwait(false);
        token.ThrowIfCancellationRequested();
        return success;
    }

    public static string Description(ConnectionHealthState state) => state switch
    {
        ConnectionHealthState.Checking => L.T("正在核验实际网络连接…"),
        ConnectionHealthState.Reachable => L.T("HTTPS 连通性已确认；不代表所有网站可达或无泄漏。"),
        ConnectionHealthState.ProxyUnconfirmed => L.T("W-N01：内核已运行，但未确认出口可达。请测试所选节点，并检查 DNS 或链式代理；未自动改走直连。"),
        ConnectionHealthState.TunUnconfirmed => L.T("W-N02：代理端口可用，但 TUN／系统 DNS 路径未通过检测。浏览器可使用系统代理，请关闭其他客户端的 TUN 后重连。"),
        _ => "",
    };
}
