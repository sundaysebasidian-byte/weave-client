import Darwin
import Foundation

/// The caller owns the runtime until stop confirms exit. Cancellation of a waiter never
/// cancels shutdown; concurrent waiters share the same termination and escalation.
package final class CoreProcess: @unchecked Sendable {
    private let process = Process()
    private let lock = NSLock()
    private var shutdown: (id: UUID, task: Task<Bool, Never>)?

    package var processIdentifier: Int32 { process.processIdentifier }
    package var isRunning: Bool { process.isRunning }

    package init(executable: URL, runtime: MacRuntime, onExit: @escaping @Sendable () -> Void) throws {
        process.executableURL = executable
        process.arguments = ["-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path]
        process.standardInput = runtime.directory.lease
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        process.terminationHandler = { _ in onExit() }
        try process.run()
    }

    package func stop() async -> Bool {
        let (id, task) = lock.withLock {
            if let shutdown { return shutdown }
            let process = process
            let next = (id: UUID(), task: Task.detached {
                guard process.isRunning else { return true }
                process.terminate()
                for _ in 0..<30 where process.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
                if process.isRunning { kill(process.processIdentifier, SIGKILL) }
                for _ in 0..<20 where process.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
                return !process.isRunning
            })
            shutdown = next
            return next
        }
        let exited = await task.value
        if !exited {
            lock.withLock { if shutdown?.id == id { shutdown = nil } }
        }
        return exited
    }
}
