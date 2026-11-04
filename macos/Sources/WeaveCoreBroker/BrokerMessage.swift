import Darwin
import Foundation

public enum BrokerRequest: Equatable, Sendable {
    case start(provider: String, node: String?)
    case stop
    case status
    case resolve(host: String)

    var startsCore: Bool { if case .start = self { true } else { false } }
    var stopsCore: Bool { self == .stop }
}

public enum BrokerState: UInt8, Sendable {
    case stopped = 0, starting, running, stopping, failed
}

public enum BrokerFailure: UInt8, Error, Sendable {
    case invalidRequest = 1, busy, unavailable, unauthorized, invalidProfile, startFailed, stopFailed, dnsFailed
}

public enum BrokerReply: Equatable, Sendable {
    case state(BrokerState, mixedPort: UInt16?)
    case addresses([String])
    case failure(BrokerFailure)
}

/// Explicit bounded fields avoid polymorphic object decoding and ignored/duplicate JSON keys.
public enum BrokerMessage {
    public static let maxProviderBytes = 5 * 1024 * 1024
    public static let maxFrameBytes = maxProviderBytes + 1024
    private static let header = Data([0x57, 0x56, 0x42, 0x52, 1]) // WVBR, protocol version 1

    public static func encode(_ request: BrokerRequest) throws -> Data {
        var writer = Writer()
        switch request {
        case let .start(provider, node):
            writer.byte(1)
            try writer.string(provider, limit: maxProviderBytes)
            writer.byte(node == nil ? 0 : 1)
            if let node { try writer.string(node, limit: 320, plain: true) }
        case .stop: writer.byte(2)
        case .status: writer.byte(3)
        case let .resolve(host):
            guard validHost(host) else { throw BrokerFailure.invalidRequest }
            writer.byte(4)
            try writer.string(host, limit: 253, plain: true)
        }
        return writer.data
    }

    public static func decodeRequest(_ data: Data) throws -> BrokerRequest {
        var reader = try Reader(data)
        let result: BrokerRequest
        switch try reader.byte() {
        case 1:
            let provider = try reader.string(limit: maxProviderBytes)
            let flag = try reader.byte()
            guard flag <= 1 else { throw BrokerFailure.invalidRequest }
            result = .start(provider: provider, node: flag == 1 ? try reader.string(limit: 320, plain: true) : nil)
        case 2: result = .stop
        case 3: result = .status
        case 4:
            let host = try reader.string(limit: 253, plain: true)
            guard validHost(host) else { throw BrokerFailure.invalidRequest }
            result = .resolve(host: host)
        default: throw BrokerFailure.invalidRequest
        }
        try reader.finish()
        return result
    }

    public static func encode(_ reply: BrokerReply) throws -> Data {
        var writer = Writer()
        switch reply {
        case let .state(state, port):
            guard (state == .running && port != nil && port != 0) || (state != .running && port == nil) else { throw BrokerFailure.invalidRequest }
            writer.byte(0x81)
            writer.byte(state.rawValue)
            writer.uint32(UInt32(port ?? 0))
        case let .addresses(addresses):
            guard !addresses.isEmpty, addresses.count <= 64 else { throw BrokerFailure.invalidRequest }
            writer.byte(0x82)
            writer.byte(UInt8(addresses.count))
            for address in addresses {
                guard isAddress(address) else { throw BrokerFailure.invalidRequest }
                try writer.string(address, limit: 45, plain: true)
            }
        case let .failure(error):
            writer.byte(0x83)
            writer.byte(error.rawValue)
        }
        return writer.data
    }

