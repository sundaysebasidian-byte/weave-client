import Darwin
import Foundation
import Testing
import WeaveCoreRuntime

@Suite("Shared core process ownership", .serialized)
struct CoreProcessTests {
    @Test func cancelledConcurrentStopsEscalateAndRetainTheLeaseUntilExit() async throws {
        let root = temporaryRoot()
        let runtime = try CoreRuntime.prepare(provider: provider, selectedNodeName: "Fixture", enableTun: false, root: root)
        var core: CoreProcess?
        defer { if core?.isRunning != true { try? FileManager.default.removeItem(at: root) } }
        do {
            let child = try CoreProcess(executable: coreURL, runtime: runtime, onExit: {})
            core = child
            try await CoreRuntime.waitUntilReady(runtime: runtime, pid: child.processIdentifier, requireTun: false)
            #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
            // A stopped child cannot handle SIGTERM. Shutdown must escalate even if its
            // first waiter was already cancelled, and every waiter must observe actual exit.
            #expect(kill(child.processIdentifier, SIGSTOP) == 0)
            let began = ContinuousClock.now
            let first = Task {
                withUnsafeCurrentTask { $0?.cancel() }
                return await child.stop()
            }
            let second = Task { await child.stop() }
            let third = Task { await child.stop() }
            #expect(await first.value)
            #expect(await second.value)
            #expect(await third.value)
            #expect(!child.isRunning)
            #expect(began.duration(to: .now) < .seconds(4))
            #expect(await child.stop())
            #expect(try runtime.directory.removeAfterCoreExit())
        } catch {
            _ = await core?.stop()
            throw error
        }
    }

    @Test func checkerUsesFixedArgumentsAndAnIsolatedEnvironment() async throws {
        let root = temporaryRoot()
        let runtime = try CoreRuntime.prepare(provider: provider, selectedNodeName: "Fixture", enableTun: false, root: root)
        let script = root.appendingPathComponent("environment.sh")
        try """
        #!/bin/sh
        [ "$1" = "-t" ] && [ "$2" = "-d" ] && [ "$4" = "-f" ] || exit 17
        /usr/bin/env > "$3/environment-fixture"
        """.write(to: script, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: script.path)
        let child = try CoreProcess(executable: script, runtime: runtime, mode: .checkConfiguration, environment: .isolated, onExit: {})
        do {
            #expect(try await child.waitForExit(timeout: .seconds(3)) == 0)
            let output = runtime.directory.url.appendingPathComponent("environment-fixture")
            let entries = try String(contentsOf: output, encoding: .utf8).split(separator: "\n")
            let values = Dictionary(uniqueKeysWithValues: entries.compactMap { line -> (String, String)? in
                let pair = line.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
                return pair.count == 2 ? (String(pair[0]), String(pair[1])) : nil
            })
            #expect(values["HOME"] == runtime.directory.url.path)
            #expect(values["TMPDIR"] == runtime.directory.url.path + "/")
            #expect(values["PATH"] == "/usr/bin:/bin:/usr/sbin:/sbin")
            // A shell adds its own bookkeeping; caller secrets, proxy settings and core
            // path overrides must never survive the process environment replacement.
            #expect(Set(values.keys).isSubset(of: ["HOME", "TMPDIR", "PATH", "PWD", "SHLVL", "_"]))
            try FileManager.default.removeItem(at: output)
            #expect(try runtime.directory.removeAfterCoreExit())
        } catch {
            if await child.stop() { try? FileManager.default.removeItem(at: root) }
            throw error
        }
        #expect(await child.stop())
        try FileManager.default.removeItem(at: root)
    }

    @Test func timedOutOrCancelledCheckerRetainsItsProcessAndLeaseUntilStopped() async throws {
        let root = temporaryRoot()
        let runtime = try CoreRuntime.prepare(provider: provider, selectedNodeName: "Fixture", enableTun: false, root: root)
        let script = root.appendingPathComponent("checker.sh")
        try "#!/bin/sh\nexec /bin/sleep 30\n".write(to: script, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o700], ofItemAtPath: script.path)
        let child = try CoreProcess(executable: script, runtime: runtime, mode: .checkConfiguration, environment: .isolated, onExit: {})
        do {
            await #expect(throws: (any Error).self) { try await child.waitForExit(timeout: .milliseconds(50)) }
            #expect(child.isRunning)
            let waiting = Task { try await child.waitForExit(timeout: .seconds(5)) }
            waiting.cancel()
            await #expect(throws: CancellationError.self) { try await waiting.value }
            #expect(child.isRunning)
            #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
            #expect(await child.stop())
            #expect(try runtime.directory.removeAfterCoreExit())
        } catch {
            if await child.stop() { try? FileManager.default.removeItem(at: root) }
            throw error
        }
        try FileManager.default.removeItem(at: root)
    }

    @Test func absentSelectionIsRejectedBeforeCreatingRuntimeFiles() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        #expect(throws: (any Error).self) {
            try CoreRuntime.prepare(provider: provider, selectedNodeName: "not-in-provider", enableTun: false, root: root)
        }
        #expect(!FileManager.default.fileExists(atPath: root.path))
    }

    private var provider: String { "proxies: [{name: Fixture, type: socks5, server: 127.0.0.1, port: 9}]" }
    private func temporaryRoot() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("WeaveCoreProcess-\(UUID())")
    }
    private var coreURL: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
    }
}
