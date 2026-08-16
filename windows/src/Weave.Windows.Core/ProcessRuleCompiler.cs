namespace Weave.Windows.Core;

public static class ProcessRuleCompiler
{
    public static IReadOnlyList<string> Compile(
        IEnumerable<WindowsAppRoute> routes,
        IReadOnlyDictionary<string, SubscriptionRecord> subscriptions)
    {
        var rules = new List<string>();
        foreach (var route in routes.OrderBy(item => item.ProcessName, StringComparer.OrdinalIgnoreCase))
        {
            if (string.IsNullOrWhiteSpace(route.ProcessName) || route.ProcessName.Contains(','))
            {
                throw new InvalidDataException($"应用进程名无效：{route.ProcessName}");
            }

            var target = CompileTarget(route.Target, subscriptions, route.DisplayName);
            rules.Add($"PROCESS-NAME,{Escape(route.ProcessName.Trim())},{Escape(target)}");
        }

        return rules;
    }

    public static string CompileTarget(
        RouteTarget target,
        IReadOnlyDictionary<string, SubscriptionRecord> subscriptions,
        string owner)
    {
        return target.Kind switch
        {
            RouteKind.Direct => "DIRECT",
            RouteKind.Block => "REJECT",
            RouteKind.Automatic => AutomaticGroup(target, subscriptions, owner),
            RouteKind.FixedNode => FixedGroup(target, subscriptions, owner),
            _ => throw new InvalidDataException($"{owner} 的分流目标无效"),
        };
    }

    public static string AutomaticGroup(RouteTarget target, IReadOnlyDictionary<string, SubscriptionRecord> subscriptions, string owner)
    {
        var id = RequireSubscription(target, subscriptions, owner);
        return $"sub-{id}-auto";
    }

    public static string FixedGroup(RouteTarget target, IReadOnlyDictionary<string, SubscriptionRecord> subscriptions, string owner)
    {
        var subscription = RequireSubscriptionRecord(target, subscriptions, owner);
        var nodeId = target.NodeId ?? throw new InvalidDataException($"{owner} 没有指定节点");
        if (subscription.Nodes.All(node => !node.Id.Equals(nodeId, StringComparison.Ordinal)))
        {
            throw new InvalidDataException($"{owner} 指向的节点已经不存在");
        }

        return $"node-{subscription.Id}-{nodeId}";
    }

    private static string RequireSubscription(RouteTarget target, IReadOnlyDictionary<string, SubscriptionRecord> subscriptions, string owner)
    {
        _ = RequireSubscriptionRecord(target, subscriptions, owner);
        return target.SubscriptionId!;
    }

    private static SubscriptionRecord RequireSubscriptionRecord(RouteTarget target, IReadOnlyDictionary<string, SubscriptionRecord> subscriptions, string owner)
    {
        var id = target.SubscriptionId ?? throw new InvalidDataException($"{owner} 没有指定订阅");
        if (!subscriptions.TryGetValue(id, out var subscription) || subscription.Nodes.Count == 0)
        {
            throw new InvalidDataException($"{owner} 指向的订阅不可用");
        }

        return subscription;
    }

    private static string Escape(string value) => value.Replace("\\", "\\\\").Replace(",", "\\,");
}
