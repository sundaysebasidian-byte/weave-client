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

public enum RoutingMode { Rule, Global, Direct }

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

public sealed class SubscriptionRecord : System.ComponentModel.INotifyPropertyChanged
{
    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
    public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(nameof(Summary)));
    public required string Id { get; init; }
    public required string Name { get; set; }
    public required string Source { get; init; }
    public required string Payload { get; init; }
    public required string ProviderYaml { get; init; }
    public List<ProxyNode> Nodes { get; init; } = new();
    public DateTimeOffset UpdatedAt { get; init; } = DateTimeOffset.UtcNow;

    [JsonIgnore]
    public string Summary => L.F($"{Nodes.Count} 个节点 · {UpdatedAt.ToLocalTime():yyyy-MM-dd HH:mm}");

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

public sealed class WindowsAppRoute : System.ComponentModel.INotifyPropertyChanged
{
    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
    public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(nameof(TargetLabel)));
    [JsonIgnore]
    public string TargetLabel => Target.Kind switch
    {
        RouteKind.Automatic => L.T("自动测速"), RouteKind.FixedNode => L.T("固定节点"),
        RouteKind.Direct => L.T("直连"), _ => L.T("阻止"),
    };
    public required string ProcessName { get; init; }
    public required string DisplayName { get; init; }
    public required RouteTarget Target { get; init; }
}

public sealed class WindowsNetworkOptions
{
    public bool ChinaDirect { get; init; }
    public string? GeoDataDirectory { get; init; }
    public string? ChainEntrySubscriptionId { get; init; }
    public string? ChainEntryNodeId { get; init; }
    public IReadOnlyList<DomainRoute> DomainRules { get; init; } = Array.Empty<DomainRoute>();
    public RoutingMode RoutingMode { get; init; } = RoutingMode.Rule;
    public bool EnableTun { get; init; } = true;
    public bool Ipv6Enabled { get; init; } = true;
    public bool BlockUdpStun { get; init; }
    public DnsProfile DnsProfile { get; init; } = DnsProfile.Privacy;
    public string? CustomDnsEndpoint { get; init; }
}

public sealed record DomainRoute(string Kind, string Value, string Target)
{
    public static IReadOnlyList<DomainRoute> Parse(string text)
    {
        var rules = new List<DomainRoute>();
        foreach (var line in text.Split('\n').Select(line => line.Trim()).Where(line => line.Length > 0))
        {
            var fields = line.Split(',').Select(field => field.Trim()).ToArray();
            if (fields.Length != 3 || fields[0] is not ("DOMAIN" or "DOMAIN-SUFFIX" or "IP-CIDR" or "IP-CIDR6") ||
                fields[2] is not ("DIRECT" or "PROXY" or "REJECT") || fields[1].Any(char.IsControl))
                throw new InvalidDataException(L.T("规则格式：DOMAIN-SUFFIX,example.com,DIRECT（也支持 PROXY / REJECT）"));
            if (fields[0].StartsWith("DOMAIN", StringComparison.Ordinal))
            {
                if (Uri.CheckHostName(fields[1]) != UriHostNameType.Dns) throw new InvalidDataException(L.T("规则域名无效"));
            }
            else
            {
                var cidr = fields[1].Split('/');
                if (cidr.Length != 2 || !System.Net.IPAddress.TryParse(cidr[0], out var ip) ||
                    !int.TryParse(cidr[1], out var bits) || bits < 0 || bits > (fields[0] == "IP-CIDR" ? 32 : 128) ||
                    (ip.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork) != (fields[0] == "IP-CIDR"))
                    throw new InvalidDataException(L.T("规则 IP 网段无效"));
            }
            rules.Add(new(fields[0], fields[1], fields[2]));
            if (rules.Count > 2000) throw new InvalidDataException(L.T("规则数量超过 2000 条"));
        }
        return rules;
    }
    public string Compile() => $"{Kind},{Value},{(Target == "PROXY" ? "DEFAULT" : Target)}{(Kind.StartsWith("IP-", StringComparison.Ordinal) ? ",no-resolve" : "")}";
}

public sealed class RuntimeBundle
{
    public required string Directory { get; init; }
    public required string ConfigPath { get; init; }
    public required int MixedPort { get; init; }
    public int ControllerPort { get; init; }
    public string ControllerSecret { get; init; } = "";
    public bool RequiresTun { get; init; }
    public string TunDevice { get; init; } = "WeaveTun";
    public IReadOnlyDictionary<string, int> ProviderNodeCounts { get; init; } = new Dictionary<string, int>();
    public IReadOnlyDictionary<string, string> RequiredSelections { get; init; } = new Dictionary<string, string>();
}
