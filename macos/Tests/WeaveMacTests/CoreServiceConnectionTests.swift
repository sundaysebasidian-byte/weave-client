import Foundation
import ServiceManagement
import Testing
import WeaveCoreBroker
import WeaveCoreRuntime
@testable import WeaveMac

@Suite("GUI TUN service integration", .serialized) @MainActor
struct CoreServiceConnectionTests {
    @Test func unsignedAppCannotRegisterOrSendProvider() async throws {
        let registration = Registration()
        let manager = CoreServiceManager(registration: registration, validateIdentity: { throw BrokerFailure.unauthorized })
        manager.enable()
        #expect(registration.registerCount == 0)
        #expect(throws: (any Error).self) { try manager.requireEnabled() }
        let fixture = Fixture(manager: manager)
        fixture.start()
        #expect(fixture.controller.state == .failed)
        #expect(fixture.channel.requests.isEmpty)
        #expect(!fixture.controller.hasOwnedSession)
    }

    @Test func approvalMustBeObservedBeforeConnectingAndCanBeRevoked() throws {
        let registration = Registration()
        let manager = CoreServiceManager(registration: registration, validateIdentity: {})
        manager.enable()
        #expect(registration.registerCount == 1)
        #expect(registration.openCount == 1)
        #expect(manager.status == .requiresApproval)
        #expect(throws: (any Error).self) { try manager.requireEnabled() }
        registration.status = .enabled
        try manager.requireEnabled()
        registration.status = .requiresApproval
        #expect(throws: (any Error).self) { try manager.requireEnabled() }
        #expect(registration.registerCount == 1)
    }

    @Test func tunUsesBrokerForProviderDnsAndConfirmedStop() async throws {
        let fixture = Fixture()
        fixture.start()
        try await wait { fixture.controller.state != .starting }
        #expect(fixture.controller.state == .tun)
        #expect(fixture.controller.processIdentifier == nil)
        let resolver = try #require(fixture.controller.subscriptionHostResolver)
        #expect(try await resolver("example.invalid") == ["203.0.113.7"])
        #expect(fixture.channel.requests.contains(.resolve(host: "example.invalid")))
        await fixture.controller.stop()
        #expect(fixture.controller.state == .stopped)
        #expect(!fixture.controller.hasOwnedSession)
        #expect(fixture.channel.closed)
        #expect(!FileManager.default.fileExists(atPath: fixture.root.path))
    }

    @Test func stopFailureRetainsTheSessionForRetryAndPreventsUnregistration() async throws {
        let fixture = Fixture(stopReplies: [.failure(.stopFailed), .state(.stopped, mixedPort: nil)])
        fixture.start()
        try await wait { fixture.controller.state == .tun }
        await fixture.controller.disableService()
        #expect(fixture.controller.state == .failed)
        #expect(fixture.controller.hasOwnedSession)
        #expect(!fixture.channel.closed)
        #expect(fixture.registration.unregisterCount == 0)
        await fixture.controller.disableService()
        #expect(fixture.controller.state == .stopped)
        #expect(fixture.registration.unregisterCount == 1)
    }

    @Test func serviceDeathUpdatesTheGuiWithoutLocalFallback() async throws {
        let fixture = Fixture(statusReply: .state(.failed, mixedPort: nil))
        fixture.start()
        try await wait { fixture.controller.state == .tun }
        await fixture.controller.checkServiceConnection()
        #expect(fixture.controller.state == .failed)
        #expect(!fixture.channel.closed)
        #expect(fixture.controller.hasOwnedSession)
        #expect(fixture.controller.processIdentifier == nil)
        #expect(fixture.controller.subscriptionHostResolver == nil)
        await fixture.controller.stop()
        #expect(fixture.controller.state == .stopped)
    }

    @Test func lostTransportNeverClaimsAConfirmedStop() async throws {
        let fixture = Fixture()
        fixture.start()
        try await wait { fixture.controller.state == .tun }
        fixture.disconnect?()
        try await wait { fixture.controller.state == .failed }
        await fixture.controller.stop()
        #expect(fixture.controller.state == .failed)
        #expect(fixture.controller.message.contains("尚未确认退出"))
    }

    @Test func cancellingAnInFlightStartCannotPublishConnectedState() async throws {
        let fixture = Fixture(delayStart: true)
        fixture.start()
        try await wait { fixture.channel.startPending }
        let stopping = Task { await fixture.controller.stop() }
        await Task.yield()
        fixture.channel.finishStart()
        await stopping.value
        #expect(fixture.controller.state == .stopped)
        #expect(fixture.channel.closed)
        #expect(fixture.channel.requests.contains(.stop))
    }

