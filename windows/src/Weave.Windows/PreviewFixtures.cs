using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>
/// Synthetic content for layout reviews, enabled only by environment variables (WEAVE_PREVIEW_DEMO=1, WEAVE_PREVIEW_STATE=connected).
/// Nothing here is read from or written to the user's data, and no network is involved.
/// </summary>
internal static class PreviewFixtures
{
    public static bool Demo { get; } = Environment.GetEnvironmentVariable("WEAVE_PREVIEW_DEMO") == "1";
    public static bool Connected { get; } = Demo && Environment.GetEnvironmentVariable("WEAVE_PREVIEW_STATE") == "connected";

    private static readonly (string Flag, string Name, string Protocol, int? Delay)[] Main =
    {
        ("\U0001F1EF\U0001F1F5", "东京 03", "trojan", 24), ("\U0001F1EF\U0001F1F5", "东京 01", "vmess", 38),
        ("\U0001F1EF\U0001F1F5", "大阪 02", "hysteria2", 46), ("\U0001F1F8\U0001F1EC", "新加坡 02", "hysteria2", 51),
        ("\U0001F1F8\U0001F1EC", "新加坡 04", "trojan", 63), ("\U0001F1F0\U0001F1F7", "首尔 01", "ss", 88),
        ("\U0001F1ED\U0001F1F0", "香港 05", "vmess", 96), ("\U0001F1FA\U0001F1F8", "洛杉矶 04", "vmess", 132),
        ("\U0001F1FA\U0001F1F8", "圣何塞 02", "trojan", 148), ("\U0001F1FA\U0001F1F8", "西雅图 01", "vmess", 171),
        ("\U0001F1E9\U0001F1EA", "法兰克福 01", "trojan", 245), ("\U0001F1E6\U0001F1FA", "悉尼 02", "ss", null),
    };

    private static readonly (string Flag, string Name, string Protocol, int? Delay)[] Backup =
    {
        ("\U0001F1EF\U0001F1F5", "大阪 01", "trojan", 41), ("\U0001F1F8\U0001F1EC", "新加坡 07", "vmess", 66),
        ("\U0001F1FA\U0001F1F8", "圣何塞 03", "trojan", 148), ("\U0001F1EC\U0001F1E7", "伦敦 01", "vmess", 212),
    };

    public static IReadOnlyList<SubscriptionRecord> Subscriptions() => new[]
    {
        Build("demo-main", "主订阅", Main, TimeSpan.FromHours(2)),
        Build("demo-backup", "备用线路", Backup, TimeSpan.FromDays(1)),
    };

    /// <summary>Two earlier measurements per node so rows show a trend and subscriptions show their health ring.</summary>
    public static void SeedHistory(NodeHistory history, IEnumerable<SubscriptionRecord> records)
    {
        foreach (var record in records)
        {
            var source = record.Id == "demo-main" ? Main : Backup;
            for (var i = 0; i < record.Nodes.Count; i++)
            {
                var delay = source[i].Delay;
                history.Record(record.Nodes[i].Id, delay is null ? null : delay + 9);
                history.Record(record.Nodes[i].Id, delay is null ? null : Math.Max(8, delay.Value - 4 + i % 3 * 3));
                history.Record(record.Nodes[i].Id, delay);
            }
        }
    }

    private static SubscriptionRecord Build(string id, string name, (string Flag, string Name, string Protocol, int? Delay)[] nodes, TimeSpan age) => new()
    {
        Id = id, Name = name, Source = "https://example.invalid/" + id, Payload = "", ProviderYaml = "",
        UpdatedAt = DateTimeOffset.UtcNow - age,
        Nodes = nodes.Select((node, index) => new ProxyNode
        {
            Id = $"{id}-{index}", Name = NodeName.Core(node.Flag + " " + node.Name), RawName = node.Flag + " " + node.Name,
            Protocol = node.Protocol, Index = index,
        }).ToList(),
    };
}
