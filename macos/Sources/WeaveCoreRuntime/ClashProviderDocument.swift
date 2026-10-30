import Foundation
import Yams

package struct ClashProviderDocument {
    package static let maxPayloadBytes = 5 * 1024 * 1024
    package let names: [String]
    let proxies: Node

    package init(_ payload: String, providerOnly: Bool = false) throws {
        try YAMLResourceLimits.validate(payload)
        let root: Node
        do {
            guard let parsed = try Yams.compose(yaml: payload) else { throw WeaveMacError.message("订阅内容为空") }
            root = try Self.expanded(parsed)
        } catch let error as WeaveMacError { throw error }
        catch { throw WeaveMacError.message("无法解析 YAML 订阅；请检查格式和重复配置键") }
        if providerOnly, root.mapping?.count != 1 || root["proxies"] == nil {
            throw WeaveMacError.message("运行时 provider 只能包含 proxies 节点列表")
        }
        guard let sequence = root["proxies"]?.sequence, !sequence.isEmpty else {
            throw WeaveMacError.message("Clash 订阅缺少有效的 proxies 节点列表")
        }
        var names: [String] = []
        var seen = Set<String>()
        for proxy in sequence {
            guard proxy.mapping != nil, let name = proxy["name"]?.string,
                  !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, name.utf8.count <= 320,
                  !name.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains),
                  let type = proxy["type"]?.string, !type.isEmpty else {
                throw WeaveMacError.message("节点缺少有效的 name 或 type 字段")
            }
            guard seen.insert(name).inserted else { throw WeaveMacError.message("订阅包含重名节点，请先为节点设置唯一名称") }
            names.append(name)
        }
        self.names = names
        proxies = Node(Array(sequence), Tag(.seq), .block)
    }

    package func sanitized() throws -> String {
        do {
            let result = try Yams.serialize(node: Node([(Node("proxies", Tag(.str)), proxies)]),
                                            indent: 2, width: -1, allowUnicode: true)
            guard result.utf8.count <= ClashProviderDocument.maxPayloadBytes else {
                throw WeaveMacError.message("锚点展开后的订阅超过 5 MiB 限制")
            }
            return result
        } catch let error as WeaveMacError { throw error }
        catch { throw WeaveMacError.message("无法生成 Clash 节点配置") }
    }

    // Inline aliases and merges so a referenced root such as `listeners: &defaults` can never
    // carry a control-plane key into the provider. Explicit fields override merged defaults;
    // earlier maps in a merge sequence take precedence, as specified by YAML merge semantics.
    private static func expanded(_ node: Node) throws -> Node {
        switch node {
        case .scalar(let scalar):
            return Node(scalar.string, node.tag, scalar.style)
        case .sequence(let sequence):
            return Node(try sequence.map(expanded), Tag(.seq), .block)
        case .mapping(let mapping):
            var pairs: [(Node, Node)] = []
            var positions: [String: Int] = [:]
            func put(_ key: Node, _ value: Node, override: Bool) throws {
                guard let name = key.string else { throw WeaveMacError.message("YAML 配置键必须是文本") }
                if let position = positions[name] {
                    if override { pairs[position] = (Node(name, Tag(.str)), value) }
                } else {
                    positions[name] = pairs.count
                    pairs.append((Node(name, Tag(.str)), value))
                }
            }
            for pair in mapping where pair.key.tag == Tag(.merge) {
                let merged = try expanded(pair.value)
                let sources = merged.mapping != nil ? [merged] : merged.array()
                guard merged.mapping != nil || merged.sequence != nil, sources.allSatisfy({ $0.mapping != nil }) else {
                    throw WeaveMacError.message("YAML 合并锚点必须引用配置映射")
                }
                for source in sources {
                    for entry in source.mapping! { try put(entry.key, entry.value, override: false) }
                }
            }
            for pair in mapping where pair.key.tag != Tag(.merge) {
                try put(pair.key, expanded(pair.value), override: true)
            }
            return Node(pairs, Tag(.map), .block)
        case .alias:
            throw WeaveMacError.message("YAML 锚点未完成解析")
        }
    }
}
