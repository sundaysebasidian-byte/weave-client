import Darwin
import Foundation
import Security
import Testing
@testable import WeaveCoreBroker

@Suite("Native authenticated XPC broker", .serialized) @MainActor
struct BrokerTransportTests {
    @Test func nativeRequestsUseAuthenticatedPeerAndStructuredReplies() async throws {
        try await withFixture { fixture async throws in
            #expect(try await fixture.client.send(.status) == .state(.stopped, mixedPort: nil))
            #expect(try await fixture.client.send(.start(provider: "proxies: []", node: nil)) == .state(.running, mixedPort: 8080))
            #expect(try await fixture.client.send(.resolve(host: "example.invalid")) == .addresses(["8.8.8.8"]))
            #expect(try await fixture.client.send(.stop) == .state(.stopped, mixedPort: nil))
            #expect(fixture.peer.value?.userID == geteuid())
            #expect(fixture.peer.value?.processID == getpid())
        }
    }

    @Test(arguments: [false, true]) func wrongSignatureIsRejectedInBothDirections(rejectClient: Bool) async throws {
        let forbidden = "identifier \"io.weave.forbidden-test-peer\""
        try await withFixture(serverRequirement: rejectClient ? forbidden : nil, clientRequirement: rejectClient ? nil : forbidden, prime: false) { fixture async throws in
            await #expect(throws: (any Error).self) { try await fixture.client.send(.start(provider: "synthetic-secret", node: nil)) }
            #expect(await fixture.session.requests.allSatisfy { $0 == .status })
        }
    }

    @Test func cancelledQueriesDrainTheirRepliesAndKeepTheSessionUsable() async throws {
        try await withFixture { fixture async throws in
            await fixture.session.holdQueries()
            let query = Task { try await fixture.client.send(.resolve(host: "example.invalid")) }
            try await fixture.waitForCount(1)
            query.cancel()
            await #expect(throws: CancellationError.self) { try await query.value }
            #expect(try await fixture.client.send(.status) == .state(.stopped, mixedPort: nil))
            await fixture.session.release()
            try await fixture.waitForCompleted(2)
            #expect(try await fixture.client.send(.resolve(host: "example.invalid")) == .addresses(["8.8.8.8"]))
        }
    }

    @Test func stopRetainsItsSlotWhenQueriesFillCapacity() async throws {
        try await withFixture { fixture async throws in
            await fixture.session.holdQueries()
            let queries = (0..<3).map { index in Task { try await fixture.client.send(.resolve(host: "q\(index).invalid")) } }
            try await fixture.waitForCount(3)
            await #expect(throws: BrokerTransportError.self) { try await fixture.client.send(.status) }
            #expect(try await fixture.client.send(.stop) == .state(.stopped, mixedPort: nil))
            for query in queries { _ = try await query.value }
        }
    }

