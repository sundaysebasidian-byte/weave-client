namespace Weave.Windows.Core;

public sealed record SubscriptionUpdate(
    SubscriptionRecord Record,
    int AddedNodes,
    int RemovedNodes,
    int RetainedNodes);

public static class SubscriptionUpdateGuard
{
    public static SubscriptionUpdate Prepare(
        SubscriptionRecord current,
        SubscriptionRecord candidate,
        IEnumerable<WindowsAppRoute> routes)
    {
        if (!string.Equals(current.Source, candidate.Source, StringComparison.Ordinal))
        {
            throw new InvalidDataException("刷新来源与原订阅不一致");
        }

        if (current.Nodes.Count >= 4 && candidate.Nodes.Count * 2 < current.Nodes.Count)
        {
            throw new InvalidDataException(
                $"刷新后节点数从 {current.Nodes.Count} 降至 {candidate.Nodes.Count}，已保留原订阅；请核查来源后重试");
        }

        var oldByKey = current.Nodes
            .GroupBy(NodeKey, StringComparer.Ordinal)
            .Where(group => group.Count() == 1)
            .ToDictionary(group => group.Key, group => group.Single(), StringComparer.Ordinal);
        var retained = 0;
        var nodes = candidate.Nodes.Select(node =>
        {
            if (!oldByKey.TryGetValue(NodeKey(node), out var old))
            {
                return node;
            }

            retained++;
            return new ProxyNode
            {
                Id = old.Id,
                Name = node.Name,
                RawName = node.RawName,
                Protocol = node.Protocol,
                Index = node.Index,
            };
        }).ToList();

        var liveIds = nodes.Select(node => node.Id).ToHashSet(StringComparer.Ordinal);
        var affectedRoutes = routes
            .Where(route => route.Target.Kind == RouteKind.FixedNode &&
                            route.Target.SubscriptionId == current.Id &&
                            !liveIds.Contains(route.Target.NodeId ?? string.Empty))
            .Select(route => route.ProcessName)
            .ToList();
        if (affectedRoutes.Count > 0)
        {
            throw new InvalidDataException(
                $"刷新会使固定节点分流失效：{string.Join("、", affectedRoutes)}；请先修改这些规则");
        }

        var updated = new SubscriptionRecord
        {
            Id = current.Id,
            Name = current.Name,
            Source = current.Source,
            Payload = candidate.Payload,
            ProviderYaml = candidate.ProviderYaml,
            Nodes = nodes,
            UpdatedAt = candidate.UpdatedAt,
        };
        return new SubscriptionUpdate(updated, nodes.Count - retained, current.Nodes.Count - retained, retained);
    }

    private static string NodeKey(ProxyNode node) => $"{node.RawName}\n{node.Protocol}";
}
