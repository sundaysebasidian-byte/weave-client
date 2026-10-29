import CryptoKit
import Foundation
import Security

protocol SubscriptionKeyStore {
    func loadKey(createIfMissing: Bool) throws -> SymmetricKey
}

struct KeychainSubscriptionKeyStore: SubscriptionKeyStore {
    var read: () -> (OSStatus, Data?) = {
        var query = identity
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        return (status, item as? Data)
    }
    var insert: (Data) -> OSStatus = { data in
        var query = identity
        query[kSecValueData as String] = data
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(query as CFDictionary, nil)
    }

    func loadKey(createIfMissing: Bool) throws -> SymmetricKey {
        let (status, data) = read()
        if status == errSecSuccess { return try validatedKey(data) }
        guard status == errSecItemNotFound else { throw failure(status) }
        guard createIfMissing else {
            throw WeaveMacError.message("订阅库的 Keychain 密钥缺失；已禁止写入，请恢复原钥匙串或加密备份")
        }
        let key = SymmetricKey(size: .bits256)
        let bytes = key.withUnsafeBytes { Data($0) }
        let inserted = insert(bytes)
        // A second app instance may have created the same item. Always use the persisted key.
        guard inserted == errSecSuccess || inserted == errSecDuplicateItem else { throw failure(inserted) }
        let (readStatus, saved) = read()
        guard readStatus == errSecSuccess else { throw failure(readStatus) }
        if inserted == errSecSuccess, saved != bytes {
            throw WeaveMacError.message("Keychain 密钥回读不一致；已禁止写入订阅库")
        }
        return try validatedKey(saved)
    }

    private func validatedKey(_ data: Data?) throws -> SymmetricKey {
        guard let data, data.count == 32 else {
            throw WeaveMacError.message("Keychain 密钥格式无效；已禁止写入订阅库")
        }
        return SymmetricKey(data: data)
    }

    private func failure(_ status: OSStatus) -> WeaveMacError {
        .message("Keychain 不可用（\(status)）；请解锁钥匙串并重新打开 Weave，原订阅库保持不变")
    }

    private static var identity: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "io.weave.client.macos",
            kSecAttrAccount as String: "subscription-master-key-v1",
        ]
    }
}
