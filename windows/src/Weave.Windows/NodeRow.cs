using System.ComponentModel;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>One node line in the virtualised node list; bound with x:Bind so rows never go through reflection.</summary>
public sealed class NodeRow : INotifyPropertyChanged
{
    public NodeRow(ProxyNode node, bool tested = false, int? delay = null) { Node = node; Tested = tested; Delay = delay; }
    public event PropertyChangedEventHandler? PropertyChanged;
    public ProxyNode Node { get; }
    public bool Tested { get; }
    public int? Delay { get; }
    public string Name => Node.Name;
    public string Protocol => Node.Protocol.ToUpperInvariant();
    public string DelayText => !Tested ? "" : Delay is { } ms ? $"{ms} ms" : L.T("超时 / 不可达");
    public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(nameof(DelayText)));
}
