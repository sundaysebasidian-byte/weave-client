import Foundation

struct SavedConnectionSelection: Codable {
    var subscriptionID: UUID?
    var nodeNames: [String: String] = [:]

    func node(for id: UUID?) -> String? { id.flatMap { nodeNames[$0.uuidString] } }
}

struct PendingSubscriptionUpdate: Identifiable {
    let original: MacSubscription
    let source: String
    let payload: String
    let nodes: [String]
    let added: [String]
    let removed: [String]
    var id: UUID { original.id }

    init(original: MacSubscription, source: String, payload: String) throws {
        self.original = original
        self.source = source
        self.payload = try ClashProviderSanitizer.sanitize(payload)
        nodes = ClashNodeNames.parse(self.payload)
        guard !nodes.isEmpty else { throw WeaveMacError.message("更新中没有有效节点，已保留原订阅") }
        let previous = Set(ClashNodeNames.parse(original.payload))
        let current = Set(nodes)
        added = current.subtracting(previous).sorted()
        removed = previous.subtracting(current).sorted()
    }
}