    public static func decodeReply(_ data: Data) throws -> BrokerReply {
        guard data.count <= 4096 else { throw BrokerFailure.invalidRequest }
        var reader = try Reader(data)
        let result: BrokerReply
        switch try reader.byte() {
        case 0x81:
            guard let state = BrokerState(rawValue: try reader.byte()) else { throw BrokerFailure.invalidRequest }
            let port = try reader.uint32()
            guard port <= UInt16.max, (state == .running ? port != 0 : port == 0) else { throw BrokerFailure.invalidRequest }
            result = .state(state, mixedPort: port == 0 ? nil : UInt16(port))
        case 0x82:
            let count = Int(try reader.byte())
            guard count > 0, count <= 64 else { throw BrokerFailure.invalidRequest }
            var addresses: [String] = []
            for _ in 0..<count {
                let address = try reader.string(limit: 45, plain: true)
                guard isAddress(address) else { throw BrokerFailure.invalidRequest }
                addresses.append(address)
            }
            result = .addresses(addresses)
        case 0x83:
            guard let failure = BrokerFailure(rawValue: try reader.byte()) else { throw BrokerFailure.invalidRequest }
            result = .failure(failure)
        default: throw BrokerFailure.invalidRequest
        }
        try reader.finish()
        return result
    }

    private static func isAddress(_ value: String) -> Bool {
        guard !value.isEmpty, value.utf8.allSatisfy({ (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0) || $0 == 46 || $0 == 58 }) else { return false }
        var ipv4 = in_addr(), ipv6 = in6_addr()
        return value.withCString { inet_pton(AF_INET, $0, &ipv4) == 1 || inet_pton(AF_INET6, $0, &ipv6) == 1 }
    }

    private static func validHost(_ host: String) -> Bool {
        guard !host.isEmpty, host.utf8.count <= 253 else { return false }
        if isAddress(host) { return true }
        let name = host.hasSuffix(".") ? String(host.dropLast()) : host
        return name.split(separator: ".", omittingEmptySubsequences: false).allSatisfy { label in
            !label.isEmpty && label.utf8.count <= 63 && label.first != "-" && label.last != "-" &&
            label.utf8.allSatisfy { (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 45 }
        }
    }

    private static func check(_ string: String, limit: Int, plain: Bool) throws {
        guard !string.isEmpty, string.utf8.count <= limit, !string.contains("\0"),
              !plain || !string.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else {
            throw BrokerFailure.invalidRequest
        }
    }

    private struct Writer {
        var data = header
        mutating func byte(_ value: UInt8) { data.append(value) }
        mutating func uint32(_ value: UInt32) {
            for shift in stride(from: 0, to: 32, by: 8) { byte(UInt8(truncatingIfNeeded: value >> shift)) }
        }
        mutating func string(_ value: String, limit: Int, plain: Bool = false) throws {
            try check(value, limit: limit, plain: plain)
            uint32(UInt32(value.utf8.count))
            data.append(contentsOf: value.utf8)
        }
    }

    private struct Reader {
        let data: Data
        var offset = header.count
        init(_ data: Data) throws {
            guard data.count >= header.count + 1, data.count <= maxFrameBytes, data.prefix(header.count) == header else { throw BrokerFailure.invalidRequest }
            self.data = data
        }
        mutating func byte() throws -> UInt8 {
            guard offset < data.count else { throw BrokerFailure.invalidRequest }
            defer { offset += 1 }
            return data[data.startIndex + offset]
        }
        mutating func uint32() throws -> UInt32 {
            var result: UInt32 = 0
            for shift in stride(from: 0, to: 32, by: 8) { result |= UInt32(try byte()) << shift }
            return result
        }
        mutating func string(limit: Int, plain: Bool = false) throws -> String {
            let count = Int(try uint32())
            guard count <= limit, count <= data.count - offset else { throw BrokerFailure.invalidRequest }
            let start = data.startIndex + offset
            guard let string = String(data: data[start..<start + count], encoding: .utf8) else { throw BrokerFailure.invalidRequest }
            offset += count
            try check(string, limit: limit, plain: plain)
            return string
        }
        func finish() throws { guard offset == data.count else { throw BrokerFailure.invalidRequest } }
    }
}
