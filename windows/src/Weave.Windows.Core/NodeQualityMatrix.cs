namespace Weave.Windows.Core;

public sealed record NodeQualityRow(NodeHealthSnapshot Health, int? StabilityScore, string StabilityLabel)
{
    public string Name => Health.Name;
    public string Protocol => Health.Protocol;
    public string ScoreText => StabilityScore is { } score
        ? $"稳定度 {score} 分 · {StabilityLabel}"
        : "稳定度未完成";
    public string MeasurementsText =>
        $"中位 {Format(Health.MedianLatencyMs)} · P95 {Format(Health.P95LatencyMs)} · " +
        $"抖动 {Format(Health.JitterMs)} · 失败 {Health.ProbeFailurePercent}% " +
        $"({Health.SuccessfulSamples}/{Health.TotalSamples} 轮成功)";

    private static string Format(int? milliseconds) => milliseconds is { } value ? $"{value} ms" : "—";
}

public static class NodeQualityMatrix
{
    public static IReadOnlyList<NodeQualityRow> Build(IEnumerable<NodeHealthSnapshot> snapshots) => snapshots
        .Select(snapshot =>
        {
            var score = Score(snapshot);
            var label = score switch
            {
                null => "未完成",
                >= 85 => "稳定",
                >= 65 => "一般",
                _ => "波动",
            };
            return new NodeQualityRow(snapshot, score, label);
        })
        .OrderBy(row => row.StabilityScore is null)
        .ThenByDescending(row => row.StabilityScore ?? -1)
        .ThenBy(row => row.Health.MedianLatencyMs ?? int.MaxValue)
        .ThenBy(row => row.Name, StringComparer.Ordinal)
        .ToArray();

    public static int? Score(NodeHealthSnapshot snapshot)
    {
        if (snapshot.MedianLatencyMs is not { } latency) return null;
        var score = 100 - latency / 12 - (snapshot.JitterMs ?? 0) / 4 - snapshot.ProbeFailurePercent * 2;
        return Math.Clamp(score, 0, 100);
    }
}
