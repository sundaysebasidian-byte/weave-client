import CryptoKit
import Foundation
import Security

public actor SecureSubscriptionStore {
    private let fileURL: URL
    private let key: SymmetricKey
    private var subscriptions: [WeaveSubscription] = []
    private var loaded = false

    public init(
        directory: URL? = nil,
        keyData: Data? = nil,
        keychainService: String = "io.weave.client.ios",
        keychainAccount: String = "subscription-master-key-v1"
    ) throws {
        let base = directory ?? FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        )[0].appendingPathComponent("Weave", isDirectory: true)
        try FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        fileURL = base.appendingPathComponent("subscriptions.enc")
        let material = try keyData ?? KeychainKey.loadOrCreate(
            service: keychainService,
            account: keychainAccount
        )
        guard material.count == 32 else { throw WeaveError.message("订阅主密钥长度无效") }
        key = SymmetricKey(data: material)
    }

    public func all() throws -> [WeaveSubscription] {
        try loadIfNeeded()
        return subscriptions
    }

    @discardableResult
    public func importPayload(
        _ imported: ImportedSubscriptionPayload,
        preferredName: String? = nil,
        replacing id: UUID? = nil
    ) throws -> WeaveSubscription {
        try loadIfNeeded()
        let nodes = ClashNodeParser.parse(imported.payload)
        guard !nodes.isEmpty else {
            throw WeaveError.message("订阅中没有可供内核使用的 Clash 节点")
        }
        let requested = preferredName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = (requested?.isEmpty == false ? requested : imported.suggestedName)
            .map { String($0.prefix(80)) } ?? "未命名订阅"
        let previous = subscriptions
        let subscription = WeaveSubscription(
            id: id ?? UUID(),
            name: name,
            source: imported.source,
            payload: imported.payload,
            nodeCount: nodes.count,
            updatedAt: Date()
        )
        if let id, let index = subscriptions.firstIndex(where: { $0.id == id }) {
            subscriptions[index] = subscription
        } else {
            subscriptions.append(subscription)
        }
        do {
            try persist()
            return subscription
        } catch {
            subscriptions = previous
            throw error
        }
    }

    public func rename(id: UUID, name: String) throws {
        try loadIfNeeded()
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let index = subscriptions.firstIndex(where: { $0.id == id }) else {
            throw WeaveError.message("订阅名称不能为空")
        }
        let previous = subscriptions[index]
        subscriptions[index].name = String(trimmed.prefix(80))
        do {
            try persist()
        } catch {
            subscriptions[index] = previous
            throw error
        }
    }

    public func remove(id: UUID) throws {
        try loadIfNeeded()
        let previous = subscriptions
        subscriptions.removeAll { $0.id == id }
        do {
            try persist()
        } catch {
            subscriptions = previous
            throw error
        }
    }

    public func replaceAll(with items: [TransferSubscription]) throws {
        try loadIfNeeded()
        guard !items.isEmpty, items.count <= TransferLimits.maxSubscriptions else {
            throw WeaveError.message("传输订阅数量无效")
        }
        let now = Date()
        let imported = try items.map { item -> WeaveSubscription in
            let payload = try SubscriptionImporter.normalizeForMihomo(item.payload)
            let nodes = ClashNodeParser.parse(payload)
            guard !nodes.isEmpty else {
                throw WeaveError.message("“\(item.name)”没有有效节点")
            }
            return WeaveSubscription(
                name: item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    ? "未命名订阅" : String(item.name.prefix(80)),
                source: item.source,
                payload: payload,
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

    public func exportItems() throws -> [TransferSubscription] {
        try loadIfNeeded()
        return subscriptions.map {
            TransferSubscription(name: $0.name, source: $0.source, payload: $0.payload)
        }
    }

    private func loadIfNeeded() throws {
        guard !loaded else { return }
        defer { loaded = true }
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            subscriptions = []
            return
        }
        let encrypted = try Data(contentsOf: fileURL)
        guard !encrypted.isEmpty else { subscriptions = []; return }
        do {
            let box = try AES.GCM.SealedBox(combined: encrypted)
            let plaintext = try AES.GCM.open(box, using: key)
            subscriptions = try JSONDecoder().decode([WeaveSubscription].self, from: plaintext)
        } catch {
            throw WeaveError.message("无法解密本机订阅库；原文件未被覆盖")
        }
    }

    private func persist() throws {
        let data = try JSONEncoder().encode(subscriptions)
        let sealed = try AES.GCM.seal(data, using: key)
        guard let combined = sealed.combined else {
            throw WeaveError.message("无法加密订阅库")
        }
        #if os(iOS)
        try combined.write(to: fileURL, options: [.atomic, .completeFileProtection])
        #else
        try combined.write(to: fileURL, options: .atomic)
        #endif
    }
}

private enum KeychainKey {
    static func loadOrCreate(service: String, account: String) throws -> Data {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data, data.count == 32 { return data }
        guard status == errSecItemNotFound else {
            throw WeaveError.message("无法读取 Keychain 订阅密钥")
        }
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            throw WeaveError.message("无法生成 Keychain 订阅密钥")
        }
        let data = Data(bytes)
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        guard SecItemAdd(add as CFDictionary, nil) == errSecSuccess else {
            throw WeaveError.message("无法保存 Keychain 订阅密钥")
        }
        return data
    }
}
