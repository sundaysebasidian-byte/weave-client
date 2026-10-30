import WeaveCoreRuntime
import Darwin
import Foundation
import Testing
@testable import WeaveMac

@Suite("Private runtime lifecycle")
struct RuntimeDirectoryTests {
    @Test(arguments: [true, false]) func coreAcceptsGeneratedConfiguration(fixed: Bool) throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try CoreRuntime.prepare(provider: subscription.payload, selectedNodeName: fixed ? "Fixture" : nil, enableTun: false, root: root)
        defer { _ = try? runtime.directory.removeAfterCoreExit() }
        let process = Process()
        let output = Pipe()
        process.executableURL = coreExecutable
        process.arguments = ["-t", "-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path]
        process.standardOutput = output
        process.standardError = output
        try process.run()
        let diagnostics = output.fileHandleForReading.readDataToEndOfFile()
        process.waitUntilExit()
        #expect(process.terminationStatus == 0, Comment(rawValue: String(decoding: diagnostics, as: UTF8.self)))
    }
    @Test func sessionsArePrivateAndActiveLeasesCannotBeReclaimed() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try CoreRuntime.prepare(provider: subscription.payload, selectedNodeName: "Fixture", enableTun: false, root: root)
        #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
        #expect(runtime.mixedPort != runtime.controllerPort)
        let config = runtime.directory.url.appendingPathComponent("config.yaml")
        let mode = try FileManager.default.attributesOfItem(atPath: config.path)[.posixPermissions] as? NSNumber
        #expect(mode?.intValue == 0o600)
        let dirMode = try FileManager.default.attributesOfItem(atPath: runtime.directory.url.path)[.posixPermissions] as? NSNumber
        #expect(dirMode?.intValue == 0o700)
        let yaml = try String(contentsOf: config, encoding: .utf8)
        #expect(yaml.contains(runtime.secret))
        #expect(!yaml.contains("health-check:"))
        #expect(!yaml.contains("type: url-test"))
        #expect(try runtime.directory.removeAfterCoreExit())
        #expect(!FileManager.default.fileExists(atPath: config.path))
    }

    @Test func childLeaseSurvivesParentHandleAndAllowsLaterRecovery() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let directory = try RuntimeDirectory(root: root)
        let child = Process()
        child.executableURL = URL(fileURLWithPath: "/bin/sleep")
        child.arguments = ["5"]
        child.standardInput = directory.lease
        try child.run()
        defer { if child.isRunning { child.terminate(); child.waitUntilExit() } }
        try directory.lease.close() // Simulates loss of the parent process's lease.
        #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
        child.terminate()
        child.waitUntilExit()
        #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 1)
    }

    @Test func recoveryPreservesUnknownFilesAndLinks() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let directory = try RuntimeDirectory(root: root)
        try Data("keep".utf8).write(to: directory.url.appendingPathComponent("user-data"))
        #expect(try directory.removeAfterCoreExit() == false)
        try directory.lease.close()
        #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
        #expect(FileManager.default.fileExists(atPath: directory.url.appendingPathComponent("user-data").path))
    }

    @Test @MainActor func actualCoreConfigAndLocalLifecycle() async throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let core = try controller(root: root)
        core.start(subscriptions: [subscription], selectedSubscriptionID: subscription.id, selectedNodeName: "Fixture", availableNodeNames: ["Fixture"], preferTun: false)
        for _ in 0..<200 where core.state == .starting { try await Task.sleep(for: .milliseconds(50)) }
        let started = core.state
        let detail = core.message
        await core.stop()
        #expect(started == .localProxy, Comment(rawValue: detail))
        #expect(core.state == .stopped)
        let sessions = try FileManager.default.contentsOfDirectory(atPath: root.path).filter { UUID(uuidString: $0) != nil }
        #expect(sessions.isEmpty)
    }

    @Test @MainActor func cancelledConcurrentStopsFinishStartupCleanup() async throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let core = try controller(root: root)
        for _ in 0..<3 {
            core.start(subscriptions: [subscription], selectedSubscriptionID: subscription.id, selectedNodeName: "Fixture", availableNodeNames: ["Fixture"], preferTun: false)
            await Task.yield()
            let first = Task { await core.stop() }
            first.cancel()
            let second = Task { await core.stop() }
            await first.value
            await second.value
            #expect(core.state == .stopped)
            #expect(core.processIdentifier == nil)
        }
        let sessions = (try? FileManager.default.contentsOfDirectory(atPath: root.path)) ?? []
        #expect(sessions.filter { UUID(uuidString: $0) != nil }.isEmpty)
    }

    @Test @MainActor func unexpectedExitCanRecoverAndReconnect() async throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let core = try controller(root: root)
        core.start(subscriptions: [subscription], selectedSubscriptionID: subscription.id, selectedNodeName: "Fixture", availableNodeNames: ["Fixture"], preferTun: false)
        for _ in 0..<200 where core.state == .starting { try await Task.sleep(for: .milliseconds(50)) }
        guard core.state == .localProxy, let pid = core.processIdentifier else {
            Issue.record(Comment(rawValue: core.message))
            await core.stop()
            return
        }
        #expect(kill(pid, SIGKILL) == 0)
        for _ in 0..<200 where core.state != .failed { try await Task.sleep(for: .milliseconds(50)) }
        let failed = core.state
        let failure = core.message
        core.start(subscriptions: [subscription], selectedSubscriptionID: subscription.id, selectedNodeName: "Fixture", availableNodeNames: ["Fixture"], preferTun: false)
        for _ in 0..<200 where core.state == .starting { try await Task.sleep(for: .milliseconds(50)) }
        let reconnected = core.state
        await core.stop()
        #expect(failed == .failed)
        #expect(failure.contains("核心意外退出"))
        #expect(reconnected == .localProxy)
        #expect(core.state == .stopped)
    }

    @MainActor private func controller(root: URL) throws -> MihomoController {
        try #require(FileManager.default.isExecutableFile(atPath: coreExecutable.path))
        let proxy = SystemProxyManager(transactionURL: root.appendingPathComponent("proxy.json"), runCommand: { _, args in
            // Fake the OS boundary; the real core only listens on random loopback ports.
            switch args[0] {
            case "-listallnetworkservices": return "Fixture\n"
            case "-n", "-listnetworkserviceorder": return ""
            case "-getwebproxy", "-getsecurewebproxy", "-getsocksfirewallproxy": return "Enabled: No\nServer: \nPort: 0\nAuthenticated Proxy Enabled: 0\n"
            case "-getautoproxyurl": return "Enabled: No\nURL: \n"
            default: return ""
            }
        })
        return MihomoController(systemProxy: proxy, executable: coreExecutable, runtimeRoot: root)
    }

    private func temporaryRoot() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString) }
    private var coreExecutable: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
    }
    private var subscription: MacSubscription {
        MacSubscription(id: UUID(uuidString: "CC7CC3A1-4484-4874-8824-0FF3BCC9BC44")!, name: "Fixture", source: "file://fixture",
                        payload: "proxies:\n  - name: 'Fixture'\n    type: socks5\n    server: 127.0.0.1\n    port: 9\n", nodeCount: 1, updatedAt: .distantPast)
    }
}
