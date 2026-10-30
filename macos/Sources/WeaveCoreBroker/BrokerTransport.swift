import Foundation

@objc(WeaveCoreBrokerRPC) public protocol BrokerRPC {
    func exchange(_ frame: Data, reply: @escaping @Sendable (Data) -> Void)
}

public enum BrokerTransportError: Error, Sendable {
    case disconnected, timedOut, busy, invalidReply
}

public final class BrokerClient: @unchecked Sendable {
    private let connection: NSXPCConnection
    private let queue = DispatchQueue(label: "io.weave.broker-client")
    private let timeout: TimeInterval
    private let onDisconnect: @Sendable () -> Void
    private var closed = false
    private var authenticated = false
    private var authenticating = false
    private var pending: [UUID: Call] = [:]
    private var pendingOrder: [UUID] = []

    public convenience init(onDisconnect: @escaping @Sendable () -> Void = {}) throws {
        let requirement = try BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.serviceName)
        try self.init(connection: NSXPCConnection(machServiceName: BrokerIdentity.serviceName, options: .privileged), requirement: requirement, onDisconnect: onDisconnect)
    }

    init(connection: NSXPCConnection, requirement: String, timeout: TimeInterval = 30, onDisconnect: @escaping @Sendable () -> Void = {}) throws {
        try BrokerIdentity.validate(requirement)
        self.connection = connection
        self.timeout = timeout
        self.onDisconnect = onDisconnect
        connection.remoteObjectInterface = NSXPCInterface(with: BrokerRPC.self)
        connection.setCodeSigningRequirement(requirement)
        connection.interruptionHandler = { [weak self] in self?.close() }
        connection.invalidationHandler = { [weak self] in self?.close() }
        connection.resume()
    }

    deinit { connection.invalidate() }

    public func send(_ request: BrokerRequest) async throws -> BrokerReply {
        try Task.checkCancellation()
        let frame = try BrokerMessage.encode(request)
        let call = Call(request, frame: frame)
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    guard !call.cancelled else { continuation.resume(throwing: CancellationError()); return }
                    guard !self.closed else { continuation.resume(throwing: BrokerTransportError.disconnected); return }
                    let occupied = self.pending.values.filter { $0.request.stopsCore == request.stopsCore }.count
                    guard occupied < (request.stopsCore ? 1 : 3) else { continuation.resume(throwing: BrokerTransportError.busy); return }
                    call.continuation = continuation
                    self.pending[call.id] = call
                    self.pendingOrder.append(call.id)
                    let id = call.id
                    let deadline = DispatchWorkItem { [weak self] in
                        guard let self, self.pending[id] != nil else { return }
                        // A timed-out request is still executing remotely. Close the session so
                        // the service can cancel it; never free a slot while work is unbounded.
                        self.failAll(BrokerTransportError.timedOut)
                    }
                    call.deadline = deadline
                    self.queue.asyncAfter(deadline: .now() + self.timeout, execute: deadline)
                    if self.authenticated { self.transmit(call) }
                    else { self.authenticate() }
                }
            }
        } onCancel: {
            self.queue.async {
                call.cancelled = true
                guard self.pending[call.id] != nil else { return }
                let continuation = call.continuation
                call.continuation = nil
                continuation?.resume(throwing: CancellationError())
                if request.startsCore { self.failAll(BrokerTransportError.disconnected) }
                else if !call.sent {
                    self.pending.removeValue(forKey: call.id)
                    self.pendingOrder.removeAll { $0 == call.id }
                    call.deadline?.cancel()
                    call.deadline = nil
                }
                // Cancelled queries keep their slot until their reply is drained. They must
                // not disconnect a healthy VPN when a subscription fetch is cancelled.
            }
        }
    }

    public func close() { queue.async { self.failAll(BrokerTransportError.disconnected) } }

    private func authenticate() {
        guard !authenticating, !closed else { return }
        authenticating = true
        guard let proxy = connection.remoteObjectProxyWithErrorHandler({ [weak self] _ in self?.close() }) as? BrokerRPC else {
            failAll(BrokerTransportError.disconnected)
            return
        }
        // Code-signing requirements validate received messages. Authenticate a harmless reply
        // before sending any provider credentials or DNS destinations to the remote process.
        proxy.exchange(try! BrokerMessage.encode(BrokerRequest.status)) { [weak self] data in
            guard let self else { return }
            self.queue.async {
                guard !self.closed else { return }
                guard let reply = try? BrokerMessage.decodeReply(data), reply.matches(.status) else {
                    self.failAll(BrokerTransportError.invalidReply)
                    return
                }
                self.authenticated = true
                for id in self.pendingOrder {
                    if let call = self.pending[id] { self.transmit(call) }
                }
            }
        }
    }

    private func transmit(_ call: Call) {
        guard !closed, !call.sent else { return }
        call.sent = true
        let id = call.id
        guard let proxy = connection.remoteObjectProxyWithErrorHandler({ [weak self] _ in self?.close() }) as? BrokerRPC else {
            failAll(BrokerTransportError.disconnected)
            return
        }
        proxy.exchange(call.frame) { [weak self] data in
            guard let self else { return }
            self.queue.async { self.complete(id, data: data) }
        }
    }

    private func complete(_ id: UUID, data: Data) {
        guard let call = pending[id] else { return }
        do {
            let reply = try BrokerMessage.decodeReply(data)
            guard reply.matches(call.request) else { throw BrokerTransportError.invalidReply }
            pending.removeValue(forKey: id)
            pendingOrder.removeAll { $0 == id }
            call.deadline?.cancel()
            call.deadline = nil
            let continuation = call.continuation
            call.continuation = nil
            continuation?.resume(returning: reply)
        } catch { failAll(BrokerTransportError.invalidReply) }
    }

    private func failAll(_ error: any Error) {
        guard !closed else { return }
        closed = true
        let calls = pending.values
        pending.removeAll()
        pendingOrder.removeAll()
        connection.invalidate()
        for call in calls {
            call.deadline?.cancel()
            call.deadline = nil
            call.continuation?.resume(throwing: error)
            call.continuation = nil
        }
        onDisconnect()
    }

    private final class Call: @unchecked Sendable {
        let id = UUID()
        let request: BrokerRequest
        let frame: Data
        var sent = false
        var cancelled = false
        var continuation: CheckedContinuation<BrokerReply, Error>?
        var deadline: DispatchWorkItem?
        init(_ request: BrokerRequest, frame: Data) { self.request = request; self.frame = frame }
    }
}

