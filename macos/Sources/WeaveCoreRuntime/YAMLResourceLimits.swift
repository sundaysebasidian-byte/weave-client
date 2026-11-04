import CYaml
import Foundation

/// Check streaming events before Yams constructs recursive Swift values or hashes mapping keys.
/// Alias costs include their full expansion, even when libyaml represents them in constant space.
enum YAMLResourceLimits {
    private static let maxDepth = 40
    private static let maxNodes = 1_000_000
    private static let maxScalarBytes = 20 * 1024 * 1024

    private struct Cost {
        var nodes = 1
        var bytes = 0
        var depth = 1

        mutating func add(_ child: Cost) throws {
            nodes += child.nodes
            bytes += child.bytes
            depth = max(depth, child.depth + 1)
            guard nodes <= maxNodes, bytes <= maxScalarBytes, depth <= maxDepth else {
                throw WeaveMacError.message("YAML 嵌套或锚点展开超过安全限制")
            }
        }
    }

    private struct Frame {
        let mapping: Bool
        let anchor: String?
        var expectsKey = true
        var keys = Set<String>()
        var cost = Cost()
    }

    static func validate(_ payload: String) throws {
        let bytes = Array(payload.utf8)
        guard !bytes.isEmpty, bytes.count <= ClashProviderDocument.maxPayloadBytes else {
            throw WeaveMacError.message("订阅内容为空或超过 5 MiB 限制")
        }
        var parser = yaml_parser_t()
        guard yaml_parser_initialize(&parser) != 0 else {
            throw WeaveMacError.message("无法初始化 YAML 解析器")
        }
        defer { yaml_parser_delete(&parser) }
        try bytes.withUnsafeBufferPointer { buffer in
            yaml_parser_set_input_string(&parser, buffer.baseAddress, buffer.count)
            var frames: [Frame] = []
            var anchors: [String: Cost] = [:]
            var documents = 0
            var events = 0

            func finish(_ cost: Cost, anchor: String?) throws {
                if let anchor { anchors[anchor] = cost }
                if !frames.isEmpty { try frames[frames.count - 1].cost.add(cost) }
            }

            while true {
                var event = yaml_event_t()
                guard yaml_parser_parse(&parser, &event) != 0 else {
                    // Parser diagnostics can quote credentials. Report only the position.
                    throw WeaveMacError.message("YAML 格式错误（第 \(parser.problem_mark.line + 1) 行）")
                }
                defer { yaml_event_delete(&event) }
                events += 1
                guard events <= maxNodes * 2 else { throw WeaveMacError.message("YAML 节点数量超过安全限制") }
                switch event.type {
                case YAML_STREAM_END_EVENT: return
                case YAML_DOCUMENT_START_EVENT:
                    documents += 1
                    guard documents == 1 else { throw WeaveMacError.message("订阅只能包含一个 YAML 文档") }
                case YAML_SCALAR_EVENT, YAML_ALIAS_EVENT, YAML_SEQUENCE_START_EVENT, YAML_MAPPING_START_EVENT:
                    if !frames.isEmpty, frames[frames.count - 1].mapping {
                        let index = frames.count - 1
                        if frames[index].expectsKey {
                            guard event.type == YAML_SCALAR_EVENT else {
                                throw WeaveMacError.message("YAML 配置键必须是普通文本")
                            }
                            let key = scalar(event)
                            guard frames[index].keys.insert(key).inserted else {
                                throw WeaveMacError.message("YAML 包含重复配置键（第 \(event.start_mark.line + 1) 行）")
                            }
                        }
                        frames[index].expectsKey.toggle()
                    }
                    switch event.type {
                    case YAML_SCALAR_EVENT:
                        try validateTag(event.data.scalar.tag)
                        try finish(Cost(bytes: event.data.scalar.length), anchor: string(event.data.scalar.anchor))
                    case YAML_ALIAS_EVENT:
                        guard let name = string(event.data.alias.anchor), let cost = anchors[name] else {
                            throw WeaveMacError.message("YAML 包含未定义或递归锚点")
                        }
                        guard frames.count + cost.depth <= maxDepth else {
                            throw WeaveMacError.message("YAML 锚点展开深度超过安全限制")
                        }
                        try finish(cost, anchor: nil)
                    default:
                        guard frames.count < maxDepth else { throw WeaveMacError.message("YAML 嵌套深度超过安全限制") }
                        let mapping = event.type == YAML_MAPPING_START_EVENT
                        try validateTag(mapping ? event.data.mapping_start.tag : event.data.sequence_start.tag)
                        let anchor = string(mapping ? event.data.mapping_start.anchor : event.data.sequence_start.anchor)
                        frames.append(Frame(mapping: mapping, anchor: anchor))
                    }
                case YAML_SEQUENCE_END_EVENT, YAML_MAPPING_END_EVENT:
                    guard let frame = frames.popLast() else { throw WeaveMacError.message("YAML 结构不完整") }
                    try finish(frame.cost, anchor: frame.anchor)
                default: break
                }
            }
        }
    }

    private static func string(_ value: UnsafePointer<UInt8>?) -> String? {
        value.map { String(cString: $0) }
    }

    private static func scalar(_ event: yaml_event_t) -> String {
        String(decoding: UnsafeBufferPointer(start: event.data.scalar.value, count: event.data.scalar.length), as: UTF8.self)
    }

    private static func validateTag(_ value: UnsafePointer<UInt8>?) throws {
        guard let tag = string(value) else { return }
        let allowed = ["!", "tag:yaml.org,2002:str", "tag:yaml.org,2002:int", "tag:yaml.org,2002:float",
                       "tag:yaml.org,2002:bool", "tag:yaml.org,2002:null", "tag:yaml.org,2002:seq",
                       "tag:yaml.org,2002:map", "tag:yaml.org,2002:merge"]
        guard allowed.contains(tag) else { throw WeaveMacError.message("订阅包含不支持的 YAML 类型标签") }
    }
}