    @Test func cancelledQueriesCannotEvadeTheInFlightLimit() async throws {
        try await withFixture { fixture async throws in
            await fixture.session.holdQueries()
            let queries = (0..<3).map { index in Task { try await fixture.client.send(.resolve(host: "q\(index).invalid")) } }
            try await fixture.waitForCount(3)
            for query in queries { query.cancel() }
            for query in queries { await #expect(throws: CancellationError.self) { try await query.value } }
            await #expect(throws: BrokerTransportError.self) { try await fixture.client.send(.status) }
            #expect(try await fixture.client.send(.stop) == .state(.stopped, mixedPort: nil))
            try await fixture.waitForCompleted(4)
            #expect(fixture.disconnections.value == 0)
        }
    }

    @Test func remoteShutdownFailsPendingRequestsAndNotifiesOnce() async throws {
        try await withFixture { fixture async throws in
            await fixture.session.holdQueries()
            let query = Task { try await fixture.client.send(.resolve(host: "example.invalid")) }
            try await fixture.waitForCount(1)
            await fixture.server.stop()
            await #expect(throws: BrokerTransportError.self) { try await query.value }
            try await fixture.waitForDisconnect()
            #expect(fixture.disconnections.value == 1)
            await #expect(throws: BrokerTransportError.self) { try await fixture.client.send(.status) }
            #expect(fixture.disconnections.value == 1)
        }
    }

    @Test(arguments: 0..<20) func stopCancelsEarlierStartBeforeReportingStopped(iteration: Int) async throws {
        try await withFixture { fixture async throws in
            await fixture.session.holdStart()
            let starting = Task { try await fixture.client.send(.start(provider: "proxies: []", node: nil)) }
            try await fixture.waitForCount(1)
            #expect(try await fixture.client.send(.stop) == .state(.stopped, mixedPort: nil))
            #expect(try await starting.value == .failure(.unavailable))
            #expect(await fixture.session.startCancelled)
        }
    }

    @Test func cancelledStartClosesSessionAndCancelsRemoteWork() async throws {
        try await withFixture { fixture async throws in
            await fixture.session.holdStart()
            let starting = Task { try await fixture.client.send(.start(provider: "proxies: []", node: nil)) }
            try await fixture.waitForCount(1)
            starting.cancel()
            await #expect(throws: CancellationError.self) { try await starting.value }
            try await fixture.waitForClosed()
            try await fixture.waitForCompleted(1)
            #expect(await fixture.session.startCancelled)
            await #expect(throws: BrokerTransportError.self) { try await fixture.client.send(.status) }
        }
    }

    @Test func replyTimeoutInvalidatesPendingWorkInsteadOfReusingTheSlot() async throws {
        try await withFixture(timeout: 0.15) { fixture async throws in
            await fixture.session.holdQueries()
            await #expect(throws: BrokerTransportError.self) { try await fixture.client.send(.resolve(host: "example.invalid")) }
            try await fixture.waitForClosed()
            await #expect(throws: BrokerTransportError.self) { try await fixture.client.send(.status) }
        }
    }

    @Test func alreadyCancelledCallDoesNotReachTheSession() async throws {
        try await withFixture { fixture async throws in
            let query = Task {
                withUnsafeCurrentTask { $0?.cancel() }
                return try await fixture.client.send(.start(provider: "proxies: []", node: nil))
            }
            await #expect(throws: CancellationError.self) { try await query.value }
            #expect(await fixture.session.requests.isEmpty)
            #expect(try await fixture.client.send(.status) == .state(.stopped, mixedPort: nil))
        }
    }

    private func withFixture(serverRequirement: String? = nil, clientRequirement: String? = nil, timeout: TimeInterval = 3, prime: Bool = true,
                             body: (Fixture) async throws -> Void) async throws {
        let fixture = try Fixture(serverRequirement: serverRequirement, clientRequirement: clientRequirement, timeout: timeout)
        do {
            if prime {
                _ = try await fixture.client.send(.status)
                await fixture.session.reset()
            }
            try await body(fixture)
        }
        catch { await fixture.close(); throw error }
        await fixture.close()
    }

    @MainActor private final class Fixture {
        let session = Session()
        let peer = PeerBox()
        let disconnections = CountBox()
        let server: BrokerListener
        let client: BrokerClient

        init(serverRequirement: String?, clientRequirement: String?, timeout: TimeInterval) throws {
            var code: SecCode?
            try #require(SecCodeCopySelf([], &code) == errSecSuccess)
            var staticCode: SecStaticCode?
            try #require(SecCodeCopyStaticCode(#require(code), [], &staticCode) == errSecSuccess)
            var requirement: SecRequirement?
            try #require(SecCodeCopyDesignatedRequirement(#require(staticCode), [], &requirement) == errSecSuccess)
            var text: CFString?
            try #require(SecRequirementCopyString(#require(requirement), [], &text) == errSecSuccess)
            let ownRequirement = try #require(text) as String
            let listener = NSXPCListener.anonymous()
            let session = self.session, peer = self.peer
            server = try BrokerListener(listener: listener, requirement: serverRequirement ?? ownRequirement) { identity in
                peer.set(identity)
                return session
            }
            server.resume()
            let disconnections = self.disconnections
            client = try BrokerClient(connection: NSXPCConnection(listenerEndpoint: listener.endpoint), requirement: clientRequirement ?? ownRequirement, timeout: timeout, onDisconnect: { disconnections.increment() })
        }
        func waitForCount(_ count: Int) async throws {
            for _ in 0..<300 {
                if await session.requests.count >= count { return }
                try await Task.sleep(for: .milliseconds(10))
            }
            throw BrokerTransportError.timedOut
        }
        func waitForCompleted(_ count: Int) async throws {
            for _ in 0..<300 {
                if await session.completed >= count { return }
                try await Task.sleep(for: .milliseconds(10))
            }
            throw BrokerTransportError.timedOut
        }
        func waitForClosed() async throws {
            for _ in 0..<300 {
                if await session.closed { return }
                try await Task.sleep(for: .milliseconds(10))
            }
            throw BrokerTransportError.timedOut
        }
        func waitForDisconnect() async throws {
            for _ in 0..<300 {
                if disconnections.value != 0 { return }
                try await Task.sleep(for: .milliseconds(10))
            }
            throw BrokerTransportError.timedOut
        }
        func close() async { client.close(); await server.stop() }
    }

    private final class CountBox: @unchecked Sendable {
        private let lock = NSLock()
        private var count = 0
        var value: Int { lock.withLock { count } }
        func increment() { lock.withLock { count += 1 } }
    }

    private final class PeerBox: @unchecked Sendable {
        private let lock = NSLock()
        private var peer: BrokerPeer?
        var value: BrokerPeer? { lock.withLock { peer } }
        func set(_ peer: BrokerPeer) { lock.withLock { self.peer = peer } }
    }

    private actor Session: BrokerSession {
        var requests: [BrokerRequest] = []
        var completed = 0
        var closed = false
        var startCancelled = false
        private var queryHeld = false
        private var startHeld = false
        func reset() { requests.removeAll(); completed = 0 }
        func holdQueries() { queryHeld = true }
        func holdStart() { startHeld = true }
        func release() { queryHeld = false; startHeld = false }
        func handle(_ request: BrokerRequest) async -> BrokerReply {
            requests.append(request)
            defer { completed += 1 }
            do {
                switch request {
                case .status: return .state(.stopped, mixedPort: nil)
                case .stop: release(); return .state(.stopped, mixedPort: nil)
                case .start:
                    while startHeld { try await Task.sleep(for: .milliseconds(10)) }
                    try Task.checkCancellation()
                    return .state(.running, mixedPort: 8080)
                case .resolve:
                    while queryHeld { try await Task.sleep(for: .milliseconds(10)) }
                    try Task.checkCancellation()
                    return .addresses(["8.8.8.8"])
                }
            } catch {
                if case .start = request { startCancelled = true }
                return .failure(.unavailable)
            }
        }
        func close() { release(); closed = true }
    }
}
