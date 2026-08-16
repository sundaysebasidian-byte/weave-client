import Darwin
import Foundation
import Network

final class OneTimeTransferServer: @unchecked Sendable {
    private let packet: Data
    private let token: String
    private let queue = DispatchQueue(label: "io.weave.lan.export")
    private var listener: NWListener?
    private var expiryWork: DispatchWorkItem?
    private let lock = NSLock()
    private var consumed = false

    init(packet: Data, token: String) {
        self.packet = packet
        self.token = token
    }

    func start(expirySeconds: TimeInterval = 300) async throws -> UInt16 {
        guard let host = LocalAddress.privateIPv4() else {
            throw WeaveMacError.message("未找到可用的局域网 IPv4 地址")
        }
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(host),
            port: .any,
        )
        let listener = try NWListener(using: parameters, on: .any)
        listener.newConnectionLimit = 4
        listener.newConnectionHandler = { [weak self] connection in
            self?.handle(connection)
        }
        self.listener = listener
        let port: UInt16 = try await withCheckedThrowingContinuation { continuation in
            let startState = ListenerStartState(continuation: continuation)
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    guard let raw = listener.port?.rawValue else { return }
                    startState.ready(raw)
                case let .failed(error):
                    startState.fail(error)
                default:
                    break
                }
            }
            listener.start(queue: queue)
        }
        let expiry = DispatchWorkItem { [weak self] in self?.stop() }
        expiryWork = expiry
        queue.asyncAfter(deadline: .now() + expirySeconds, execute: expiry)
        return port
    }

    func stop() {
        lock.lock()
        defer { lock.unlock() }
        expiryWork?.cancel()
        expiryWork = nil
        listener?.cancel()
        listener = nil
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        receiveRequest(connection, buffer: Data())
    }

    private func receiveRequest(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8_192) {
            [weak self] data, _, isComplete, error in
            guard let self else { connection.cancel(); return }
            if error != nil {
                connection.cancel()
                return
            }
            var requestData = buffer
            if let data { requestData.append(data) }
            guard requestData.count <= 8_192 else { connection.cancel(); return }
            guard requestData.range(of: Data("\r\n\r\n".utf8)) != nil else {
                if isComplete {
                    connection.cancel()
                    return
                }
                self.receiveRequest(connection, buffer: requestData)
                return
            }
            let request = String(data: requestData, encoding: .utf8) ?? ""
            let requestLine = request.components(separatedBy: "\r\n").first ?? ""
            let expected = Set([
                "GET /v1/\(self.token) HTTP/1.0",
                "GET /v1/\(self.token) HTTP/1.1",
            ])
            self.lock.lock()
            let allowed = !self.consumed && expected.contains(requestLine)
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

private final class ListenerStartState: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<UInt16, Error>?

    init(continuation: CheckedContinuation<UInt16, Error>) {
        self.continuation = continuation
    }

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

enum LANTransferClient {
    static func fetch(_ link: TransferLink) async throws -> Data {
        guard let port = NWEndpoint.Port(rawValue: link.port) else {
            throw WeaveMacError.message("传输端口无效")
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
                case let .failed(error):
                    state.fail(error)
                default:
                    break
                }
            }
            connection.start(queue: DispatchQueue(label: "io.weave.lan.import"))
        }
    }

    private static func receive(_ connection: NWConnection, state: FetchState) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) {
            data, _, complete, error in
            if let data {
                guard state.append(data) else {
                    state.fail(WeaveMacError.message("传输内容超过限制"))
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

private final class FetchState: @unchecked Sendable {
    private let lock = NSLock()
    private var data = Data()
    private var continuation: CheckedContinuation<Data, Error>?

    init(continuation: CheckedContinuation<Data, Error>) {
        self.continuation = continuation
    }

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
        guard
            let separator = data.range(of: Data("\r\n\r\n".utf8)),
            let statusEnd = data.range(of: Data("\r\n".utf8)),
            let status = String(data: data[..<statusEnd.lowerBound], encoding: .utf8),
            status.hasPrefix("HTTP/1.1 200 ")
        else {
            continuation.resume(throwing: WeaveMacError.message("发送设备拒绝了传输或链接已失效"))
            return
        }
        let header = String(data: data[..<separator.lowerBound], encoding: .utf8) ?? ""
        let fields = Dictionary(uniqueKeysWithValues: header
            .components(separatedBy: "\r\n")
            .dropFirst()
            .compactMap { line -> (String, String)? in
                let parts = line.split(separator: ":", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { return nil }
                return (parts[0].trimmingCharacters(in: .whitespaces).lowercased(), parts[1].trimmingCharacters(in: .whitespaces))
            })
        guard fields["content-type"]?.lowercased() == "application/vnd.weave.transfer",
              let declared = fields["content-length"].flatMap(Int.init),
              declared > 0,
              declared <= TransferLimits.maxCiphertextBytes,
              data.count - separator.upperBound == declared
        else {
            continuation.resume(throwing: WeaveMacError.message("发送设备返回了无效传输响应"))
            return
        }
        let body = data[separator.upperBound...]
        continuation.resume(returning: Data(body))
    }

    func fail(_ error: Error) {
        lock.lock()
        defer { lock.unlock() }
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(throwing: error)
    }
}

enum LocalAddress {
    static func privateIPv4() -> String? {
        var pointer: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&pointer) == 0, let first = pointer else { return nil }
        defer { freeifaddrs(pointer) }
        var candidates: [(name: String, address: String)] = []
        for interface in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let flags = Int32(interface.pointee.ifa_flags)
            guard
                flags & IFF_UP != 0,
                flags & IFF_LOOPBACK == 0,
                flags & IFF_POINTOPOINT == 0
            else { continue }
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
            let rawName = interface.pointee.ifa_name!
            let name = String(cString: rawName)
            guard !name.hasPrefix("utun"),
                  !name.hasPrefix("awdl"),
                  !name.hasPrefix("llw"),
                  !name.hasPrefix("gif"),
                  !name.hasPrefix("stf") else { continue }
            candidates.append((name, value))
        }
        return candidates.sorted {
            interfacePriority($0.name) < interfacePriority($1.name)
        }.first?.address
    }

    private static func interfacePriority(_ name: String) -> Int {
        if name == "en0" { return 0 }
        if name.hasPrefix("en") { return 1 }
        if name.hasPrefix("bridge") { return 2 }
        return 3
    }
}
