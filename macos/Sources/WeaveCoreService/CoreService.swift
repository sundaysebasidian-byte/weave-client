import Darwin
import Foundation
import WeaveCoreBroker
import WeaveCoreRuntime

protocol ServiceCoreChild: Sendable {
    var processIdentifier: Int32 { get }
    var isRunning: Bool { get }
    func waitForExit(timeout: Duration) async throws -> Int32
    func stop() async -> Bool
}
extension CoreProcess: ServiceCoreChild {}

typealias ServiceCoreFactory = @Sendable (MacRuntime, CoreProcess.Mode, @escaping @Sendable () -> Void) throws -> any ServiceCoreChild

/// Owns one core across all authenticated connections to this service instance.
/// Root construction remains closed until installation/path integrity and daemon-wide
/// process ownership are integrated. No executable/daemon is registered by this module.
package final class CoreService: Sendable {
    private let ownership = ServiceOwnership()
    private let root: URL
    private let enableTun: Bool
    private let factory: ServiceCoreFactory

    package convenience init(executable: URL, runtimeRoot: URL, enableTun: Bool) throws {
        try self.init(runtimeRoot: runtimeRoot, enableTun: enableTun) { runtime, mode, onExit in
            try CoreProcess(executable: executable, runtime: runtime, mode: mode, environment: .isolated, onExit: onExit)
        }
    }

    package convenience init(installation: ManagedCoreInstallation, enableTun: Bool) throws {
        try self.init(runtimeRoot: installation.runtimeRoot, enableTun: enableTun) { runtime, mode, onExit in
            var inherited = runtime
            inherited.additionalInheritedLeases.append(installation.lease)
            return try CoreProcess(executable: installation.coreURL, runtime: inherited, mode: mode,
                                   environment: .isolated, onExit: onExit)
        }
    }

    init(runtimeRoot: URL, enableTun: Bool, factory: @escaping ServiceCoreFactory) throws {
        guard geteuid() != 0 else { throw BrokerFailure.unauthorized }
        root = runtimeRoot
        self.enableTun = enableTun
        self.factory = factory
    }

    package func session(for peer: BrokerPeer) -> any BrokerSession {
        makeSession(for: peer)
    }

    func makeSession(for peer: BrokerPeer) -> CoreServiceSession {
        CoreServiceSession(peer: peer, ownership: ownership, root: root, enableTun: enableTun, factory: factory)
    }

    /// A failed shutdown keeps the owner reachable so the host can retry rather than
    /// deallocating a still-live child or permitting another connection to acquire it.
    package func shutdown() async -> Bool { await ownership.shutdown() }
}

private actor ServiceOwnership {
    private var closed = false
    private var owner: CoreServiceSession?

    func acquire(_ session: CoreServiceSession) throws {
        guard !closed else { throw BrokerFailure.unavailable }
        guard owner == nil else { throw BrokerFailure.busy }
        owner = session
    }

    func release(_ id: UUID) {
        if owner?.id == id { owner = nil }
    }

    func shutdown() async -> Bool {
        closed = true
        return await owner?.shutdown() ?? true
    }
}

