using System.Text;

namespace Weave.Windows.Core;

public sealed class MihomoConfigBuilder
{
    public RuntimeBundle Build(
        IReadOnlyCollection<SubscriptionRecord> subscriptions,
        IReadOnlyCollection<WindowsAppRoute> routes,
        string? selectedSubscriptionId,
        string? selectedNodeId,
        WindowsNetworkOptions options,
        string runtimeDirectory)
    {
        var usable = subscriptions.Where(item => item.Nodes.Count > 0).ToList();
        if (usable.Count == 0)
        {
            throw new InvalidDataException("没有可用订阅，请先导入 Clash/Mihomo 节点");
        }

        var byId = usable.ToDictionary(item => item.Id, StringComparer.Ordinal);
        if (selectedSubscriptionId is null || !byId.TryGetValue(selectedSubscriptionId, out var selectedSubscription))
        {
            throw new InvalidDataException("请先选择订阅");
        }

        if (selectedNodeId is not null && selectedSubscription.Nodes.All(node => node.Id != selectedNodeId))
        {
            throw new InvalidDataException("所选节点已不存在，请重新选择");
        }

        if (Directory.Exists(runtimeDirectory))
        {
            Directory.Delete(runtimeDirectory, recursive: true);
        }

        var providersDirectory = Path.Combine(runtimeDirectory, "providers");
        Directory.CreateDirectory(providersDirectory);
        foreach (var subscription in usable)
        {
            var providerPath = Path.Combine(providersDirectory, ProviderFileName(subscription));
            File.WriteAllText(providerPath, subscription.ProviderYaml, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
        }

        var configPath = Path.Combine(runtimeDirectory, "config.yaml");
        var yaml = BuildYaml(usable, byId, routes, selectedSubscription, selectedNodeId, options);
        File.WriteAllText(configPath, yaml, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
        return new RuntimeBundle
        {
            Directory = runtimeDirectory,
            ConfigPath = configPath,
            MixedPort = 7890,
        };
    }

    private static string BuildYaml(
        IReadOnlyList<SubscriptionRecord> subscriptions,
        IReadOnlyDictionary<string, SubscriptionRecord> byId,
        IReadOnlyCollection<WindowsAppRoute> routes,
        SubscriptionRecord selectedSubscription,
        string? selectedNodeId,
        WindowsNetworkOptions options)
    {
        var selectedGroup = selectedNodeId is null
            ? AutomaticGroup(selectedSubscription)
            : FixedGroup(selectedSubscription, selectedNodeId);
        var builder = new StringBuilder();
        builder.AppendLine("mixed-port: 7890");
        builder.AppendLine("allow-lan: false");
        builder.AppendLine("mode: rule");
        builder.AppendLine("log-level: warning");
        builder.AppendLine($"ipv6: {options.Ipv6Enabled.ToString().ToLowerInvariant()}");
        builder.AppendLine("find-process-mode: strict");
        builder.AppendLine("unified-delay: true");
        builder.AppendLine("tcp-concurrent: true");
        builder.AppendLine("profile:");
        builder.AppendLine("  store-selected: false");

        if (options.EnableTun)
        {
            builder.AppendLine("tun:");
            builder.AppendLine("  enable: true");
            builder.AppendLine("  stack: mixed");
            builder.AppendLine("  auto-route: true");
            builder.AppendLine("  auto-detect-interface: true");
            builder.AppendLine("  strict-route: true");
            builder.AppendLine("  dns-hijack:");
            builder.AppendLine("    - any:53");
            builder.AppendLine("    - tcp://any:53");
        }

        builder.AppendLine("dns:");
        builder.AppendLine("  enable: true");
        builder.AppendLine($"  ipv6: {options.Ipv6Enabled.ToString().ToLowerInvariant()}");
        builder.AppendLine("  enhanced-mode: fake-ip");
        builder.AppendLine("  fake-ip-range: 198.18.0.1/16");
        builder.AppendLine("  fake-ip-filter:");
        builder.AppendLine("    - '*.lan'");
        builder.AppendLine("    - '*.local'");
        builder.AppendLine("    - '*.home.arpa'");
        builder.AppendLine("  nameserver:");
        foreach (var endpoint in DnsEndpoints(options))
        {
            builder.AppendLine($"    - {YamlString(endpoint)}");
        }

        builder.AppendLine("  proxy-server-nameserver:");
        foreach (var endpoint in DnsEndpoints(options))
        {
            builder.AppendLine($"    - {YamlString(endpoint)}");
        }

        builder.AppendLine("proxies:");
        builder.AppendLine("  - name: DIRECT");
        builder.AppendLine("    type: direct");
        builder.AppendLine("proxy-providers:");
        foreach (var subscription in subscriptions)
        {
            builder.AppendLine($"  {YamlString(ProviderName(subscription))}:");
            builder.AppendLine("    type: file");
            builder.AppendLine($"    path: {YamlString($"providers/{ProviderFileName(subscription)}")}");
            builder.AppendLine("    health-check:");
            builder.AppendLine("      enable: true");
            builder.AppendLine("      url: https://www.gstatic.com/generate_204");
            builder.AppendLine("      interval: 300");
        }

        builder.AppendLine("proxy-groups:");
        foreach (var subscription in subscriptions)
        {
            builder.AppendLine($"  - name: {YamlString(AutomaticGroup(subscription))}");
            builder.AppendLine("    type: url-test");
            builder.AppendLine("    use:");
            builder.AppendLine($"      - {YamlString(ProviderName(subscription))}");
            builder.AppendLine("    url: https://www.gstatic.com/generate_204");
            builder.AppendLine("    interval: 180");
            builder.AppendLine("    timeout: 5000");
            builder.AppendLine("    max-failed-times: 2");
        }

        var fixedTargets = routes
            .Select(route => route.Target)
            .Where(target => target.Kind == RouteKind.FixedNode)
            .ToList();
        if (selectedNodeId is not null)
        {
            fixedTargets.Add(RouteTarget.Fixed(selectedSubscription.Id, selectedNodeId));
        }

        fixedTargets = fixedTargets
            .GroupBy(target => $"{target.SubscriptionId}:{target.NodeId}", StringComparer.Ordinal)
            .Select(group => group.First())
            .ToList();
        foreach (var target in fixedTargets)
        {
            var targetSubscription = target.SubscriptionId is not null && byId.TryGetValue(target.SubscriptionId, out var candidate)
                ? candidate
                : throw new InvalidDataException("应用分流引用的订阅不存在");
            var targetNodeId = target.NodeId ?? throw new InvalidDataException("应用分流没有指定节点");
            var node = targetSubscription.Nodes.FirstOrDefault(item => item.Id == targetNodeId)
                ?? throw new InvalidDataException("应用分流引用的节点不存在");
            builder.AppendLine($"  - name: {YamlString(FixedGroup(targetSubscription, targetNodeId))}");
            builder.AppendLine("    type: select");
            builder.AppendLine("    use:");
            builder.AppendLine($"      - {YamlString(ProviderName(targetSubscription))}");
            builder.AppendLine($"    filter: {YamlString($"^{RegexEscape(node.RawName)}$")}");
        }

        builder.AppendLine("  - name: DEFAULT");
        builder.AppendLine("    type: select");
        builder.AppendLine("    proxies:");
        builder.AppendLine($"      - {YamlString(selectedGroup)}");
        foreach (var subscription in subscriptions.Where(item => item.Id != selectedSubscription.Id))
        {
            builder.AppendLine($"      - {YamlString(AutomaticGroup(subscription))}");
        }
        builder.AppendLine("      - DIRECT");

        builder.AppendLine("rules:");
        foreach (var rule in ProcessRuleCompiler.Compile(routes, byId))
        {
            builder.AppendLine($"  - {YamlString(rule)}");
        }

        if (options.BlockUdpStun)
        {
            builder.AppendLine("  - DST-PORT,3478-3479,REJECT");
            builder.AppendLine("  - DST-PORT,19302-19309,REJECT");
        }

        builder.AppendLine("  - MATCH,DEFAULT");
        return builder.ToString();
    }

    private static IEnumerable<string> DnsEndpoints(WindowsNetworkOptions options)
    {
        return options.DnsProfile switch
        {
            DnsProfile.AdBlock => new[] { "https://dns.adguard-dns.com/dns-query" },
            DnsProfile.Family => new[] { "https://family.adguard-dns.com/dns-query" },
            DnsProfile.Custom when Uri.TryCreate(options.CustomDnsEndpoint, UriKind.Absolute, out var custom) &&
                                   custom.Scheme is "https" or "tls" => new[] { options.CustomDnsEndpoint! },
            _ => new[] { "https://dns.alidns.com/dns-query", "https://doh.pub/dns-query" },
        };
    }

    private static string ProviderName(SubscriptionRecord subscription) => $"provider-{subscription.Id}";

    private static string ProviderFileName(SubscriptionRecord subscription) => $"{ProviderName(subscription)}.yaml";

    private static string AutomaticGroup(SubscriptionRecord subscription) => $"sub-{subscription.Id}-auto";

    private static string FixedGroup(SubscriptionRecord subscription, string nodeId) => $"node-{subscription.Id}-{nodeId}";

    private static string RegexEscape(string value)
    {
        const string meta = @"\.^$|?*+()[]{}";
        var builder = new StringBuilder();
        foreach (var character in value)
        {
            if (meta.IndexOf(character) >= 0)
            {
                builder.Append('\\');
            }

            builder.Append(character);
        }

        return builder.ToString();
    }

    private static string YamlString(string value) => $"'{value.Replace("'", "''", StringComparison.Ordinal)}'";
}
