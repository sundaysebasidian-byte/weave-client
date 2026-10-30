import WeaveCoreRuntime
import CryptoKit
import Combine
import Foundation

@MainActor
final class SubscriptionVault: ObservableObject {
    @Published private(set) var subscriptions: [MacSubscription] = []
    @Published var lastError: String?
    @Published private(set) var isReadOnly = true

    private let fileURL: URL
    private var key: SymmetricKey?

    init(directory: URL? = nil, keyStore: any SubscriptionKeyStore = KeychainSubscriptionKeyStore()) {
        let support = directory ?? FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first!.appendingPathComponent("Weave", isDirectory: true)
        fileURL = support.appendingPathComponent("subscriptions.enc")
        do {
            try FileManager.default.createDirectory(
                at: support, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700]
            )
            let encrypted: Data?
            do { encrypted = try Data(contentsOf: fileURL) }
            catch CocoaError.fileReadNoSuchFile { encrypted = nil }
            let loadedKey = try keyStore.loadKey(createIfMissing: encrypted == nil)
            if let encrypted {
                let box = try AES.GCM.SealedBox(combined: encrypted)
                let plaintext = try AES.GCM.open(box, using: loadedKey)
                subscriptions = try JSONDecoder().decode([MacSubscription].self, from: plaintext)
            }
            key = loadedKey
            isReadOnly = false
        } catch {
            lastError = "无法打开本机订阅库，已禁止写入；原文件保持不变。\(error.localizedDescription)"
        }
    }

    func importTransfer(_ items: [TransferSubscription]) async throws {
        try requireWritable()
        let now = Date()
        let imported = try await Task.detached(priority: .userInitiated) {
            try items.map { item in
                let document = try ClashProviderDocument(item.payload)
                return MacSubscription(
                    id: UUID(),
                    name: item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? "未命名订阅" : String(item.name.prefix(80)),
                    source: item.source,
                    payload: try document.sanitized(),
                    nodeCount: document.names.count,
                    updatedAt: now
                )
            }
        }.value
        try Task.checkCancellation()
        try commit(subscriptions + imported)
    }

    func update(
        id: UUID,
        name: String,
        source: String,
        payload: String,
    ) async throws {
        try requireWritable()
        guard let original = subscriptions.first(where: { $0.id == id }) else {
            throw WeaveMacError.message("订阅不存在")
        }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            throw WeaveMacError.message("订阅名称不能为空")
        }
        let prepared = try await Task.detached(priority: .userInitiated) {
            let document = try ClashProviderDocument(payload)
            return (try document.sanitized(), document.names.count)
        }.value
        try Task.checkCancellation()
        guard let index = subscriptions.firstIndex(where: { $0.id == id }), subscriptions[index] == original else {
            throw WeaveMacError.message("校验期间订阅已被修改或移除，请重新编辑")
        }
        var updated = subscriptions
        updated[index] = MacSubscription(
            id: id,
            name: String(trimmedName.prefix(80)),
            source: String(source.trimmingCharacters(in: .whitespacesAndNewlines).prefix(8_192)),
            payload: prepared.0,
            nodeCount: prepared.1,
            updatedAt: Date(),
        )
        try commit(updated)
    }

    func remove(_ subscription: MacSubscription) {
        do {
            try commit(subscriptions.filter { $0.id != subscription.id })
        } catch {
            lastError = error.localizedDescription
        }
    }

    func exportItems() -> [TransferSubscription] {
        subscriptions.map {
            TransferSubscription(name: $0.name, source: $0.source, payload: $0.payload)
        }
    }

    private func requireWritable() throws {
        guard !isReadOnly, key != nil else {
            throw WeaveMacError.message(lastError ?? "订阅库处于只读恢复状态")
        }
    }

    private func commit(_ values: [MacSubscription]) throws {
        try requireWritable()
        guard let key else { throw WeaveMacError.message("订阅库密钥不可用") }
        let data = try JSONEncoder().encode(values)
        let sealed = try AES.GCM.seal(data, using: key)
        guard let combined = sealed.combined else {
            throw WeaveMacError.message("无法加密订阅库")
        }
        try combined.write(to: fileURL, options: [.atomic, .completeFileProtection])
        subscriptions = values
        lastError = nil
    }
}