    @Test func stoppingDuringAHealthQueryDoesNotInvalidateTheStopChannel() async throws {
        let fixture = Fixture(delayStatus: true)
        fixture.start()
        try await wait(attempts: 500) { fixture.channel.statusPending }
        await fixture.controller.stop()
        #expect(fixture.controller.state == .stopped)
        #expect(fixture.channel.requests.contains(.stop))
        #expect(fixture.channel.closed)
    }

    private func wait(attempts: Int = 200, _ condition: () -> Bool) async throws {
        for _ in 0..<attempts {
            if condition() { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        try #require(condition())
    }

    private final class Registration: CoreServiceRegistration {
        var status: SMAppService.Status = .notRegistered
        var registerCount = 0, unregisterCount = 0, openCount = 0
        func register() throws { registerCount += 1; status = .requiresApproval }
        func unregister() async throws { unregisterCount += 1; status = .notRegistered }
        func openSettings() { openCount += 1 }
    }

    @MainActor private final class Fixture {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveGuiBroker-\(UUID())")
        let registration = Registration()
        let channel: Channel
        var disconnect: (@Sendable () -> Void)?
        var controller: MihomoController!
        let subscription = MacSubscription(id: UUID(), name: "Fixture", source: "fixture", payload:
            "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]", nodeCount: 1, updatedAt: .distantPast)

        init(manager: CoreServiceManager? = nil, stopReplies: [BrokerReply] = [.state(.stopped, mixedPort: nil)],
             statusReply: BrokerReply = .state(.running, mixedPort: 17890), delayStart: Bool = false, delayStatus: Bool = false) {
            registration.status = .enabled
            channel = Channel(stopReplies: stopReplies, statusReply: statusReply, delayStart: delayStart, delayStatus: delayStatus)
            let proxy = SystemProxyManager(transactionURL: root.appendingPathComponent("proxy.json"), runCommand: { _, _ in
                Issue.record("TUN tests must never change system proxy settings")
                throw BrokerFailure.unavailable
            })
            controller = MihomoController(systemProxy: proxy, runtimeRoot: root,
                serviceManager: manager ?? CoreServiceManager(registration: registration, validateIdentity: {}),
                brokerFactory: { [weak self] callback in
                    guard let self else { throw BrokerFailure.unavailable }
                    self.disconnect = callback
                    return self.channel
                })
        }
        func start() {
            controller.start(subscriptions: [subscription], selectedSubscriptionID: subscription.id,
                selectedNodeName: "fixture", availableNodeNames: ["fixture"], preferTun: true)
        }
    }

    private final class Channel: TunBrokerChannel, @unchecked Sendable {
        private let lock = NSLock()
        private var recorded: [BrokerRequest] = []
        private var isClosed = false
        private var stops: [BrokerReply]
        private let statusReply: BrokerReply
        private let delayStart: Bool
        private let delayStatus: Bool
        private var cancelledStatus = false
        private var pendingStatus: CheckedContinuation<BrokerReply, Error>?
        var statusPending: Bool { lock.withLock { pendingStatus != nil } }
        private var pending: CheckedContinuation<BrokerReply, Never>?
        var requests: [BrokerRequest] { lock.withLock { recorded } }
        var closed: Bool { lock.withLock { isClosed } }
        var startPending: Bool { lock.withLock { pending != nil } }
        init(stopReplies: [BrokerReply], statusReply: BrokerReply, delayStart: Bool, delayStatus: Bool) {
            stops = stopReplies; self.statusReply = statusReply; self.delayStart = delayStart; self.delayStatus = delayStatus
        }
        func send(_ request: BrokerRequest) async throws -> BrokerReply {
            lock.withLock { recorded.append(request) }
            switch request {
            case .start:
                if delayStart { return await withCheckedContinuation { c in lock.withLock { pending = c } } }
                return .state(.running, mixedPort: 17890)
            case .stop:
                try await Task.sleep(for: .milliseconds(20))
                guard !closed else { throw BrokerTransportError.disconnected }
                return lock.withLock { stops.isEmpty ? .failure(.stopFailed) : stops.removeFirst() }
            case .status:
                if delayStatus {
                    return try await withTaskCancellationHandler {
                        try await withCheckedThrowingContinuation { c in
                            lock.withLock {
                                if cancelledStatus { c.resume(throwing: CancellationError()) }
                                else { pendingStatus = c }
                            }
                        }
                    } onCancel: { self.cancelStatus() }
                }
                return statusReply
            case .resolve: return .addresses(["203.0.113.7"])
            }
        }
        func finishStart() {
            let continuation = lock.withLock { let value = pending; pending = nil; return value }
            continuation?.resume(returning: .state(.running, mixedPort: 17890))
        }
        private func cancelStatus() {
            let continuation = lock.withLock {
                cancelledStatus = true
                let value = pendingStatus; pendingStatus = nil; return value
            }
            continuation?.resume(throwing: CancellationError())
        }
        func close() { lock.withLock { isClosed = true }; finishStart(); cancelStatus() }
    }
}
