import Foundation
import Security
import ServiceManagement
import Testing
import WeaveCoreRuntime
@testable import WeaveCoreBroker
@testable import WeaveCoreService
@testable import WeaveMac

@Suite("GUI to native XPC and real core", .serialized) @MainActor
struct CoreServiceNativeConnectionTests {
    @Test func guiCanReconnectAndObserveHostDisconnect() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveGuiNative-\(UUID())")
        let coreURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
        // Native transport and real process only; this fixture never enables TUN.
        let service = try CoreService(executable: coreURL, runtimeRoot: root, enableTun: false)
        let endpoint = NSXPCListener.anonymous()
        let requirement = try ownRequirement()
        let listener = try BrokerListener(listener: endpoint, requirement: requirement) { service.session(for: $0) }
        listener.resume()
        let proxy = SystemProxyManager(transactionURL: root.appendingPathComponent("proxy.json"), runCommand: { _, _ in
            Issue.record("Native GUI broker test must not change system proxy settings")
            throw BrokerFailure.unavailable
        })
        let manager = CoreServiceManager(registration: GrantedRegistration(), validateIdentity: {})
        let controller = MihomoController(systemProxy: proxy, serviceManager: manager, brokerFactory: { callback in
            try BrokerClient(connection: NSXPCConnection(listenerEndpoint: endpoint.endpoint),
                             requirement: requirement, onDisconnect: callback)
        })
        let subscription = MacSubscription(id: UUID(), name: "fixture", source: "fixture",
            payload: "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]", nodeCount: 1, updatedAt: .distantPast)
        var failure: (any Error)?
        do {
            for iteration in 0..<3 {
                controller.start(subscriptions: [subscription], selectedSubscriptionID: subscription.id,
                    selectedNodeName: "fixture", availableNodeNames: ["fixture"], preferTun: true)
                for _ in 0..<1000 where controller.state == .starting { try await Task.sleep(for: .milliseconds(10)) }
                try #require(controller.state == .tun, Comment(rawValue: controller.message))
                #expect(controller.processIdentifier == nil)
                if iteration < 2 {
                    await controller.stop()
                    #expect(controller.state == .stopped)
                    #expect(!controller.hasOwnedSession)
                } else {
                    await listener.stop()
                    for _ in 0..<200 where controller.state == .tun { try await Task.sleep(for: .milliseconds(10)) }
                    #expect(controller.state == .failed)
                    #expect(controller.subscriptionHostResolver == nil)
                }
            }
        } catch { failure = error }
        await controller.stop()
        await listener.stop()
        let stopped = await service.shutdown()
        #expect(stopped)
        if stopped { try FileManager.default.removeItem(at: root) }
        if let failure { throw failure }
    }

    private func ownRequirement() throws -> String {
        var code: SecCode?
        try #require(SecCodeCopySelf([], &code) == errSecSuccess)
        var staticCode: SecStaticCode?
        try #require(SecCodeCopyStaticCode(#require(code), [], &staticCode) == errSecSuccess)
        var requirement: SecRequirement?
        try #require(SecCodeCopyDesignatedRequirement(#require(staticCode), [], &requirement) == errSecSuccess)
        var text: CFString?
        try #require(SecRequirementCopyString(#require(requirement), [], &text) == errSecSuccess)
        return try #require(text) as String
    }

    @MainActor private final class GrantedRegistration: CoreServiceRegistration {
        var status: SMAppService.Status { .enabled }
        func register() throws { Issue.record("Unexpected registration") }
        func unregister() async throws { Issue.record("Unexpected unregistration") }
        func openSettings() { Issue.record("Unexpected settings change") }
    }
}
