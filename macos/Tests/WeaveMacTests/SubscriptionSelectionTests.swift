import WeaveCoreRuntime
import CryptoKit
import Foundation
import Testing
@testable import WeaveMac

@Suite("Subscription selection and staged refresh") @MainActor
struct SubscriptionSelectionTests {
    @Test func selectedSubscriptionAndFixedNodeSurviveRestart() async throws {
        let fixture = try await Fixture()
        defer { fixture.close() }
        let model = fixture.model()
        try await settle(model)
        let second = fixture.vault.subscriptions[1]
        model.selectSubscription(second.id)
        model.selectNode("Beta")
        let restored = fixture.model()
        try await settle(restored)
        #expect(restored.selectedSubscriptionID == second.id)
        #expect(restored.selectedNodeName == "Beta")
        #expect(restored.selectedNodes == ["Beta"])
    }

    @Test func cacheCompletionUsesLatestSelection() async throws {
        let fixture = try await Fixture()
        defer { fixture.close() }
        let model = fixture.model()
        // Change selection while the initial cache task is suspended.
        model.selectSubscription(fixture.vault.subscriptions[1].id)
        try await settle(model)
        #expect(model.selectedNodes == ["Beta"])
    }

    @Test func removedFixedNodeIsNotSilentlyChangedToAutomatic() async throws {
        let fixture = try await Fixture()
        defer { fixture.close() }
        let model = fixture.model()
        try await settle(model)
        model.selectNode("Alpha")
        let original = fixture.vault.subscriptions[0]
        try await fixture.vault.update(id: original.id, name: original.name, source: original.source, payload: payload("Replacement"))
        try await settle(model)
        #expect(model.selectedNodeName == "Alpha")
        #expect(model.selectionIssue != nil)
        model.selectNode(nil)
        #expect(model.selectionIssue == nil)
    }

    @Test func refreshPreviewDoesNotWriteUntilApplied() async throws {
        let fixture = try await Fixture()
        defer { fixture.close() }
        let model = fixture.model()
        let original = fixture.vault.subscriptions[0]
        try await model.stageSubscriptionUpdate(original, imported: imported("Replacement"))
        #expect(fixture.vault.subscriptions[0] == original)
        #expect(model.pendingSubscriptionUpdate?.added == ["Replacement"])
        #expect(model.pendingSubscriptionUpdate?.removed == ["Alpha"])
        await model.applyPendingSubscriptionUpdate()
        #expect(ClashNodeNames.parse(fixture.vault.subscriptions[0].payload) == ["Replacement"])
        #expect(model.pendingSubscriptionUpdate == nil)
        try await settle(model)
    }

    @Test func concurrentEditInvalidatesRefreshPreview() async throws {
        let fixture = try await Fixture()
        defer { fixture.close() }
        let model = fixture.model()
        let original = fixture.vault.subscriptions[0]
        try await model.stageSubscriptionUpdate(original, imported: imported("Downloaded"))
        try await fixture.vault.update(id: original.id, name: "Edited", source: original.source, payload: payload("Manual"))
        await model.applyPendingSubscriptionUpdate()
        #expect(fixture.vault.subscriptions[0].name == "Edited")
        #expect(ClashNodeNames.parse(fixture.vault.subscriptions[0].payload) == ["Manual"])
        #expect(model.directImportMessage.contains("已被修改"))
        try await settle(model)
    }

    private func settle(_ model: AppModel) async throws {
        for _ in 0..<1_000 {
            if !model.nodeCacheLoading && model.core.state != .stopping { return }
            try await Task.sleep(for: .milliseconds(1))
        }
        Issue.record("Subscription cache did not finish")
    }

    private func imported(_ name: String) -> ImportedSubscriptionPayload {
        .init(suggestedName: "Test", source: "https://example.invalid/sub", payload: payload(name))
    }

    private func payload(_ name: String) -> String { Self.payload(name) }
    private static func payload(_ name: String) -> String {
        "proxies:\n  - name: '\(name)'\n    type: socks5\n    server: example.invalid\n    port: 1080\n"
    }

    @MainActor private struct Fixture {
        let directory: URL
        let defaultsName = "WeaveTests.\(UUID())"
        let defaults: UserDefaults
        let vault: SubscriptionVault
        init() async throws {
            directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            defaults = UserDefaults(suiteName: defaultsName)!
            vault = SubscriptionVault(directory: directory, keyStore: Key())
            try await vault.importTransfer(["Alpha", "Beta"].map {
                TransferSubscription(name: $0, source: "file://fixture", payload: SubscriptionSelectionTests.payload($0))
            })
        }
        func model() -> AppModel {
            let proxy = SystemProxyManager(transactionURL: directory.appendingPathComponent("proxy.json"), runCommand: { _, _ in
                throw WeaveMacError.message("No system commands expected")
            })
            return AppModel(vault: vault, core: MihomoController(systemProxy: proxy), defaults: defaults)
        }
        func close() {
            defaults.removePersistentDomain(forName: defaultsName)
            try? FileManager.default.removeItem(at: directory)
        }
        struct Key: SubscriptionKeyStore {
            func loadKey(createIfMissing: Bool) throws -> SymmetricKey { SymmetricKey(size: .bits256) }
        }
    }
}
