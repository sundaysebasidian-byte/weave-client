namespace Weave.Windows.Core;

public sealed record NodeHealthSnapshot(
    string Name,
    string Protocol,
    int? MedianLatencyMs,
    int? P95LatencyMs,
    int? JitterMs,
    int ProbeFailurePercent,
    int SuccessfulSamples,
    int TotalSamples)
{
    public static NodeHealthSnapshot FromSamples(ProxyNode node, IReadOnlyList<int?> samples)
    {
        if (samples.Count == 0) throw new ArgumentException("测速样本不能为空", nameof(samples));
        var successes = samples.Where(value => value is >= 1 and <= 10000)
            .Select(value => value!.Value).ToArray();
        var ordered = successes.Order().ToArray();
        var jitter = successes.Length < 2
            ? (int?)null
            : (int)Math.Round(successes.Zip(successes.Skip(1), (first, second) => Math.Abs(second - first))
                .Average(), MidpointRounding.AwayFromZero);

        return new NodeHealthSnapshot(
            node.RawName,
            node.Protocol,
            ordered.Length == 0 ? null : ordered[ordered.Length / 2],
            ordered.Length == 0 ? null : ordered[(int)Math.Ceiling(ordered.Length * 0.95) - 1],
            jitter,
            (samples.Count - successes.Length) * 100 / samples.Count,
            successes.Length,
            samples.Count);
    }
}
