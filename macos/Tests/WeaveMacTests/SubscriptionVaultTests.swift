import CryptoKit
import Foundation
import Security
import Testing
@testable import WeaveMac

private struct FixedKeyStore: SubscriptionKeyStore {
    let key: SymmetricKey
    func loadKey(createIfMissing: Bool) throws -> SymmetricKey { key }
}

@Suite("Subscription storage recovery")
struct SubscriptionVaultTests {
    @Test(arguments: [errSecInteractionNotAllowed, errSecAuthFailed, errSecNotAvailable])
    func failedKeychainReadNeverCreatesKey(status: OSStatus) throws {
        var writes = 0
        let store = KeychainSubscriptionKeyStore(read: { (status, nil) }, insert: { _ in writes += 1; return errSecSuccess })
        #expect(throws: (any Error).self) { try store.loadKey(createIfMissing: true) }
        #expect(writes == 0)
    }

    @Test func missingKeyCannotReplaceExistingVaultKey() throws {
        var writes = 0
        let store = KeychainSubscriptionKeyStore(read: { (errSecItemNotFound, nil) }, insert: { _ in writes += 1; return errSecSuccess })
        #expect(throws: (any Error).self) { try store.loadKey(createIfMissing: false) }
        #expect(writes == 0)
    }

    @Test func failedKeychainInsertDoesNotReturnEphemeralKey() throws {
        let store = KeychainSubscriptionKeyStore(read: { (errSecItemNotFound, nil) }, insert: { _ in errSecAuthFailed })
        #expect(throws: (any Error).self) { try store.loadKey(createIfMissing: true) }
    }

    @Test func newKeyIsVerifiedByReadingItBack() throws {
        var persisted: Data?
        let store = KeychainSubscriptionKeyStore(
            read: { (persisted == nil ? errSecItemNotFound : errSecSuccess, persisted) },
            insert: { persisted = $0; return errSecSuccess }
        )
        let key = try store.loadKey(createIfMissing: true)
        #expect(key.withUnsafeBytes { Data($0) } == persisted)
    }

    @Test func concurrentCreatorUsesDurableKey() throws {
        let existing = Data(repeating: 7, count: 32)
        var reads = 0
        let store = KeychainSubscriptionKeyStore(
            read: { reads += 1; return reads == 1 ? (errSecItemNotFound, nil) : (errSecSuccess, existing) },
            insert: { _ in errSecDuplicateItem }
        )
        #expect(try store.loadKey(createIfMissing: true).withUnsafeBytes { Data($0) } == existing)
    }

    @Test @MainActor func wrongKeyCannotOverwriteEncryptedSubscriptions() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let key = SymmetricKey(size: .bits256)
        let vault = SubscriptionVault(directory: directory, keyStore: FixedKeyStore(key: key))
        try vault.importTransfer([item])
        let file = directory.appendingPathComponent("subscriptions.enc")
        let before = try Data(contentsOf: file)
        let locked = SubscriptionVault(directory: directory, keyStore: FixedKeyStore(key: SymmetricKey(size: .bits256)))
        #expect(locked.isReadOnly)
        #expect(throws: (any Error).self) { try locked.importTransfer([item]) }
        #expect(try Data(contentsOf: file) == before)
        let restored = SubscriptionVault(directory: directory, keyStore: FixedKeyStore(key: key))
        #expect(!restored.isReadOnly)
        #expect(restored.subscriptions.map(\.name) == [item.name])
    }

    @Test @MainActor func corruptAndEmptyVaultsRemainReadOnly() throws {
        for bytes in [Data(), Data("invalid".utf8)] {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            defer { try? FileManager.default.removeItem(at: directory) }
            let file = directory.appendingPathComponent("subscriptions.enc")
            try bytes.write(to: file)
            let vault = SubscriptionVault(directory: directory, keyStore: FixedKeyStore(key: SymmetricKey(size: .bits256)))
            #expect(vault.isReadOnly)
            #expect(throws: (any Error).self) { try vault.importTransfer([item]) }
            #expect(try Data(contentsOf: file) == bytes)
        }
    }

    @Test @MainActor func failedWriteDoesNotPublishNewSubscriptions() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let vault = SubscriptionVault(directory: directory, keyStore: FixedKeyStore(key: SymmetricKey(size: .bits256)))
        // A directory at the file path deterministically rejects atomic replacement, even as root.
        try FileManager.default.createDirectory(at: directory.appendingPathComponent("subscriptions.enc"), withIntermediateDirectories: true)
        #expect(throws: (any Error).self) { try vault.importTransfer([item]) }
        #expect(vault.subscriptions.isEmpty)
    }

    private var item: TransferSubscription {
        TransferSubscription(name: "Test", source: "file://fixture", payload: "proxies:\n  - name: Test\n    type: socks5\n    server: example.invalid\n    port: 1080\n")
    }
}
