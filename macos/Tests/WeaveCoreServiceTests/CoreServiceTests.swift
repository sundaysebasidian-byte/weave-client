import Darwin
import Foundation
import Security
import Testing
import WeaveCoreRuntime
@testable import WeaveCoreBroker
@testable import WeaveCoreService

@Suite("Actual core broker backend", .serialized) @MainActor
struct CoreServiceTests {
    @Test func realCoreStartsStopsAndCanReconnect() async throws {
        try await withService { service, _ in
            let session = service.makeSession(for: peer)
            for _ in 0..<2 {
                let response = await session.handle(.start(provider: provider, node: "fixture"))
                guard case .state(.running, let port) = response else { Issue.record("Expected running, got \(response)"); return }
                #expect(port != nil)
                #expect(await session.handle(.status) == response)
                #expect(await session.handle(.stop) == .state(.stopped, mixedPort: nil))
                #expect(await session.child == nil)
                #expect(await session.runtime == nil)
            }
        }
    }

    @Test func independentServicesShareTheLeaseAndReleaseItAfterConfirmedCleanup() async throws {
        try await withService { first, root in
            let second = try CoreService(executable: coreURL, runtimeRoot: root, enableTun: false)
            let owner = first.makeSession(for: peer)
            let contender = second.makeSession(for: peer)
            do {
                let firstReply = await owner.handle(.start(provider: provider, node: "fixture"))
                guard case .state(.running, _) = firstReply else {
                    Issue.record("First service did not start: \(firstReply)"); return
                }
                let heldRuntime = try #require(await owner.runtime)
                #expect(await contender.handle(.start(provider: provider, node: "fixture")) == .failure(.busy))
                let unknown = heldRuntime.directory.url.appendingPathComponent("retained-fixture")
                try Data("retain".utf8).write(to: unknown)
                #expect(await owner.handle(.stop) == .failure(.stopFailed))
                #expect(await contender.handle(.start(provider: provider, node: "fixture")) == .failure(.busy))
                try FileManager.default.removeItem(at: unknown)
                #expect(await owner.handle(.stop) == .state(.stopped, mixedPort: nil))
                // Holding an old runtime value must not keep the parent's lease open.
                let restarted = await contender.handle(.start(provider: provider, node: "fixture"))
                if case .state(.running, _) = restarted {} else { Issue.record("Independent service could not acquire the released lease: \(restarted)") }
                #expect(!FileManager.default.fileExists(atPath: heldRuntime.directory.url.path))
            } catch { _ = await second.shutdown(); throw error }
            #expect(await second.shutdown())
        }
    }

    @Test func everyConcurrentStopWaiterCanImmediatelyStartAgain() async throws {
        try await withService { service, _ in
            let session = service.makeSession(for: peer)
            for _ in 0..<5 {
                guard case .state(.running, _) = await session.handle(.start(provider: provider, node: "fixture")) else {
                    Issue.record("Initial core did not start"); return
                }
                let child = try #require(await session.child)
                #expect(kill(child.processIdentifier, SIGSTOP) == 0)
                let first = Task.detached(priority: .background) { await session.handle(.stop) }
                try await waitUntil { await session.handle(.status) == .state(.stopping, mixedPort: nil) }
                let second = Task.detached(priority: .high) {
                    let stopped = await session.handle(.stop)
                    let next = await session.handle(.start(provider: "invalid", node: nil))
                    return (stopped, next)
                }
                let result = await second.value
                #expect(await first.value == .state(.stopped, mixedPort: nil))
                #expect(result.0 == .state(.stopped, mixedPort: nil))
                #expect(result.1 == .failure(.invalidProfile))
            }
        }
    }

    @Test func invalidProfileDoesNotLeaveRuntimeOrOwnership() async throws {
        try await withService { service, _ in
            let session = service.makeSession(for: peer)
            let response = await session.handle(.start(provider: "proxies: [{name: fixture, type: vmess, server: 127.0.0.1, port: 443, uuid: 11111111-1111-4111-8111-111111111111, alterId: 0, cipher: not-a-cipher}]", node: "fixture"))
            #expect(response == .failure(.invalidProfile))
            #expect(await session.child == nil)
            #expect(await session.runtime == nil)
            let next = service.makeSession(for: peer)
            guard case .state(.running, _) = await next.handle(.start(provider: provider, node: "fixture")) else { Issue.record("Slot was not released after invalid profile"); return }
        }
    }

