import Darwin
import Foundation
@preconcurrency import Network
import Security

public struct TransferMaterial: Sendable {
    public let key: Data
    public let token: String
    public let packet: Data

    public static func make(items: [TransferSubscription]) throws -> TransferMaterial {
        var random = [UInt8](repeating: 0, count: 48)
        guard SecRandomCopyBytes(kSecRandomDefault, random.count, &random) == errSecSuccess else {
            throw WeaveError.message("无法生成一次性传输密钥")
        }
        let key = Data(random.prefix(32))
        let token = random.suffix(16).map { String(format: "%02x", $0) }.joined()
        let plaintext = try TransferCodec.encode(items)
        return TransferMaterial(key: key, token: token, packet: try TransferCodec.seal(plaintext, key: key))
    }
}

public final class OneTimeTransferServer: @unchecked Sendable {
    private let packet: Data
    private let token: String
    private let queue = DispatchQueue(label: "io.weave.ios.lan.export")
    private var listener: NWListener?
    private var expiryWork: DispatchWorkItem?
    private let lock = NSLock()
    private var consumed = false

    public init(packet: Data, token: String) {
        self.packet = packet
        self.token = token
    }

    public func start(expirySeconds: TimeInterval = 300) async throws -> UInt16 {
        let listener = try NWListener(using: .tcp, on: .any)
        listener.newConnectionLimit = 4
        listener.newConnectionHandler = { [weak self] connection in self?.handle(connection) }
        self.listener = listener
        let port: UInt16 = try await withCheckedThrowingContinuation { continuation in
            let startState = ListenerStartState(continuation: continuation)
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    guard let raw = listener.port?.rawValue else { return }
                    startState.ready(raw)
                case let .failed(error): startState.fail(error)
                default: break
                }
            }
            listener.start(queue: queue)
        }
        let expiry = DispatchWorkItem { [weak self] in self?.stop() }
        expiryWork = expiry
        queue.asyncAfter(deadline: .now() + expirySeconds, execute: expiry)
        return port
    }

    public func stop() {
        lock.lock()
        defer { lock.unlock() }
        expiryWork?.cancel()
        expiryWork = nil
        listener?.cancel()
        listener = nil
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8_192) {
            [weak self] data, _, _, _ in
            guard let self else { connection.cancel(); return }
            let request = data.flatMap { String(data: $0, encoding: .utf8) } ?? ""
            let expected = "GET /v1/\(self.token) HTTP/1."
            self.lock.lock()
            let allowed = !self.consumed && request.hasPrefix(expected)
            if allowed { self.consumed = true }
            self.lock.unlock()

            if allowed {
                let header = Data(
                    """
                    HTTP/1.1 200 OK\r
                    Content-Type: application/vnd.weave.transfer\r
                    Cache-Control: no-store\r
                    Content-Length: \(self.packet.count)\r
                    Connection: close\r
                    \r
                    """.utf8
                )
                connection.send(content: header + self.packet, completion: .contentProcessed { _ in
                    connection.cancel()
                    self.stop()
                })
            } else {
                let body = Data("Not Found".utf8)
                let response = Data(
                    "HTTP/1.1 404 Not Found\r\nContent-Length: \(body.count)\r\nConnection: close\r\n\r\n".utf8
                ) + body
                connection.send(content: response, completion: .contentProcessed { _ in
                    connection.cancel()
                })
            }
        }
    }
}

public enum LANTransferClient {
    public static func fetch(_ link: TransferLink) async throws -> Data {
        guard let port = NWEndpoint.Port(rawValue: link.port) else {
            throw WeaveError.message("传输端口无效")
        }
        let connection = NWConnection(host: NWEndpoint.Host(link.host), port: port, using: .tcp)
        return try await withCheckedThrowingContinuation { continuation in
            let state = FetchState(continuation: continuation)
            connection.stateUpdateHandler = { connectionState in
                switch connectionState {
                case .ready:
                    let request = Data(
                        "GET /v1/\(link.token) HTTP/1.1\r\nHost: \(link.host)\r\nConnection: close\r\n\r\n".utf8
                    )
                    connection.send(content: request, completion: .contentProcessed { error in
                        if let error { state.fail(error); connection.cancel(); return }
                        receive(connection, state: state)
                    })
                case let .failed(error): state.fail(error)
                default: break
                }
            }
            connection.start(queue: DispatchQueue(label: "io.weave.ios.lan.import"))
        }
    }