actor CoreServiceSession: BrokerSession {
    nonisolated let id = UUID()
    private let peer: BrokerPeer
    private let ownership: ServiceOwnership
    private let root: URL
    private let enableTun: Bool
    private let factory: ServiceCoreFactory
    private var state: BrokerState = .stopped
    private var desiredState: BrokerState = .stopped
    private var closed = false
    private var ownsSlot = false
    private var machineLease: ServiceMachineLease?
    private var startup: (id: UUID, task: Task<BrokerReply, Never>)?
    private var shutdownTask: (id: UUID, task: Task<Bool, Never>)?
    private var launchID: UUID?
    private(set) var runtime: MacRuntime?
    private(set) var child: (any ServiceCoreChild)?

    fileprivate init(peer: BrokerPeer, ownership: ServiceOwnership, root: URL, enableTun: Bool, factory: @escaping ServiceCoreFactory) {
        self.peer = peer
        self.ownership = ownership
        self.root = root
        self.enableTun = enableTun
        self.factory = factory
    }

    func handle(_ request: BrokerRequest) async -> BrokerReply {
        guard !closed else { return .failure(.unavailable) }
        guard peer.userID != 0, peer.userID != uid_t.max, peer.processID > 0 else { return .failure(.unauthorized) }
        guard (try? BrokerMessage.encode(request)) != nil else { return .failure(.invalidRequest) }
        switch request {
        case let .start(provider, node):
            guard startup == nil, shutdownTask == nil, runtime == nil, child == nil else { return .failure(.busy) }
            guard !Task.isCancelled else { return .failure(.unavailable) }
            desiredState = .running
            state = .starting
            let operation = UUID()
            let task = Task { await start(provider: provider, node: node) }
            startup = (operation, task)
            let result = await withTaskCancellationHandler { await task.value } onCancel: { task.cancel() }
            if startup?.id == operation { startup = nil }
            return result
        case .stop:
            desiredState = .stopped
            return await stop() ? .state(.stopped, mixedPort: nil) : .failure(.stopFailed)
        case .status:
            if state == .running, child?.isRunning != true {
                desiredState = .failed
                _ = await stop()
            }
            return .state(state, mixedPort: state == .running ? runtime?.mixedPort : nil)
        case let .resolve(host):
            guard state == .running, let runtime, let child, child.isRunning, let operation = launchID else { return .failure(.unavailable) }
            do {
                let addresses = try await CoreDNSResolver(controllerPort: runtime.controllerPort, secret: runtime.secret,
                                                         processID: child.processIdentifier).resolve(host)
                guard !closed, state == .running, launchID == operation, child.isRunning else { return .failure(.unavailable) }
                let result = BrokerReply.addresses(addresses)
                guard (try? BrokerMessage.encode(result)) != nil else { return .failure(.dnsFailed) }
                return result
            } catch { return .failure(.dnsFailed) }
        }
    }

    func close() async { _ = await shutdown() }

    func shutdown() async -> Bool {
        closed = true
        desiredState = .stopped
        return await stop()
    }

    private func start(provider: String, node: String?) async -> BrokerReply {
        var failure = BrokerFailure.startFailed
        do {
            try Task.checkCancellation()
            try await ownership.acquire(self)
            ownsSlot = true
            let acquiredLease = try ServiceMachineLease(root: root)
            machineLease = acquiredLease
            try Task.checkCancellation()
            let root = root, enableTun = enableTun
            let prepared = await Task.detached {
                Result { try CoreRuntime.prepare(provider: provider, selectedNodeName: node, enableTun: enableTun, root: root) }
            }.value
            do {
                runtime = try prepared.get()
                runtime?.additionalInheritedLeases = [acquiredLease.handle]
            } catch { throw BrokerFailure.invalidProfile }
            try Task.checkCancellation()
            guard let runtime else { throw BrokerFailure.startFailed }
            let checker = try factory(runtime, .checkConfiguration, {})
            child = checker
            let result = try await checker.waitForExit(timeout: .seconds(10))
            guard !checker.isRunning else { throw BrokerFailure.startFailed }
            child = nil
            guard result == 0 else { throw BrokerFailure.invalidProfile }
            try Task.checkCancellation()
            let operation = UUID()
            launchID = operation
            let process = try factory(runtime, .run) { [weak self] in
                Task { await self?.childExited(operation) }
            }
            child = process
            try await CoreRuntime.waitUntilReady(runtime: runtime, pid: process.processIdentifier, requireTun: enableTun)
            try Task.checkCancellation()
            guard !closed, desiredState == .running, launchID == operation, process.isRunning else { throw BrokerFailure.startFailed }
            state = .running
            return .state(.running, mixedPort: runtime.mixedPort)
        } catch let error as BrokerFailure { failure = error }
        catch is CancellationError { failure = desiredState == .failed ? .startFailed : .unavailable }
        catch { failure = .startFailed }
        if desiredState == .running { desiredState = .failed }
        if !(await cleanUp()) { failure = .stopFailed }
        return .failure(failure)
    }

    private func childExited(_ operation: UUID) async {
        guard launchID == operation, desiredState == .running else { return }
        desiredState = .failed
        _ = await stop()
    }

    private func stop() async -> Bool {
        let operation: (id: UUID, task: Task<Bool, Never>)
        if let shutdownTask {
            operation = shutdownTask
        } else {
            state = .stopping
            startup?.task.cancel()
            let starting = startup?.task
            // Start cleans its own failed resources and never waits on this task.
            let task = Task {
                if let starting { _ = await starting.value }
                return await cleanUp()
            }
            operation = (UUID(), task)
            shutdownTask = operation
        }
        let result = await operation.task.value
        // Every waiter retires the completed operation before replying. A later
        // waiter must not clear a new stop that began after another waiter resumed.
        if shutdownTask?.id == operation.id { shutdownTask = nil }
        return result
    }

    private func cleanUp() async -> Bool {
        launchID = nil
        if let child {
            guard await child.stop(), !child.isRunning else { state = .failed; return false }
            self.child = nil
        }
        if let runtime {
            guard (try? runtime.directory.removeAfterCoreExit()) == true else { state = .failed; return false }
            self.runtime = nil
        }
        do { try machineLease?.close() } catch { state = .failed; return false }
        machineLease = nil
        if ownsSlot {
            await ownership.release(id)
            ownsSlot = false
        }
        state = desiredState == .running ? .failed : desiredState
        return true
    }
}
