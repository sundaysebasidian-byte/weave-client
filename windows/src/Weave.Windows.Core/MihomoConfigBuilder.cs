using System.Text;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;

namespace Weave.Windows.Core;

public sealed class MihomoConfigBuilder
{
    public static void ValidateOptions(WindowsNetworkOptions options)
    {
        _ = DnsEndpoints(options).ToArray();
    }

    public RuntimeBundle Build(
        IReadOnlyCollection<SubscriptionRecord> subscriptions,
        IReadOnlyCollection<WindowsAppRoute> routes,
        string? selectedSubscriptionId,
        string? selectedNodeId,
        WindowsNetworkOptions options,
        string runtimeDirectory)
    {
        ValidateOptions(options);
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

        var mixedPort = AvailableLocalPort();
        var controlPort = AvailableLocalPort();
        while (controlPort == mixedPort)
        {
            controlPort = AvailableLocalPort();
        }
        var controlSecret = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        var configPath = Path.Combine(runtimeDirectory, "config.yaml");
        var yaml = BuildYaml(usable, byId, routes, selectedSubscription, selectedNodeId, options,
            mixedPort, controlPort, controlSecret);
        File.WriteAllText(configPath, yaml, new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));
        return new RuntimeBundle
        {
            Directory = runtimeDirectory,
            ConfigPath = configPath,
            MixedPort = mixedPort,
            ControlPort = controlPort,
            ControlSecret = controlSecret,
        };
    }

    private static string BuildYaml(
        IReadOnlyList<SubscriptionRecord> subscriptions,
        IReadOnlyDictionary<string, SubscriptionRecord> byId,
        IReadOnlyCollection<WindowsAppRoute> routes,
        SubscriptionRecord selectedSubscription,
        string? selectedNodeId,
        WindowsNetworkOptions options,
        int mixedPort,
        int controlPort,
        string controlSecret)
    {
        var selectedGroup = selectedNodeId is null
            ? AutomaticGroup(selectedSubscription)
            : FixedGroup(selectedSubscription, selectedNodeId);
        var builder = new StringBuilder();
        builder.AppendLine($"mixed-port: {mixedPort}");
        builder.AppendLine("allow-lan: false");
        builder.AppendLine($"external-controller: 127.0.0.1:{controlPort}");
        builder.AppendLine($"secret: {YamlString(controlSecret)}");
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
            DnsProfile.Custom => new[] { ValidateCustomDnsEndpoint(options.CustomDnsEndpoint) },
            _ => new[] { "https://dns.alidns.com/dns-query", "https://doh.pub/dns-query" },
        };
    }

    private static string ValidateCustomDnsEndpoint(string? endpoint)
    {
        if (!Uri.TryCreate(endpoint, UriKind.Absolute, out var uri) ||
            uri.Scheme is not ("https" or "tls") ||
            string.IsNullOrWhiteSpace(uri.Host) ||
            !string.IsNullOrEmpty(uri.UserInfo) ||
            !string.IsNullOrEmpty(uri.Fragment))
        {
            throw new InvalidDataException("自定义 DNS 必须是有效的 HTTPS DoH 或 TLS DoT 地址");
        }

        return endpoint!;
    }

    public static string ProviderName(SubscriptionRecord subscription) => $"provider-{subscription.Id}";

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

    private static int AvailableLocalPort()
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        try
        {
            return ((IPEndPoint)listener.LocalEndpoint).Port;
        }
        finally
        {
            listener.Stop();
        }
    }
}
