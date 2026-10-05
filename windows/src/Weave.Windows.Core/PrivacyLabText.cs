namespace Weave.Windows.Core;

/// <summary>Translations apply only to the bundled static lab template, never to network responses.</summary>
public static class PrivacyLabText
{
    public static string Localize(string html, string language)
    {
        if (language != "en") return html;
        foreach (var pair in English.OrderByDescending(pair => pair.Key.Length))
            html = html.Replace(pair.Key, pair.Value, StringComparison.Ordinal);
        return html.Replace("lang=\"zh-CN\"", "lang=\"en\"", StringComparison.Ordinal);
    }
    private static readonly Dictionary<string, string> English = new()
    {
        ["Weave 本地浏览器隐私实验"] = "Weave local browser privacy lab",
        ["浏览器隐私实验"] = "Browser privacy lab",
        ["一次性检测，不循环刷新。网络错误、无候选地址均不是“无泄漏”的证据。"] = "One-time tests without continuous polling. Network errors or missing candidates are not proof of no leakage.",
        ["HTTPS 出口"] = "HTTPS exit",
        ["正在分别查询 IPv4 / IPv6…"] = "Checking IPv4 / IPv6 separately…",
        ["WebRTC 路径"] = "WebRTC path",
        ["正在收集 ICE 候选，最多 6 秒…"] = "Collecting ICE candidates for up to 6 seconds…",
        ["出口不同可能来自绕过代理、双栈差异或代理多出口，需要结合实际网络判断。"] = "Different exits may indicate a proxy bypass, dual-stack differences or multiple proxy exits. Interpret them in your network context.",
        ["浏览器标识特征 · 只在本机"] = "Browser identifiers · Local only",
        ["这是特征展示，不计算虚假的隐私分数，也不判断你在全网是否唯一。"] = "These are observable features, not a privacy score or proof of global uniqueness.",
        ["无法确认（不可据此判断已阻断）"] = "Unknown (does not prove IPv6 is blocked)",
        ["无法确认"] = "Unknown",
        ["未得到可判定的公网候选，不能证明无泄漏。"] = "No conclusive public candidate. This does not prove the absence of leaks.",
        ["HTTPS 出口未知，无法进行一致性判断。"] = "HTTPS exit is unknown; consistency cannot be assessed.",
        ["公网候选与本次 HTTPS 出口一致（仅当前浏览器）。"] = "Public candidates match this HTTPS exit (this browser only).",
        ["发现不一致的公网候选，请检查 WebRTC、IPv6 路由或多出口配置。"] = "Different public candidates found. Check WebRTC, IPv6 routes or multiple exits.",
        ["当前环境无法执行 WebRTC 测试，结果未知。"] = "WebRTC testing is unavailable in this environment. Result unknown.",
        ["平台: "] = "Platform: ", ["语言: "] = "Languages: ", ["时区: "] = "Time zone: ",
        ["屏幕: "] = "Screen: ", ["逻辑处理器: "] = "Logical processors: ",
        ["本地标识"] = "Local identity", ["Canvas 摘要: 当前环境不支持"] = "Canvas digest: Not supported in this environment",
    };
}
