import CryptoKit
import Combine
import Foundation
import Security

@MainActor
final class SubscriptionVault: ObservableObject {
    @Published private(set) var subscriptions: [MacSubscription] = []
    @Published var lastError: String?

    private let fileURL: URL
    private let key: SymmetricKey

    init() {
        let support = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first!.appendingPathComponent("Weave", isDirectory: true)
        try? FileManager.default.createDirectory(
            at: support,
            withIntermediateDirectories: true
        )
        fileURL = support.appendingPathComponent("subscriptions.enc")
        key = Self.loadOrCreateKey()
        load()
    }

    func importTransfer(
        _ items: [TransferSubscription],
        nodeNames: [[String]]? = nil
    ) throws {
        let now = Date()
        if let nodeNames, nodeNames.count != items.count {
            throw WeaveMacError.message("订阅节点校验结果不完整")
        }
        let imported = try items.enumerated().map { index, item in
            let nodes = nodeNames?[index] ?? ClashNodeNames.parse(item.payload)
            guard !nodes.isEmpty else {
                throw WeaveMacError.message("“\(item.name)”不是包含有效节点的 Clash 订阅")
            }
            return MacSubscription(
                id: UUID(),
                name: item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? "未命名订阅" : String(item.name.prefix(80)),
                source: item.source,
                payload: item.payload,
                nodeCount: nodes.count,
                updatedAt: now
            )
        }
        let previous = subscriptions
        subscriptions.append(contentsOf: imported)
        do {
            try persist()
        } catch {
            subscriptions = previous
            throw error
        }
    }

    func remove(_ subscription: MacSubscription) {
        let previous = subscriptions
        subscriptions.removeAll { $0.id == subscription.id }
        do {
            try persist()
        } catch {
            subscriptions = previous
            lastError = error.localizedDescription
        }
    }

    func exportItems() -> [TransferSubscription] {
        subscriptions.map {
            TransferSubscription(name: $0.name, source: $0.source, payload: $0.payload)
        }
    }

    private func load() {
        guard let encrypted = try? Data(contentsOf: fileURL), !encrypted.isEmpty else { return }
        do {
            let box = try AES.GCM.SealedBox(combined: encrypted)
            let plaintext = try AES.GCM.open(box, using: key)
            subscriptions = try JSONDecoder().decode([MacSubscription].self, from: plaintext)
        } catch {
            lastError = "无法解密本机订阅库；原文件未被覆盖"
        }
    }

    private func persist() throws {
        let data = try JSONEncoder().encode(subscriptions)
        let sealed = try AES.GCM.seal(data, using: key)
        guard let combined = sealed.combined else {
            throw WeaveMacError.message("无法加密订阅库")
        }
        try combined.write(to: fileURL, options: [.atomic, .completeFileProtection])
    }

    private static func loadOrCreateKey() -> SymmetricKey {
        let service = "io.weave.client.macos"
        let account = "subscription-master-key-v1"
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
        ]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
           let data = item as? Data,
           data.count == 32 {
            return SymmetricKey(data: data)
        }
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            preconditionFailure("无法生成 Keychain 主密钥")
        }
        let data = Data(bytes)
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemAdd(add as CFDictionary, nil)
        return SymmetricKey(data: data)
    }
}
