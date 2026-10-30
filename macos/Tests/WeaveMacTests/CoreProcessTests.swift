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
