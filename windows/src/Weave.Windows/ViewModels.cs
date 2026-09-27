using System.ComponentModel;
using System.Runtime.CompilerServices;
using Microsoft.UI.Xaml.Media;
using Weave.Windows.Core;
using Windows.UI;

namespace Weave.Windows;

public sealed class NodeItem : INotifyPropertyChanged
{
    // Mid-tone status colours read on both light and dark surfaces.
    private static readonly SolidColorBrush StableBrush = new(Color.FromArgb(0xFF, 0x2E, 0xA0, 0x43));
    private static readonly SolidColorBrush FairBrush = new(Color.FromArgb(0xFF, 0xD4, 0xA0, 0x17));
    private static readonly SolidColorBrush PoorBrush = new(Color.FromArgb(0xFF, 0xE5, 0x53, 0x4B));
    private static readonly SolidColorBrush UnknownBrush = new(Color.FromArgb(0x80, 0x8B, 0x94, 0x9E));

    private NodeQualityRow? _quality;
    private bool _isTesting;
    private bool _canProbe;
    private bool _isExit;

    public NodeItem(ProxyNode node)
    {
        Node = node;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public ProxyNode Node { get; }

    public string Name => Node.DisplayName;

    public string Protocol => Node.Protocol.ToUpperInvariant();

    public NodeHealthSnapshot? Health => _quality?.Health;

    public int? Score => _quality?.StabilityScore;

    public string MedianText => UiFormat.Latency(Health?.MedianLatencyMs);

    public string P95Text => UiFormat.Latency(Health?.P95LatencyMs);

    public string JitterText => UiFormat.Latency(Health?.JitterMs);

    public string FailureText => Health is { } health ? $"{health.ProbeFailurePercent}%" : "—";

    public string ScoreText => _isTesting
        ? "测试中…"
        : _quality is null
            ? "未测试"
            : _quality.StabilityScore is { } score
                ? $"{score} · {_quality.StabilityLabel}"
                : "不可用";

    public Brush ScoreBrush => _isTesting || _quality is null
        ? UnknownBrush
        : _quality.StabilityScore switch
        {
            null => PoorBrush,
            >= 85 => StableBrush,
            >= 65 => FairBrush,
            _ => PoorBrush,
        };

    public bool IsTesting
    {
        get => _isTesting;
        set
        {
            if (_isTesting == value) return;
            _isTesting = value;
            Raise();
            Raise(nameof(ScoreText));
            Raise(nameof(ScoreBrush));
        }
    }

    public bool CanProbe
    {
        get => _canProbe;
        set
        {
            if (_canProbe == value) return;
            _canProbe = value;
            Raise();
        }
    }

    public bool IsExit
    {
        get => _isExit;
        set
        {
            if (_isExit == value) return;
            _isExit = value;
            Raise();
            Raise(nameof(ExitMarkerOpacity));
        }
    }

    public double ExitMarkerOpacity => _isExit ? 1 : 0;

    public void SetQuality(NodeQualityRow? quality)
    {
        _quality = quality;
        Raise(nameof(Health));
        Raise(nameof(Score));
        Raise(nameof(MedianText));
        Raise(nameof(P95Text));
        Raise(nameof(JitterText));
        Raise(nameof(FailureText));
        Raise(nameof(ScoreText));
        Raise(nameof(ScoreBrush));
    }

    private void Raise([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

public sealed class RouteItem
{
    public required WindowsAppRoute Route { get; init; }

    public string ProcessName => Route.ProcessName;

    public required string KindText { get; init; }

    public required string KindGlyph { get; init; }

    public required string TargetText { get; init; }

    public static RouteItem From(WindowsAppRoute route, IEnumerable<SubscriptionRecord> subscriptions)
    {
        var subscription = subscriptions.FirstOrDefault(item => item.Id == route.Target.SubscriptionId);
        var subscriptionName = subscription?.Name ?? "订阅已不存在";
        return route.Target.Kind switch
        {
            RouteKind.Automatic => new RouteItem
            {
                Route = route,
                KindText = "自动测速",
                KindGlyph = "",
                TargetText = $"{subscriptionName} · 延迟最优",
            },
            RouteKind.FixedNode => new RouteItem
            {
                Route = route,
                KindText = "固定节点",
                KindGlyph = "",
                TargetText = $"{subscriptionName} · " +
                    (subscription?.Nodes.FirstOrDefault(node => node.Id == route.Target.NodeId)?.DisplayName ?? "节点已不存在"),
            },
            RouteKind.Direct => new RouteItem
            {
                Route = route,
                KindText = "直连",
                KindGlyph = "",
                TargetText = "不经过代理，直接访问",
            },
            _ => new RouteItem
            {
                Route = route,
                KindText = "阻止",
                KindGlyph = "",
                TargetText = "拒绝该应用的所有连接",
            },
        };
    }
}

public static class UiFormat
{
    public static string Latency(int? milliseconds) => milliseconds is { } value ? $"{value} ms" : "—";

    public static string Bytes(long bytes)
    {
        if (bytes < 1024) return $"{bytes} B";
        if (bytes < 1024L * 1024) return $"{bytes / 1024d:0.0} KiB";
        if (bytes < 1024L * 1024 * 1024) return $"{bytes / (1024d * 1024):0.0} MiB";
        return $"{bytes / (1024d * 1024 * 1024):0.00} GiB";
    }

    public static string Rate(long bytesPerSecond) => $"{Bytes(bytesPerSecond)}/s";

    public static string Duration(TimeSpan duration) =>
        $"{(int)duration.TotalHours:00}:{duration.Minutes:00}:{duration.Seconds:00}";

    public static string SourceGlyph(bool remote) => remote ? "" : "";

    public static string SubscriptionDetail(int nodeCount, DateTimeOffset updatedAt, string source)
    {
        var origin = Uri.TryCreate(source, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps
            ? uri.Host
            : "本地导入";
        return $"{nodeCount} 个节点 · {origin} · 更新于 {updatedAt.ToLocalTime():yyyy-MM-dd HH:mm}";
    }
}