extension BrokerReply {
    fileprivate func matches(_ request: BrokerRequest) -> Bool {
        if case .failure = self { return true }
        return switch (request, self) {
        case (.start, .state(.running, _)), (.stop, .state(.stopped, nil)), (.status, .state), (.resolve, .addresses): true
        default: false
        }
    }
}

public struct BrokerPeer: Sendable {
    public let userID: uid_t
    public let sessionID: au_asid_t
    public let processID: pid_t
}

public protocol BrokerSession: Sendable {
    func handle(_ request: BrokerRequest) async -> BrokerReply
    /// Must cancel startup and retain resources until owned processes have actually exited.
    func close() async
}

public final class BrokerListener: NSObject, NSXPCListenerDelegate, @unchecked Sendable {
    private let listener: NSXPCListener
    private let requirement: String
    private let factory: @Sendable (BrokerPeer) -> any BrokerSession
    private let lock = NSLock()
    private var closed = false
    private var peers: [UUID: Endpoint] = [:]

    public convenience init(factory: @escaping @Sendable (BrokerPeer) -> any BrokerSession) throws {
        try self.init(listener: NSXPCListener(machServiceName: BrokerIdentity.serviceName),
                      requirement: BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.appIdentifier), factory: factory)
    }

    public init(listener: NSXPCListener, requirement: String, factory: @escaping @Sendable (BrokerPeer) -> any BrokerSession) throws {
        try BrokerIdentity.validate(requirement)
        self.listener = listener
        self.requirement = requirement
        self.factory = factory
        super.init()
        // XPC enforces this for every incoming message, avoiding PID-reuse/exec races.
        listener.setConnectionCodeSigningRequirement(requirement)
        listener.delegate = self
    }

    public func resume() { listener.resume() }

    public func listener(_ listener: NSXPCListener, shouldAcceptNewConnection connection: NSXPCConnection) -> Bool {
        lock.withLock {
            guard !closed, peers.count < 8, connection.effectiveUserIdentifier != 0, connection.effectiveUserIdentifier != uid_t.max, connection.processIdentifier > 0 else { return false }
            let id = UUID()
            let peer = BrokerPeer(userID: connection.effectiveUserIdentifier, sessionID: connection.auditSessionIdentifier,
                                  processID: connection.processIdentifier)
            let endpoint = Endpoint(connection: connection, session: factory(peer))
            peers[id] = endpoint
            connection.exportedInterface = NSXPCInterface(with: BrokerRPC.self)
            connection.exportedObject = endpoint
            connection.setCodeSigningRequirement(requirement)
            let close: @Sendable () -> Void = { [weak self, weak endpoint] in
                guard let endpoint else { return }
                Task {
                    await endpoint.close().value
                    self?.lock.withLock { _ = self?.peers.removeValue(forKey: id) }
                }
            }
            connection.invalidationHandler = close
            connection.interruptionHandler = close
            connection.resume()
            return true
        }
    }

    public func stop() async {
        let endpoints = lock.withLock {
            closed = true
            listener.invalidate()
            return Array(peers.values)
        }
        let closures = endpoints.map { $0.close() }
        for closure in closures { await closure.value }
        lock.withLock { peers.removeAll() }
    }

    private final class Endpoint: NSObject, BrokerRPC, @unchecked Sendable {
        private let connection: NSXPCConnection
        private let session: any BrokerSession
        private let lock = NSLock()
        private var closing: Task<Void, Never>?
        private var work: [UUID: (request: BrokerRequest, task: Task<Void, Never>)] = [:]

        init(connection: NSXPCConnection, session: any BrokerSession) {
            self.connection = connection
            self.session = session
        }

        func exchange(_ frame: Data, reply: @escaping @Sendable (Data) -> Void) {
            guard let request = try? BrokerMessage.decodeRequest(frame) else {
                reply(Self.failure(.invalidRequest))
                return
            }
            lock.withLock {
                guard closing == nil else { reply(Self.failure(.unavailable)); return }
                let count = work.values.filter { $0.request.stopsCore == request.stopsCore }.count
                guard count < (request.stopsCore ? 1 : 3) else { reply(Self.failure(.busy)); return }
                let previousStarts = request.stopsCore ? work.values.filter { $0.request.startsCore }.map(\.task) : []
                for task in previousStarts { task.cancel() }
                let id = UUID()
                let task = Task {
                    let result = Task.isCancelled ? BrokerReply.failure(.unavailable) : await session.handle(request)
                    // A stop reply cannot overtake an earlier startup's completion/cleanup.
                    for previous in previousStarts { await previous.value }
                    let response = result.matches(request) ? (try? BrokerMessage.encode(result)) : nil
                    lock.withLock { _ = work.removeValue(forKey: id) }
                    reply(response ?? Self.failure(.unavailable))
                }
                work[id] = (request, task)
            }
        }

        func close() -> Task<Void, Never> {
            lock.withLock {
                if let closing { return closing }
                connection.invalidate()
                let tasks = work.values.map(\.task)
                for task in tasks { task.cancel() }
                let cleanup = Task {
                    await session.close()
                    for task in tasks { await task.value }
                }
                closing = cleanup
                return cleanup
            }
        }

        private static func failure(_ value: BrokerFailure) -> Data {
            // All enum values have a fixed, bounded representation.
            try! BrokerMessage.encode(BrokerReply.failure(value))
        }
    }
}
