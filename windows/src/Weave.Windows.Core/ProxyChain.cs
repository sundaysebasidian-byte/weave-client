using YamlDotNet.RepresentationModel;

namespace Weave.Windows.Core;

public static class ProxyChain
{
    public static bool Enabled(WindowsNetworkOptions options) => !string.IsNullOrEmpty(options.ChainEntrySubscriptionId);
    public static string Build(IReadOnlyDictionary<string, SubscriptionRecord> subscriptions,
        SubscriptionRecord exitSubscription, string? exitNodeId, WindowsNetworkOptions options)
    {
        if (exitNodeId is null || options.ChainEntryNodeId is null ||
            !subscriptions.TryGetValue(options.ChainEntrySubscriptionId!, out var entry))
            throw new InvalidDataException("链式代理需要明确选择入口和出口两个节点，不能使用自动节点");
        if (entry.Id == exitSubscription.Id && options.ChainEntryNodeId == exitNodeId)
            throw new InvalidDataException("入口和出口不能是同一节点");
        var first = Node(entry, options.ChainEntryNodeId, "WEAVE-CHAIN-ENTRY");
        var last = Node(exitSubscription, exitNodeId, "WEAVE-CHAIN-EXIT");
        last.Add(new YamlScalarNode("dialer-proxy"), new YamlScalarNode("WEAVE-CHAIN-ENTRY"));
        using var writer = new StringWriter();
        new YamlStream(new YamlDocument(new YamlMappingNode(new YamlScalarNode("proxies"),
            new YamlSequenceNode(first, last)))).Save(writer, false);
        return string.Join("\n", writer.ToString().Split('\n').Where(line => line.TrimEnd('\r') is not ("..." or "---")));
    }
    private static YamlMappingNode Node(SubscriptionRecord record, string id, string name)
    {
        var selected = record.Nodes.FirstOrDefault(node => node.Id == id) ?? throw new InvalidDataException("链式节点已不存在");
        var root = ClashPayloadParser.Read(record.ProviderYaml);
        var sequence = ClashPayloadParser.Find(root, "proxies") as YamlSequenceNode ?? throw new InvalidDataException("链式订阅无节点");
        var node = sequence.Children.OfType<YamlMappingNode>().FirstOrDefault(item => ClashPayloadParser.Scalar(item, "name") == selected.RawName)
            ?? throw new InvalidDataException("链式节点未完整载入");
        if (ClashPayloadParser.Find(node, "dialer-proxy") is not null)
            throw new InvalidDataException("所选节点已有链式引用，请使用独立原始节点，避免循环");
        node.Children[new YamlScalarNode("name")] = new YamlScalarNode(name);
        return node;
    }
}
