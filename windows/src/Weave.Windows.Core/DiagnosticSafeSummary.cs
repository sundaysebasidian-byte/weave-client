using System.Text;

namespace Weave.Windows.Core;

/// <summary>Only typed, allowlisted evidence is exported. Never copy raw Result or error text.</summary>
public static class DiagnosticSafeSummary
{
    public static string Build(IEnumerable<ProbeResult> evidence, bool historical)
    {
        var rows = evidence.Take(64).ToArray();
        var text = new StringBuilder("Weave Windows · diagnostic summary v1\n")
            .Append("historical=").Append(historical).AppendLine();
        foreach (var (name, ipv6) in new[] { ("代理出口 IPv4", false), ("代理出口 IPv6", true) })
        {
            var row = rows.FirstOrDefault(item => item.Name == name);
            text.Append(ipv6 ? "ipv6" : "ipv4").Append("_measured=").Append(row is not null).Append("; present=")
                .Append(row?.ExitVerified == true).Append("; failure=").Append(row?.Failure?.ToString() ?? "none").AppendLine();
        }
        foreach (var target in NetworkDiagnostics.Targets)
        {
            var row = rows.FirstOrDefault(item => item.Name == target.Name);
            if (row is null) continue;
            text.Append(target.Name).Append(": ").Append(row.EndpointVerified ? "VERIFIED" : "UNCONFIRMED")
                .Append("; http=").Append(row.HttpStatus is >= 100 and <= 599 ? row.HttpStatus.Value.ToString() : "unknown")
                .Append("; ms=").Append(row.Milliseconds is >= 0 and <= 60_000 ? row.Milliseconds.Value.ToString() : "unknown")
                .Append("; failure=").Append(row.Failure?.ToString() ?? "none").AppendLine();
        }
        return text.Append("Snapshot only. No IP addresses, node/subscription names, credentials, fingerprints or raw errors. Not proof of leak-free networking.").ToString();
    }
}
