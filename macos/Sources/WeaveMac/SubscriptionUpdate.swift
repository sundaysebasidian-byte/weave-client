import WeaveCoreRuntime
import Foundation

struct SavedConnectionSelection: Codable {
    var subscriptionID: UUID?
    var nodeNames: [String: String] = [:]

    func node(for id: UUID?) -> String? { id.flatMap { nodeNames[$0.uuidString] } }
}

struct PendingSubscriptionUpdate: Identifiable, Sendable {
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
        let document = try ClashProviderDocument(payload)
        self.payload = try document.sanitized()
        nodes = document.names
        let previous = Set(ClashNodeNames.parse(original.payload))
        let current = Set(nodes)
        added = current.subtracting(previous).sorted()
        removed = previous.subtracting(current).sorted()
    }
}