    @Test func foreignConnectionCannotStopOrQueryTheOwnersCore() async throws {
        try await withService { service, _ in
            let owner = service.makeSession(for: peer)
            let other = service.makeSession(for: BrokerPeer(userID: getuid() + 1, sessionID: 2, processID: getpid()))
            let running = await owner.handle(.start(provider: provider, node: "fixture"))
            guard case .state(.running, _) = running else { Issue.record("Core did not start"); return }
            #expect(await other.handle(.start(provider: provider, node: "fixture")) == .failure(.busy))
            #expect(await other.handle(.resolve(host: "example.invalid")) == .failure(.unavailable))
            #expect(await other.handle(.stop) == .state(.stopped, mixedPort: nil))
            #expect(await owner.handle(.status) == running)
            await owner.close()
            guard case .state(.running, _) = await other.handle(.start(provider: provider, node: "fixture")) else { Issue.record("Closed owner did not release the slot"); return }
        }
    }

    @Test(arguments: [false, true])
    func cancellationStopsAndAwaitsTheActualChecker(close: Bool) async throws {
        try await withService(factory: { runtime, mode, onExit in
            #expect(mode == .checkConfiguration)
            let script = runtime.directory.url.deletingLastPathComponent().appendingPathComponent("checker.sh")
            try "#!/bin/sh\nexec /bin/sleep 30\n".write(to: script, atomically: true, encoding: .utf8)
            try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: script.path)
            return try CoreProcess(executable: script, runtime: runtime, mode: mode, environment: .isolated, onExit: onExit)
        }) { service, root in
            let session = service.makeSession(for: peer)
            let starting = Task { await session.handle(.start(provider: provider, node: "fixture")) }
            try await waitUntil { await session.child != nil }
            let child = try #require(await session.child)
            #expect(kill(child.processIdentifier, SIGSTOP) == 0)
            #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
            if close { await session.close() }
            else {
                starting.cancel()
                let first = Task { await session.handle(.stop) }
                let second = Task { await session.handle(.stop) }
                #expect(await first.value == .state(.stopped, mixedPort: nil))
                #expect(await second.value == .state(.stopped, mixedPort: nil))
            }
            #expect(await starting.value == .failure(.unavailable))
            #expect(!child.isRunning)
            #expect(await session.child == nil)
            #expect(await session.runtime == nil)
            #expect(Set(try FileManager.default.contentsOfDirectory(atPath: root.path)) == ["checker.sh", ".service-lease"])
        }
    }

    @Test func failedCloseRetainsTheLiveChildAndExclusiveOwnerUntilRetry() async throws {
        let gate = StopGate()
        let executable = coreURL
        try await withService(factory: { runtime, mode, onExit in
            let child = try CoreProcess(executable: executable, runtime: runtime, mode: mode, environment: .isolated, onExit: onExit)
            return GatedChild(child: child, gate: gate)
        }) { service, root in
            defer { gate.open() }
            let session = service.makeSession(for: peer)
            guard case .state(.running, _) = await session.handle(.start(provider: provider, node: "fixture")) else { Issue.record("Core did not start"); return }
            let child = try #require(await session.child)
            let runtime = try #require(await session.runtime)
            await session.close()
            #expect(child.isRunning)
            #expect(FileManager.default.fileExists(atPath: runtime.directory.url.path))
            #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
            #expect(await service.makeSession(for: peer).handle(.start(provider: provider, node: "fixture")) == .failure(.busy))
            #expect(!(await service.shutdown()))
            gate.open()
            #expect(await service.shutdown())
            #expect(!child.isRunning)
            #expect(!FileManager.default.fileExists(atPath: runtime.directory.url.path))
        }
    }

    @Test func unknownRuntimeFilesAreRetainedUntilExplicitRecovery() async throws {
        try await withService { service, _ in
            let session = service.makeSession(for: peer)
            _ = await session.handle(.start(provider: provider, node: "fixture"))
            let runtime = try #require(await session.runtime)
            let child = try #require(await session.child)
            let unknown = runtime.directory.url.appendingPathComponent("unknown-user-file")
            try Data("retain".utf8).write(to: unknown)
            #expect(await session.handle(.stop) == .failure(.stopFailed))
            #expect(!child.isRunning)
            #expect(try Data(contentsOf: unknown) == Data("retain".utf8))
            #expect(await service.makeSession(for: peer).handle(.start(provider: provider, node: "fixture")) == .failure(.busy))
            try FileManager.default.removeItem(at: unknown) // Only the synthetic file created above.
            #expect(await session.handle(.stop) == .state(.stopped, mixedPort: nil))
        }
    }

    @Test func unexpectedExitCleansOwnedRuntimeAndReportsFailure() async throws {
        try await withService { service, _ in
            let session = service.makeSession(for: peer)
            _ = await session.handle(.start(provider: provider, node: "fixture"))
            let child = try #require(await session.child)
            let runtime = try #require(await session.runtime)
            #expect(kill(child.processIdentifier, SIGKILL) == 0)
            try await waitUntil { await session.runtime == nil }
            #expect(await session.handle(.status) == .state(.failed, mixedPort: nil))
            #expect(!FileManager.default.fileExists(atPath: runtime.directory.url.path))
            guard case .state(.running, _) = await session.handle(.start(provider: provider, node: "fixture")) else { Issue.record("Reconnect failed after crash cleanup"); return }
        }
    }

    @Test func serviceShutdownClosesIdleAndFutureStarts() async throws {
        try await withService { service, _ in
            let session = service.makeSession(for: peer)
            #expect(await service.shutdown())
            #expect(await session.handle(.start(provider: provider, node: "fixture")) == .failure(.unavailable))
            let rootPeer = service.makeSession(for: BrokerPeer(userID: 0, sessionID: 1, processID: getpid()))
            #expect(await rootPeer.handle(.status) == .failure(.unauthorized))
        }
    }

    @Test func nativeXPCForwardsResolvesAndCleansUpOnDisconnect() async throws {
        let network = NetworkFixture(root: coreURL.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent())
        do {
            let ports = try await network.start()
            let executable = coreURL
            let payload = "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: \(ports.upstream)}]"
            try await withService(factory: { runtime, mode, onExit in
                if mode == .checkConfiguration {
                    let file = runtime.directory.url.appendingPathComponent("config.yaml")
                    var config = try String(contentsOf: file, encoding: .utf8)
                    for address in ["223.5.5.5", "119.29.29.29", "https://doh.pub/dns-query", "https://dns.alidns.com/dns-query", "https://1.1.1.1/dns-query"] {
                        config = config.replacingOccurrences(of: address, with: "udp://127.0.0.1:\(ports.dns)")
                    }
                    try config.write(to: file, atomically: true, encoding: .utf8)
                }
                return try CoreProcess(executable: executable, runtime: runtime, mode: mode, environment: .isolated, onExit: onExit)
            }) { service, _ in
                let requirement = try ownRequirement()
                let listener = NSXPCListener.anonymous()
                let box = SessionBox()
                let server = try BrokerListener(listener: listener, requirement: requirement) { peer in
                    let session = service.makeSession(for: peer)
                    box.set(session)
                    return session
                }
                server.resume()
                let client = try BrokerClient(connection: NSXPCConnection(listenerEndpoint: listener.endpoint), requirement: requirement, timeout: 10)
                do {
                    guard case .state(.running, let mixedPort) = try await client.send(.start(provider: payload, node: "fixture")) else { throw BrokerFailure.startFailed }
                    let port = try #require(mixedPort)
                    let response = try await Task.detached {
                        try BoundedCommand.run(executable: "/usr/bin/curl", arguments: ["--silent", "--show-error", "--fail", "--max-time", "3",
                            "--noproxy", "", "--proxy", "http://127.0.0.1:\(port)", "http://198.51.100.42/service-check"], timeout: 5)
                    }.value
                    #expect(response == "weave-session-upstream")
                    #expect(try await client.send(.resolve(host: "service-check.invalid")) == .addresses(["2606:4700:4700::1111", "8.8.8.8"]))
                    let session = try #require(box.value)
                    let child = try #require(await session.child)
                    let runtime = try #require(await session.runtime)
                    client.close()
                    try await waitUntil { await session.runtime == nil }
                    #expect(!child.isRunning)
                    #expect(!FileManager.default.fileExists(atPath: runtime.directory.url.path))
                    #expect(await session.handle(.start(provider: provider, node: "fixture")) == .failure(.unavailable))
                } catch {
                    client.close()
                    await server.stop()
                    throw error
                }
                await server.stop()
            }
        } catch { await network.close(); throw error }
        await network.close()
    }

    private func withService(factory: ServiceCoreFactory? = nil, body: (CoreService, URL) async throws -> Void) async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveService-\(UUID())")
        let service: CoreService
        if let factory { service = try CoreService(runtimeRoot: root, enableTun: false, factory: factory) }
        else { service = try CoreService(executable: coreURL, runtimeRoot: root, enableTun: false) }
        var failure: (any Error)?
        do { try await body(service, root) } catch { failure = error }
        let stopped = await service.shutdown()
        #expect(stopped)
        if stopped { try? FileManager.default.removeItem(at: root) }
        if let failure { throw failure }
    }

    private func waitUntil(_ predicate: () async -> Bool) async throws {
        for _ in 0..<500 {
            if await predicate() { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        throw BrokerTransportError.timedOut
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
    private var peer: BrokerPeer { BrokerPeer(userID: getuid(), sessionID: 1, processID: getpid()) }
    private var provider: String { "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]" }
    private var coreURL: URL { URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("Resources/mihomo") }
}

private final class SessionBox: @unchecked Sendable {
    private let lock = NSLock()
    private var session: CoreServiceSession?
    var value: CoreServiceSession? { lock.withLock { session } }
    func set(_ value: CoreServiceSession) { lock.withLock { session = value } }
}

private final class StopGate: @unchecked Sendable {
    private let lock = NSLock()
    private var opened = false
    var allowsStop: Bool { lock.withLock { opened } }
    func open() { lock.withLock { opened = true } }
}
private struct GatedChild: ServiceCoreChild {
    let child: CoreProcess
    let gate: StopGate
    var processIdentifier: Int32 { child.processIdentifier }
    var isRunning: Bool { child.isRunning }
    func waitForExit(timeout: Duration) async throws -> Int32 { try await child.waitForExit(timeout: timeout) }
    func stop() async -> Bool { gate.allowsStop ? await child.stop() : false }
}

@MainActor private final class NetworkFixture {
    private let source: URL
    private let root = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveServiceNetwork-\(UUID())")
    private var servers: [Process] = []

    init(root: URL) { source = root }

    func start() async throws -> (upstream: Int, dns: Int) {
        for (name, fixture) in [("proxy", "core-readiness"), ("dns", "https")] {
            let folder = root.appendingPathComponent(name)
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let server = Process()
            server.executableURL = URL(fileURLWithPath: "/usr/bin/python3")
            server.arguments = [source.appendingPathComponent("tests/fixtures/\(fixture)/server.py").path, folder.path]
            server.standardOutput = FileHandle.nullDevice
            server.standardError = FileHandle.nullDevice
            server.terminationHandler = { _ in }
            try server.run()
            servers.append(server)
        }
        for _ in 0..<500 {
            if let data = try? Data(contentsOf: root.appendingPathComponent("proxy/ports.json")),
               let ports = try? JSONDecoder().decode([String: Int].self, from: data), let upstream = ports["upstream"],
               let value = try? String(contentsOf: root.appendingPathComponent("dns/dns-port.txt"), encoding: .utf8), let dns = Int(value) {
                return (upstream, dns)
            }
            guard servers.allSatisfy(\.isRunning) else { throw BrokerFailure.unavailable }
            try await Task.sleep(for: .milliseconds(10))
        }
        throw BrokerTransportError.timedOut
    }

    func close() async {
        var allExited = true
        for server in servers {
            let exited = await Task.detached {
                if server.isRunning { server.terminate() }
                for _ in 0..<30 where server.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
                if server.isRunning { kill(server.processIdentifier, SIGKILL) }
                for _ in 0..<20 where server.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
                return !server.isRunning
            }.value
            #expect(exited)
            allExited = allExited && exited
        }
        if allExited { try? FileManager.default.removeItem(at: root) }
    }
}
