import CryptoKit
import Foundation

public enum TransferLimits {
    public static let maxSubscriptions = 64
    public static let maxNameBytes = 320
    public static let maxSourceBytes = 8 * 1024
    public static let maxPayloadBytes = 5 * 1024 * 1024
    public static let maxPlaintextBytes = 20 * 1024 * 1024
    public static let maxCiphertextBytes = maxPlaintextBytes + 64
}

public struct TransferLink: Equatable, Sendable {
    public let host: String
    public let port: UInt16
    public let token: String
    public let key: Data

    public var confirmationCode: String {
        var input = Data(token.utf8)
        input.append(key)
        let digest = SHA256.hash(data: input)
        let bytes = Array(digest)
        let number = (Int(bytes[0]) << 16) | (Int(bytes[1]) << 8) | Int(bytes[2])
        return String(format: "%06d", number % 1_000_000)
    }

    public init(host: String, port: UInt16, token: String, key: Data) {
        self.host = host
        self.port = port
        self.token = token
        self.key = key
    }

    public var string: String {
        var components = URLComponents()
        components.scheme = "weave"
        components.host = "lan"
        components.path = "/v1/\(token)"
        components.queryItems = [
            URLQueryItem(name: "host", value: host),
            URLQueryItem(name: "port", value: String(port)),
        ]
        components.fragment = key.base64URLEncodedString()
        return components.string ?? ""
    }

    public static func parse(_ raw: String) throws -> TransferLink {
        guard
            raw.utf8.count <= 2_048,
            let components = URLComponents(
                string: raw.trimmingCharacters(in: .whitespacesAndNewlines)
            ),
            components.scheme?.lowercased() == "weave",
            components.host?.lowercased() == "lan"
        else {
            throw WeaveError.message("这不是有效的 Weave 局域网链接")
        }
        let pathParts = components.path.split(separator: "/")
        guard pathParts.count == 2, pathParts[0] == "v1" else {
            throw WeaveError.message("不支持的传输协议版本")
        }
        let token = String(pathParts[1])
        guard token.range(of: "^[0-9a-f]{32}$", options: .regularExpression) != nil else {
            throw WeaveError.message("传输 token 无效")
        }
        let items = components.queryItems ?? []
        guard
            let host = items.first(where: { $0.name == "host" })?.value,
            PrivateIPv4.isAllowed(host),
            let portText = items.first(where: { $0.name == "port" })?.value,
            let port = UInt16(portText),
            port > 0,
            let fragment = components.fragment,
            let key = Data(base64URLEncoded: fragment),
            key.count == 32
        else {
            throw WeaveError.message("局域网地址、端口或密钥无效")
        }
        return TransferLink(host: host, port: port, token: token, key: key)
    }
}

public enum PrivateIPv4 {
    public static func isAllowed(_ value: String) -> Bool {
        let parts = value.split(separator: ".", omittingEmptySubsequences: false)
        guard
            parts.count == 4,
            let a = UInt8(parts[0]),
            let b = UInt8(parts[1]),
            UInt8(parts[2]) != nil,
            UInt8(parts[3]) != nil
        else { return false }
        return a == 10 ||
            (a == 172 && (16...31).contains(b)) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}

public enum TransferCodec {
    private static let plaintextMagic = Data("WVLAN001".utf8)
    private static let encryptedMagic = Data("WVENC001".utf8)
    private static let aad = Data("weave-lan-transfer-v1".utf8)

    public static func encode(_ subscriptions: [TransferSubscription]) throws -> Data {
        guard !subscriptions.isEmpty, subscriptions.count <= TransferLimits.maxSubscriptions else {
            throw WeaveError.message("请选择 1–\(TransferLimits.maxSubscriptions) 个订阅")
        }
        var output = plaintextMagic
        output.appendUInt32(UInt32(subscriptions.count))
        for subscription in subscriptions {
            try output.appendString(subscription.name, maxBytes: TransferLimits.maxNameBytes)
            try output.appendString(subscription.source, maxBytes: TransferLimits.maxSourceBytes)
            try output.appendString(subscription.payload, maxBytes: TransferLimits.maxPayloadBytes)
            guard output.count <= TransferLimits.maxPlaintextBytes else {
                throw WeaveError.message("传输内容超过 20 MiB 限制")
            }
        }
        return output
    }

