import Darwin
import Foundation
import Testing
@testable import WeaveMac

@Suite("Actual core forwarding readiness", .serialized) @MainActor
struct CoreReadinessTests {
    @Test(arguments: [false, true])
    func controllerCannotReportReadyWhileForwardingIsBlocked(cancel: Bool) async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let runtime = try fixture.runtime()
        let core = try fixture.launch(runtime)
        defer { if core.isRunning { core.terminate(); core.waitUntilExit() } }
        var finished = false
        let starting = Task {
            defer { finished = true }
            try await MihomoController.waitUntilReady(runtime: runtime, pid: core.processIdentifier, requireTun: false)
        }
        do {
            try await fixture.waitForRules()
            let configuration = URLSessionConfiguration.ephemeral
            configuration.connectionProxyDictionary = ["HTTPEnable": 0, "HTTPSEnable": 0, "SOCKSEnable": 0]
            configuration.timeoutIntervalForResource = 2
            let session = URLSession(configuration: configuration)
            defer { session.invalidateAndCancel() }
            var request = URLRequest(url: URL(string: "http://127.0.0.1:\(runtime.controllerPort)/configs")!)
            request.setValue("Bearer \(runtime.secret)", forHTTPHeaderField: "Authorization")
            let (_, response) = try await session.data(for: request)
            #expect((response as? HTTPURLResponse)?.statusCode == 200)
            try await Task.sleep(for: .milliseconds(1200))
            #expect(!finished, "Controller and mixed listener exist before forwarding is enabled.")
            if cancel {
                starting.cancel()
                await #expect(throws: CancellationError.self) { try await starting.value }
                #expect(!MihomoController.listenerBelongsToProcess(port: runtime.readinessTargetPort, pid: getpid()))
            } else {
                try Data().write(to: fixture.root.appendingPathComponent("release"))
                try await starting.value
                #expect(!MihomoController.listenerBelongsToProcess(port: runtime.readinessTargetPort, pid: getpid()))
                // Explicit proxy use keeps the synthetic destination inside the loopback fixture.
                let result = try await Task.detached {
                    try BoundedCommand.run(executable: "/usr/bin/curl", arguments: ["--silent", "--show-error", "--max-time", "3", "--noproxy", "", "--proxy", "http://127.0.0.1:\(runtime.mixedPort)", "http://198.51.100.42/check"], timeout: 5)
                }.value
                #expect(result == "weave-session-upstream")
            }
        } catch {
            starting.cancel()
            _ = await starting.result
            throw error
        }
    }

    @Test(arguments: ["echo", "short", "hang"])
    func unrelatedResponseCannotConfirmForwarding(kind: String) async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let runtime = try fixture.runtime()
        let probe = try CoreReadinessProbe(port: runtime.readinessTargetPort)
        try await probe.start()
        let began = ContinuousClock.now
        let result = try await probe.isForwarding(port: fixture.port(kind))
        await probe.stop()
        #expect(!result)
        #expect(began.duration(to: .now) < .seconds(3))
        #expect(!MihomoController.listenerBelongsToProcess(port: runtime.readinessTargetPort, pid: getpid()))
    }

    @Test func cancelPendingExchangeClosesBothSockets() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let runtime = try fixture.runtime()
        let probe = try CoreReadinessProbe(port: runtime.readinessTargetPort)
        try await probe.start()
        let port = try fixture.port("hang")
        let pending = Task { try await probe.isForwarding(port: port) }
        try await Task.sleep(for: .milliseconds(50))
        let began = ContinuousClock.now
        pending.cancel()
        await #expect(throws: CancellationError.self) { try await pending.value }
        await probe.stop()
        #expect(began.duration(to: .now) < .seconds(2))
        #expect(!MihomoController.listenerBelongsToProcess(port: runtime.readinessTargetPort, pid: getpid()))
    }

    @Test func alreadyCancelledStartStillCleansUp() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let runtime = try fixture.runtime()
        let probe = try CoreReadinessProbe(port: runtime.readinessTargetPort)
        let pending = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            try await probe.start()
        }
        await #expect(throws: CancellationError.self) { try await pending.value }
        await probe.stop()
        #expect(!MihomoController.listenerBelongsToProcess(port: runtime.readinessTargetPort, pid: getpid()))
    }

    @Test func occupiedResponderPortFailsWithoutClaimingTheOtherListener() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let probe = try CoreReadinessProbe(port: fixture.port("echo"))
        await #expect(throws: (any Error).self) { try await probe.start() }
        await probe.stop()
        #expect(fixture.isRunning)
    }

    private final class Fixture {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveReadiness-\(UUID())")
        private let server = Process()
        private let coreURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
        private var ports: [String: Int] = [:]

        init() throws {
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
            let script = coreURL.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
                .appendingPathComponent("tests/fixtures/core-readiness/server.py")
            server.executableURL = URL(fileURLWithPath: "/usr/bin/python3")
            server.arguments = [script.path, root.path]
            server.standardOutput = FileHandle.nullDevice
            server.standardError = FileHandle.nullDevice
            do {
                try server.run()
                for _ in 0..<500 {
                    if let data = try? Data(contentsOf: root.appendingPathComponent("ports.json")),
                       let value = try? JSONDecoder().decode([String: Int].self, from: data) {
                        ports = value
                        return
                    }
                    if !server.isRunning { break }
                    Thread.sleep(forTimeInterval: 0.01)
                }
                throw WeaveMacError.message("Readiness fixture did not start")
            } catch { close(); throw error }
        }

        var isRunning: Bool { server.isRunning }
        func port(_ name: String) throws -> UInt16 { try UInt16(#require(ports[name])) }

        func runtime() throws -> MacRuntime {
            let upstream = try #require(ports["upstream"])
            let rules = try #require(ports["rules"])
            let subscription = MacSubscription(id: UUID(), name: "Fixture", source: "file://fixture",
                payload: "proxies: [{name: Fixture, type: http, server: 127.0.0.1, port: \(upstream)}]",
                nodeCount: 1, updatedAt: .distantPast)
            let runtime = try MihomoController.prepareRuntime(selected: subscription, selectedNodeName: "Fixture", enableTun: false, root: root.appendingPathComponent("sessions"))
            let config = runtime.directory.url.appendingPathComponent("config.yaml")
            var yaml = try String(contentsOf: config, encoding: .utf8)
            yaml += "\nrule-providers:\n  startup-gate:\n    type: http\n    behavior: domain\n    format: yaml\n    url: http://127.0.0.1:\(rules)/rules\n    path: ./startup-gate.yaml\n    proxy: DIRECT\n    interval: 86400\n"
            try yaml.write(to: config, atomically: true, encoding: .utf8)
            return runtime
        }

        func launch(_ runtime: MacRuntime) throws -> Process {
            let core = Process()
            core.executableURL = coreURL
            core.arguments = ["-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path]
            core.standardInput = runtime.directory.lease
            core.standardOutput = FileHandle.nullDevice
            core.standardError = FileHandle.nullDevice
            try core.run()
            return core
        }

        func waitForRules() async throws {
            for _ in 0..<500 {
                if FileManager.default.fileExists(atPath: root.appendingPathComponent("requested").path) { return }
                try await Task.sleep(for: .milliseconds(20))
            }
            throw WeaveMacError.message("Core did not request gated rules")
        }

        func close() {
            if server.isRunning { server.terminate(); server.waitUntilExit() }
            try? FileManager.default.removeItem(at: root)
        }
    }
}
