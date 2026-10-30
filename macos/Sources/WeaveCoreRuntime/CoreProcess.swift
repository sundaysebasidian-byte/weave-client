import Darwin
import Foundation

/// Cancellation of a waiter never cancels shutdown. Reaping and signalling share a lock
/// so an exited child's PID cannot be reused between the ownership check and kill().
package final class CoreProcess: @unchecked Sendable {
    private enum State { case running, exited(Int32), ownershipLost }
    package let processIdentifier: Int32
    private let exitSource: any DispatchSourceProcess
    private let lock = NSLock()
    private var state = State.running
    private var shutdown: (id: UUID, task: Task<Bool, Never>)?

    /// Unknown ownership is deliberately not reported as confirmed exit.
    package var isRunning: Bool { lock.withLock { if case .exited = state { return false }; return true } }
    package enum Mode: Sendable { case run, checkConfiguration }
    package enum Environment: Sendable { case inherited, isolated }

    package init(executable: URL, runtime: MacRuntime, mode: Mode = .run,
                 environment: Environment = .inherited, onExit: @escaping @Sendable () -> Void) throws {
        let arguments = [executable.path] + (mode == .checkConfiguration ? ["-t"] : []) +
            ["-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path]
        let values = environment == .isolated
            ? ["PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "HOME": runtime.directory.url.path, "TMPDIR": runtime.directory.url.path + "/"]
            : ProcessInfo.processInfo.environment
        processIdentifier = try Self.spawn(arguments: arguments, environment: values, runtime: runtime)
        exitSource = DispatchSource.makeProcessSource(identifier: processIdentifier, eventMask: .exit, queue: .global(qos: .utility))
        exitSource.setEventHandler { [self] in
            reap()
            exitSource.cancel()
            exitSource.setEventHandler(handler: nil)
            onExit()
        }
        exitSource.activate()
    }

    private func reap() {
        lock.withLock {
            var status: Int32 = 0
            var waited: pid_t
            repeat { waited = waitpid(processIdentifier, &status, WNOHANG) } while waited == -1 && errno == EINTR
            if waited == processIdentifier, status & 0x7f != 0x7f {
                state = .exited(status & 0x7f == 0 ? (status >> 8) & 0xff : status & 0x7f)
            } else {
                // An unexpected reaper invalidates PID ownership. Never signal it again
                // or release its runtime based on an assumption that it exited.
                state = .ownershipLost
            }
        }
    }

    private static func spawn(arguments: [String], environment: [String: String], runtime: MacRuntime) throws -> pid_t {
        func check(_ result: Int32) throws {
            if result != 0 { throw NSError(domain: NSPOSIXErrorDomain, code: Int(result)) }
        }
        let strings = arguments + environment.map { "\($0.key)=\($0.value)" }
        guard !strings.contains(where: { $0.utf8.contains(0) }) else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(EINVAL)) }
        let copies = strings.map { strdup($0) }
        defer { copies.forEach { free($0) } }
        guard copies.allSatisfy({ $0 != nil }) else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(ENOMEM)) }
        var argv = Array(copies.prefix(arguments.count)) + [nil]
        var envp = Array(copies.dropFirst(arguments.count)) + [nil]
        var actions: posix_spawn_file_actions_t?
        try check(posix_spawn_file_actions_init(&actions))
        defer { posix_spawn_file_actions_destroy(&actions) }
        try check(posix_spawn_file_actions_adddup2(&actions, runtime.directory.lease.fileDescriptor, STDIN_FILENO))
        try check(posix_spawn_file_actions_addopen(&actions, STDOUT_FILENO, "/dev/null", O_WRONLY, 0))
        try check(posix_spawn_file_actions_addopen(&actions, STDERR_FILENO, "/dev/null", O_WRONLY, 0))
        var inherited: [Int32] = []
        defer { inherited.forEach { _ = Darwin.close($0) } }
        for lease in runtime.additionalInheritedLeases {
            let fd = fcntl(lease.fileDescriptor, F_DUPFD_CLOEXEC, 3)
            guard fd >= 0 else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno)) }
            inherited.append(fd)
            try check(posix_spawn_file_actions_addinherit_np(&actions, fd))
        }
        var attributes: posix_spawnattr_t?
        try check(posix_spawnattr_init(&attributes))
        defer { posix_spawnattr_destroy(&attributes) }
        var mask = sigset_t(), defaults = sigset_t()
        sigemptyset(&mask)
        sigemptyset(&defaults)
        for signal in [SIGTERM, SIGINT, SIGHUP, SIGPIPE] { sigaddset(&defaults, signal) }
        // Keep checker and core in the service job group so launchd can reap them
        // even when the host is killed before its asynchronous cleanup runs.
        try check(posix_spawnattr_setpgroup(&attributes, getpgrp()))
        try check(posix_spawnattr_setsigmask(&attributes, &mask))
        try check(posix_spawnattr_setsigdefault(&attributes, &defaults))
        try check(posix_spawnattr_setflags(&attributes, Int16(POSIX_SPAWN_CLOEXEC_DEFAULT | POSIX_SPAWN_SETSIGMASK | POSIX_SPAWN_SETSIGDEF | POSIX_SPAWN_SETPGROUP)))
        var pid: pid_t = 0
        try check(posix_spawn(&pid, arguments[0], &actions, &attributes, &argv, &envp))
        return pid
    }

    /// Timeout/cancellation does not transfer ownership; the caller must still stop the child.
    package func waitForExit(timeout: Duration) async throws -> Int32 {
        let deadline = ContinuousClock.now + timeout
        while true {
            try Task.checkCancellation()
            switch lock.withLock({ state }) {
            case .exited(let status): return status
            case .ownershipLost: throw WeaveMacError.message("无法确认核心进程归属")
            case .running: break
            }
            guard ContinuousClock.now < deadline else { throw WeaveMacError.message("核心配置检查超时") }
            try await Task.sleep(for: .milliseconds(25))
        }
    }

    private func signal(_ number: Int32) {
        lock.withLock { if case .running = state { _ = kill(processIdentifier, number) } }
    }

    package func stop() async -> Bool {
        let (id, task) = lock.withLock {
            if let shutdown { return shutdown }
            let next = (id: UUID(), task: Task.detached { [self] in
                signal(SIGTERM)
                for _ in 0..<30 where isRunning { try? await Task.sleep(for: .milliseconds(50)) }
                if isRunning { signal(SIGKILL) }
                for _ in 0..<20 where isRunning { try? await Task.sleep(for: .milliseconds(50)) }
                return !isRunning
            })
            shutdown = next
            return next
        }
        let exited = await task.value
        if !exited { lock.withLock { if shutdown?.id == id { shutdown = nil } } }
        return exited
    }
}