    public static func decode(_ data: Data) throws -> [TransferSubscription] {
        guard data.count <= TransferLimits.maxPlaintextBytes else {
            throw WeaveError.message("传输内容过大")
        }
        var reader = BinaryReader(data: data)
        guard try reader.read(count: 8) == plaintextMagic else {
            throw WeaveError.message("传输内容标识无效")
        }
        let count = Int(try reader.readUInt32())
        guard count > 0, count <= TransferLimits.maxSubscriptions else {
            throw WeaveError.message("订阅数量无效")
        }
        var result: [TransferSubscription] = []
        for _ in 0..<count {
            result.append(
                TransferSubscription(
                    name: try reader.readString(maxBytes: TransferLimits.maxNameBytes),
                    source: try reader.readString(maxBytes: TransferLimits.maxSourceBytes),
                    payload: try reader.readString(maxBytes: TransferLimits.maxPayloadBytes)
                )
            )
        }
        guard reader.isAtEnd else {
            throw WeaveError.message("传输内容包含多余数据")
        }
        return result
    }

    public static func seal(_ plaintext: Data, key: Data) throws -> Data {
        guard key.count == 32 else { throw WeaveError.message("传输密钥长度无效") }
        let nonce = AES.GCM.Nonce()
        let box = try AES.GCM.seal(
            plaintext,
            using: SymmetricKey(data: key),
            nonce: nonce,
            authenticating: aad
        )
        guard let combined = box.combined else {
            throw WeaveError.message("无法生成加密传输包")
        }
        return encryptedMagic + combined
    }

    public static func open(_ packet: Data, key: Data) throws -> Data {
        guard
            packet.count <= TransferLimits.maxCiphertextBytes,
            packet.count > 8,
            packet.prefix(8) == encryptedMagic,
            key.count == 32
        else {
            throw WeaveError.message("加密传输包无效")
        }
        let box = try AES.GCM.SealedBox(combined: packet.dropFirst(8))
        return try AES.GCM.open(
            box,
            using: SymmetricKey(data: key),
            authenticating: aad
        )
    }
}

private struct BinaryReader {
    let data: Data
    var offset = 0

    var isAtEnd: Bool { offset == data.count }

    mutating func read(count: Int) throws -> Data {
        guard count >= 0, offset <= data.count - count else {
            throw WeaveError.message("传输内容被截断")
        }
        defer { offset += count }
        return data.subdata(in: offset..<(offset + count))
    }

    mutating func readUInt32() throws -> UInt32 {
        let bytes = try read(count: 4)
        return bytes.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
    }

    mutating func readString(maxBytes: Int) throws -> String {
        let count = Int(try readUInt32())
        guard count <= maxBytes else { throw WeaveError.message("传输字段过大") }
        let bytes = try read(count: count)
        guard let string = String(data: bytes, encoding: .utf8) else {
            throw WeaveError.message("传输文本不是有效 UTF-8")
        }
        return string
    }
}

private extension Data {
    mutating func appendUInt32(_ value: UInt32) {
        append(UInt8((value >> 24) & 0xff))
        append(UInt8((value >> 16) & 0xff))
        append(UInt8((value >> 8) & 0xff))
        append(UInt8(value & 0xff))
    }

    mutating func appendString(_ value: String, maxBytes: Int) throws {
        let bytes = Data(value.utf8)
        guard bytes.count <= maxBytes else { throw WeaveError.message("传输字段过大") }
        appendUInt32(UInt32(bytes.count))
        append(bytes)
    }

    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    init?(base64URLEncoded value: String) {
        guard value.range(of: "^[A-Za-z0-9_-]+$", options: .regularExpression) != nil else {
            return nil
        }
        let padding = String(repeating: "=", count: (4 - value.count % 4) % 4)
        self.init(
            base64Encoded: value
                .replacingOccurrences(of: "-", with: "+")
                .replacingOccurrences(of: "_", with: "/") + padding
        )
    }
}
