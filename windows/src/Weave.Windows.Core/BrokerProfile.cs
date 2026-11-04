namespace Weave.Windows.Core;

// The broker receives connection data, never executable/config/geodata paths or a raw
// controller request. Display names, subscription URLs and unused providers stay in the GUI.
public sealed record BrokerNode(string Id, string RawName, string Protocol);
public sealed record BrokerSubscription(string Id, string ProviderYaml, BrokerNode[] Nodes);
public sealed record BrokerAppRoute(string ProcessName, RouteKind Kind, string? SubscriptionId, string? NodeId);
public sealed record BrokerSettings(RoutingMode RoutingMode, bool Ipv6Enabled, bool ChinaDirect, bool BlockUdpStun,
    DnsProfile DnsProfile, string? CustomDnsEndpoint, string DomainRules,
    string? ChainEntrySubscriptionId, string? ChainEntryNodeId);

public sealed record BrokerProfile(BrokerSubscription[] Subscriptions, BrokerAppRoute[] AppRoutes,
    string? SelectedSubscriptionId, string? SelectedNodeId, BrokerSettings Settings)
{
    public static BrokerProfile FromLocal(IReadOnlyCollection<SubscriptionRecord> subscriptions,
        IReadOnlyCollection<WindowsAppRoute> routes, string? subscriptionId, string? nodeId, WindowsNetworkOptions options)
    {
        var activeRoutes = options.RoutingMode == RoutingMode.Rule ? routes : [];
        var required = MihomoConfigBuilder.RequiredSubscriptions(subscriptions, activeRoutes, subscriptionId, options);
        return new(required.Select(item => new BrokerSubscription(item.Id, item.ProviderYaml,
                item.Nodes.Select(node => new BrokerNode(node.Id, node.RawName, node.Protocol)).ToArray())).ToArray(),
            activeRoutes.Select(route => new BrokerAppRoute(route.ProcessName, route.Target.Kind,
                route.Target.SubscriptionId, route.Target.NodeId)).ToArray(),
            options.RoutingMode == RoutingMode.Direct ? null : subscriptionId,
            options.RoutingMode == RoutingMode.Direct ? null : nodeId,
            new(options.RoutingMode, options.Ipv6Enabled, options.ChinaDirect, options.BlockUdpStun,
                options.DnsProfile, options.DnsProfile == DnsProfile.Custom ? options.CustomDnsEndpoint : null,
                options.RoutingMode == RoutingMode.Rule ? string.Join("\n", options.DomainRules.Select(rule => $"{rule.Kind},{rule.Value},{rule.Target}")) : "",
                options.RoutingMode == RoutingMode.Direct ? null : options.ChainEntrySubscriptionId,
                options.RoutingMode == RoutingMode.Direct ? null : options.ChainEntryNodeId));
    }

    public RuntimeBundle Build(string runtimeDirectory, string installedGeoDataDirectory)
    {
        if (Subscriptions is null || AppRoutes is null || Settings is null || Subscriptions.Length > 256 || AppRoutes.Length > 2000 ||
            !Enum.IsDefined(Settings.RoutingMode) || !Enum.IsDefined(Settings.DnsProfile) ||
            Settings.DomainRules is null || Settings.DomainRules.Length > 512 * 1024) throw Invalid();
        if (Settings.RoutingMode != RoutingMode.Rule && (AppRoutes.Length != 0 || Settings.DomainRules.Length != 0)) throw Invalid();
        CheckId(SelectedSubscriptionId);
        CheckId(SelectedNodeId);
        CheckId(Settings.ChainEntrySubscriptionId);
        CheckId(Settings.ChainEntryNodeId);
        if (Settings.DnsProfile == DnsProfile.Custom)
        {
            if (Settings.CustomDnsEndpoint is not { Length: > 0 and <= 2048 } dns || dns.Any(char.IsControl) ||
                !Uri.TryCreate(dns, UriKind.Absolute, out var uri) || uri.Scheme is not ("https" or "tls") ||
                string.IsNullOrEmpty(uri.Host) || uri.UserInfo.Length != 0 || uri.Fragment.Length != 0 ||
                (uri.Scheme == "tls" && (uri.AbsolutePath is not ("" or "/") || uri.Query.Length != 0))) throw Invalid();
        }
        else if (Settings.CustomDnsEndpoint is not null) throw Invalid();

        var records = new List<SubscriptionRecord>();
        long nodeCount = 0;
        foreach (var item in Subscriptions)
        {
            if (item is null || item.Id is null || item.ProviderYaml is null || item.Nodes is null || item.Nodes.Length == 0) throw Invalid();
            CheckId(item.Id);
            nodeCount += item.Nodes.Length;
            if (nodeCount > 50_000) throw Invalid();
            var nodes = new List<ProxyNode>();
            foreach (var node in item.Nodes)
            {
                if (node is null || node.Id is null || node.RawName is not { Length: > 0 and <= 4096 } ||
                    node.Protocol is not { Length: > 0 and <= 32 }) throw Invalid();
                CheckId(node.Id);
                nodes.Add(new() { Id = node.Id, Name = node.RawName, RawName = node.RawName, Protocol = node.Protocol });
            }
            records.Add(new() { Id = item.Id, Name = item.Id, Source = "inline://service", Payload = "",
                ProviderYaml = item.ProviderYaml, Nodes = nodes });
        }
        if (records.Select(record => record.Id).Distinct(StringComparer.Ordinal).Count() != records.Count) throw Invalid();
        ProxyProviderSecurity.CheckTotalSize(records);
        var routes = new List<WindowsAppRoute>();
        foreach (var route in AppRoutes)
        {
            if (route is null || route.ProcessName is not { Length: > 4 and <= 260 } name ||
                !name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) || name.Any(c => char.IsControl(c) || "/\\:*?\"<>|,".Contains(c)) ||
                !Enum.IsDefined(route.Kind)) throw Invalid();
            CheckId(route.SubscriptionId);
            CheckId(route.NodeId);
            if ((route.Kind is RouteKind.Direct or RouteKind.Block) && (route.SubscriptionId is not null || route.NodeId is not null)) throw Invalid();
            if (route.Kind == RouteKind.Automatic && route.NodeId is not null) throw Invalid();
            routes.Add(new() { ProcessName = name, DisplayName = name,
                Target = new() { Kind = route.Kind, SubscriptionId = route.SubscriptionId, NodeId = route.NodeId } });
        }
        if (routes.Select(route => route.ProcessName).Distinct(StringComparer.OrdinalIgnoreCase).Count() != routes.Count) throw Invalid();
        var options = new WindowsNetworkOptions
        {
            EnableTun = true, RoutingMode = Settings.RoutingMode, Ipv6Enabled = Settings.Ipv6Enabled,
            ChinaDirect = Settings.ChinaDirect, GeoDataDirectory = installedGeoDataDirectory, BlockUdpStun = Settings.BlockUdpStun,
            DnsProfile = Settings.DnsProfile, CustomDnsEndpoint = Settings.CustomDnsEndpoint,
            DomainRules = DomainRoute.Parse(Settings.DomainRules),
            ChainEntrySubscriptionId = Settings.ChainEntrySubscriptionId, ChainEntryNodeId = Settings.ChainEntryNodeId,
        };
        var required = MihomoConfigBuilder.RequiredSubscriptions(records, routes, SelectedSubscriptionId, options);
        if (!required.Select(record => record.Id).ToHashSet(StringComparer.Ordinal).SetEquals(records.Select(record => record.Id))) throw Invalid();
        var byId = records.ToDictionary(record => record.Id, StringComparer.Ordinal);
        _ = ProcessRuleCompiler.Compile(routes, byId);
        if (Settings.RoutingMode != RoutingMode.Direct)
        {
            if (SelectedSubscriptionId is null || !byId.TryGetValue(SelectedSubscriptionId, out var selected)) throw Invalid();
            if (SelectedNodeId is not null && selected.Nodes.All(node => node.Id != SelectedNodeId)) throw Invalid();
            if (ProxyChain.Enabled(options)) _ = ProxyChain.Build(byId, selected, SelectedNodeId, options);
        }
        else if (records.Count != 0 || Settings.ChainEntrySubscriptionId is not null || Settings.ChainEntryNodeId is not null) throw Invalid();
        return new MihomoConfigBuilder().Build(records, routes, SelectedSubscriptionId, SelectedNodeId, options, runtimeDirectory);
    }

    private static void CheckId(string? value)
    {
        if (value is not null && (value.Length is < 1 or > 80 || value.Any(c => !char.IsAsciiLetterOrDigit(c) && c is not ('_' or '-')))) throw Invalid();
    }

    private static InvalidDataException Invalid() => new("Invalid service connection profile.");
}

// This is the only runtime description that crosses back to an unprivileged client.
public sealed record BrokerRuntimeInfo(int MixedPort, bool Ipv6Enabled, IReadOnlyDictionary<string, int> ProviderNodeCounts)
{
    public static BrokerRuntimeInfo FromBundle(RuntimeBundle bundle, bool ipv6Enabled) =>
        new(bundle.MixedPort, ipv6Enabled, bundle.ProviderNodeCounts);
}
