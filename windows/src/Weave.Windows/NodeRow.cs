using System.ComponentModel;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>
/// One node line in the virtualised lists; bound with x:Bind so rows never go through reflection. The same row type serves the
/// full node table and the quick exit grid on the connection page, where <see cref="IsAuto"/> stands for "lowest latency".
/// </summary>
public sealed class NodeRow : INotifyPropertyChanged
{
    private string? _region;

    public NodeRow(ProxyNode? node, bool tested = false, int? delay = null, IReadOnlyList<int?>? trend = null, bool isAuto = false)
    {
        Node = node; Tested = tested; Delay = delay; Trend = trend ?? Array.Empty<int?>(); IsAuto = isAuto;
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    public ProxyNode? Node { get; }
    public bool IsAuto { get; }
    public bool Tested { get; }
    public int? Delay { get; }
    public IReadOnlyList<int?> Trend { get; }

    public string Name => IsAuto ? L.T("自动") : Node!.Name;
    public string Protocol => IsAuto ? L.T("最低延迟") : Node!.Protocol.ToUpperInvariant();
    public string Region => IsAuto ? "" : _region ??= NodeRegion.Code(Node!);
    /// <summary>Two letters in the small chip; "··" when the region is unknown, so the column never looks broken.</summary>
    public string RegionChip => IsAuto ? "" : Region.Length == 0 ? "··" : Region;
    public bool IsNode => !IsAuto;

    public int Level => LatencyScale.Level(Tested ? Delay : null);
    public bool HasDelay => Tested && Delay.HasValue;
    public bool IsLost => Tested && Delay is null;
    public string DelayNumber => HasDelay ? Delay!.Value.ToString() : "";
    public string DelayText => !Tested ? "" : Delay is { } ms ? $"{ms} ms" : L.T("超时 / 不可达");
    public string LostText => L.T("超时");

    public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(null));
}
