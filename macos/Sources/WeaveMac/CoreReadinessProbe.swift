import CryptoKit
import Foundation
import Network

/// A startup-only loopback responder. Its key never enters the core configuration.
final class CoreReadinessProbe: @unchecked Sendable {
    private let key = SymmetricKey(size: .bits256)
    private let queue = DispatchQueue(label: "io.weave.core-readiness")
    private let listener: NWListener
    // All mutable state belongs to queue, including Network callbacks.
    private var starting: CheckedContinuation<Void, Error>?
    private var stopping = false
    private var listenerCancelled = false
    private var exchanges: [UUID: ReadinessExchange] = [:]
    private var stopped: [CheckedContinuation<Void, Never>] = []

    init(port: UInt16) throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        listener = try NWListener(using: parameters)
    }

    func start() async throws {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    self.starting = continuation
                    self.listener.stateUpdateHandler = { state in
                        switch state {
                        case .ready: self.finishStarting(.success(()))
                        case let .failed(error):
                            self.finishStarting(.failure(error))
                            self.cancelOnQueue()
                        case .cancelled:
                            self.listenerCancelled = true
                            self.listener.stateUpdateHandler = nil
                            self.listener.newConnectionHandler = nil
                            self.finishStarting(.failure(CancellationError()))
                            self.finishStopping()
                        default: break
                        }
                    }
                    self.listener.newConnectionHandler = { connection in
                        guard !self.stopping, self.exchanges.count < 8 else { connection.cancel(); return }
                        let id = UUID()
                        let key = self.key
                        let exchange = ReadinessExchange(connection: connection, reply: {
                            Data(HMAC<SHA256>.authenticationCode(for: $0, using: key))
                        })
                        self.exchanges[id] = exchange
                        Task {
                            _ = try? await exchange.run()
                            self.queue.async {
                                self.exchanges.removeValue(forKey: id)
                                self.finishStopping()
                            }
                        }
                    }
                    self.listener.start(queue: self.queue)
                    if self.stopping { self.listener.cancel() }
                    self.queue.asyncAfter(deadline: .now() + 2) {
                        guard self.starting != nil else { return }
                        self.finishStarting(.failure(WeaveMacError.message("本地就绪响应器未能启动")))
                        self.cancelOnQueue()
                    }
                }
            }
        } onCancel: { self.queue.async { self.cancelOnQueue() } }
    }

    func isForwarding(port: UInt16) async throws -> Bool {
        try Task.checkCancellation()
        let challenge = SymmetricKey(size: .bits256).withUnsafeBytes { Data($0) }
        let connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!, using: .tcp)
        do {
            let reply = try await ReadinessExchange(connection: connection, request: challenge).run()
            try Task.checkCancellation()
            return HMAC<SHA256>.isValidAuthenticationCode(reply, authenticating: challenge, using: key)
        } catch {
            try Task.checkCancellation()
            return false
        }
    }

    func stop() async {
        await withCheckedContinuation { continuation in
            queue.async {
                self.stopped.append(continuation)
                self.cancelOnQueue()
                self.finishStopping()
            }
        }
    }

    private func cancelOnQueue() {
        stopping = true
        listener.cancel()
        for exchange in exchanges.values { exchange.cancel() }
    }

    private func finishStarting(_ result: Result<Void, Error>) {
        let continuation = starting
        starting = nil
        continuation?.resume(with: result)
    }

    private func finishStopping() {
        guard listenerCancelled, exchanges.isEmpty else { return }
        let continuations = stopped
        stopped.removeAll()
        for continuation in continuations { continuation.resume() }
    }
}

/// Fixed 32-byte exchanges, a one-second deadline, and cancellation that waits for socket closure.
private final class ReadinessExchange: @unchecked Sendable {
    private let queue = DispatchQueue(label: "io.weave.readiness-exchange")
    private let connection: NWConnection
    private let request: Data?
    private let reply: (@Sendable (Data) -> Data)?
    private var continuation: CheckedContinuation<Data, Error>?
    private var result: Result<Data, Error>?
    private var started = false

    init(connection: NWConnection, request: Data? = nil, reply: (@Sendable (Data) -> Data)? = nil) {
        self.connection = connection
        self.request = request
        self.reply = reply
    }

    func run() async throws -> Data {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    if let result = self.result { continuation.resume(with: result); return }
                    self.continuation = continuation
                    self.connection.stateUpdateHandler = { state in
                        switch state {
                        case .ready:
                            if let request = self.request {
                                self.connection.send(content: request, completion: .contentProcessed { error in
                                    if let error { self.finish(.failure(error)) }
                                    else { self.receive() }
                                })
                            } else { self.receive() }
                        case let .failed(error): self.finish(.failure(error))
                        case .cancelled:
                            let continuation = self.continuation
                            self.continuation = nil
                            continuation?.resume(with: self.result ?? .failure(CancellationError()))
                        default: break
                        }
                    }
                    self.started = true
                    self.connection.start(queue: self.queue)
                    self.queue.asyncAfter(deadline: .now() + 1) {
                        self.finish(.failure(WeaveMacError.message("本地转发验证超时")))
                    }
                }
            }
        } onCancel: { self.cancel() }
    }

    func cancel() { queue.async { self.finish(.failure(CancellationError())) } }

    private func receive() {
        guard result == nil else { return }
        connection.receive(minimumIncompleteLength: 32, maximumLength: 32) { data, _, _, error in
            if let error { self.finish(.failure(error)); return }
            guard let data, data.count == 32 else {
                self.finish(.failure(WeaveMacError.message("本地转发验证响应不完整")))
                return
            }
            if let reply = self.reply {
                self.connection.send(content: reply(data), completion: .contentProcessed { error in
                    self.finish(error.map { .failure($0) } ?? .success(data))
                })
            } else { self.finish(.success(data)) }
        }
    }

    private func finish(_ result: Result<Data, Error>) {
        guard self.result == nil else { return }
        self.result = result
        connection.cancel()
        if !started {
            continuation?.resume(with: result)
            continuation = nil
        }
    }
}
