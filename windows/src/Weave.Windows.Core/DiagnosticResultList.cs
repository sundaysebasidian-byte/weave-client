using System.Collections.ObjectModel;

namespace Weave.Windows.Core;

/// <summary>Fixed slots: arrival order must never reorder evidence under the pointer.</summary>
public sealed class DiagnosticResultList : ObservableCollection<ProbeResult>
{
    public DiagnosticResultList() : base(new[] { "代理出口 IPv4", "代理出口 IPv6" }
        .Concat(NetworkDiagnostics.Targets.Select(target => target.Name))
        .Select(name => new ProbeResult(name, "", Pending: true))) { }

    public int CompletedCount => this.Count(row => !row.Pending);

    // Called on the UI thread. Unknown and duplicate rows cannot add extra slots or inflate progress.
    public void Record(ProbeResult result)
    {
        if (result.Pending) return;
        for (var index = 0; index < Count; index++)
        {
            if (this[index].Name != result.Name) continue;
            if (this[index].Pending) this[index] = result;
            return;
        }
    }
}
