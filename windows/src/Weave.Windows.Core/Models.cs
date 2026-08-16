using System.Text.Json.Serialization;

namespace Weave.Windows.Core;

public enum RouteKind
{
    Automatic,
    FixedNode,
    Direct,
    Block,
}

public enum DnsProfile
{
    Privacy,
    AdBlock,
    Family,
    Custom,
}

public sealed class ProxyNode
{
    public required string Id { get; init; }
    public required string Name { get; init; }
    public string RawName { get; init; } = "";
    public required string Protocol { get; init; }
    public int Index { get; init; }

    [JsonIgnore]
    public string DisplayName => Name;

    public override string ToString() => DisplayName;
}

public sealed class SubscriptionRecord
{
    public required string Id { get; init; }
    public required string Name { get; set; }
    public required string Source { get; init; }
    public required string Payload { get; init; }
    public required string ProviderYaml { get; init; }
    public List<ProxyNode> Nodes { get; init; } = new();
    public DateTimeOffset UpdatedAt { get; init; } = DateTimeOffset.UtcNow;

    [JsonIgnore]
    public string Summary => $"{Nodes.Count} 个节点 · {UpdatedAt.ToLocalTime():yyyy-MM-dd HH:mm}";

    public override string ToString() => Name;
}

public sealed class RouteTarget
{
    public RouteKind Kind { get; init; }
    public string? SubscriptionId { get; init; }
    public string? NodeId { get; init; }

    public static RouteTarget Direct() => new() { Kind = RouteKind.Direct };

    public static RouteTarget Block() => new() { Kind = RouteKind.Block };

    public static RouteTarget Automatic(string subscriptionId) => new()
    {
        Kind = RouteKind.Automatic,
        SubscriptionId = subscriptionId,
    };

    public static RouteTarget Fixed(string subscriptionId, string nodeId) => new()
    {
        Kind = RouteKind.FixedNode,
        SubscriptionId = subscriptionId,
        NodeId = nodeId,
    };
}

public sealed class WindowsAppRoute
{
    public required string ProcessName { get; init; }
    public required string DisplayName { get; init; }
    public required RouteTarget Target { get; init; }
}

public sealed class WindowsNetworkOptions
{
    public bool EnableTun { get; init; } = true;
    public bool Ipv6Enabled { get; init; } = true;
    public bool BlockUdpStun { get; init; }
    public DnsProfile DnsProfile { get; init; } = DnsProfile.Privacy;
    public string? CustomDnsEndpoint { get; init; }
}

public sealed class RuntimeBundle
{
    public required string Directory { get; init; }
    public required string ConfigPath { get; init; }
    public required int MixedPort { get; init; }
}