    private static func receive(_ connection: NWConnection, state: FetchState) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) {
            data, _, complete, error in
            if let data {
                guard state.append(data) else {
                    state.fail(WeaveError.message("传输内容超过限制"))
                    connection.cancel()
                    return
                }
            }
            if let error {
                state.fail(error)
                connection.cancel()
            } else if complete {
                state.finish()
                connection.cancel()
            } else {
                receive(connection, state: state)
            }
        }
    }
}

public enum LocalAddress {
    public static func privateIPv4() -> String? {
        var pointer: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&pointer) == 0, let first = pointer else { return nil }
        defer { freeifaddrs(pointer) }
        var candidates: [(name: String, address: String)] = []
        for interface in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let flags = Int32(interface.pointee.ifa_flags)
            guard flags & IFF_UP != 0,
                  flags & IFF_LOOPBACK == 0,
                  flags & IFF_POINTOPOINT == 0 else { continue }
            let address = interface.pointee.ifa_addr
            guard address?.pointee.sa_family == UInt8(AF_INET) else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let result = getnameinfo(
                address,
                socklen_t(address!.pointee.sa_len),
                &host,
                socklen_t(host.count),
                nil,
                0,
                NI_NUMERICHOST
            )
            guard result == 0 else { continue }
            let value = String(decoding: host.prefix { $0 != 0 }.map(UInt8.init), as: UTF8.self)
            guard PrivateIPv4.isAllowed(value), !value.hasPrefix("127.") else { continue }
            let name = String(cString: interface.pointee.ifa_name!)
            candidates.append((name, value))
        }
        return candidates.sorted { interfacePriority($0.name) < interfacePriority($1.name) }
            .first?.address
    }

    private static func interfacePriority(_ name: String) -> Int {
        if name == "en0" { return 0 }
        if name.hasPrefix("en") { return 1 }
        if name.hasPrefix("pdp_ip") { return 2 }
        if name.hasPrefix("bridge") { return 3 }
        return 4
    }
}

private final class ListenerStartState: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<UInt16, Error>?

    init(continuation: CheckedContinuation<UInt16, Error>) { self.continuation = continuation }

    func ready(_ port: UInt16) {
        lock.lock()
        defer { lock.unlock() }
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(returning: port)
    }

    func fail(_ error: Error) {
        lock.lock()
        defer { lock.unlock() }
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(throwing: error)
    }
}

private final class FetchState: @unchecked Sendable {
    private let lock = NSLock()
    private var data = Data()
    private var continuation: CheckedContinuation<Data, Error>?

    init(continuation: CheckedContinuation<Data, Error>) { self.continuation = continuation }

    func append(_ bytes: Data) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard data.count + bytes.count <= TransferLimits.maxCiphertextBytes + 4_096 else {
            return false
        }
        data.append(bytes)
        return true
    }

    func finish() {
        lock.lock()
        defer { lock.unlock() }
        guard let continuation else { return }
        self.continuation = nil
        guard let separator = data.range(of: Data("\r\n\r\n".utf8)),
              let statusEnd = data.range(of: Data("\r\n".utf8)),
              let status = String(data: data[..<statusEnd.lowerBound], encoding: .utf8),
              status.contains(" 200 ") else {
            continuation.resume(throwing: WeaveError.message("发送设备拒绝了传输或链接已失效"))
            return
        }
        continuation.resume(returning: Data(data[separator.upperBound...]))
    }

    func fail(_ error: Error) {
        lock.lock()
        defer { lock.unlock() }
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(throwing: error)
    }
}
