import AppKit
import Darwin
import Foundation
import UniformTypeIdentifiers

struct ImportedSubscriptionPayload: Sendable {
    let suggestedName: String
    let source: String
    let payload: String
}

enum DirectSubscriptionImporter {
    static let maxPayloadBytes = 5 * 1024 * 1024

    static func fetchHTTPS(_ rawValue: String) async throws -> ImportedSubscriptionPayload {
        let url = try validateRemoteURL(rawValue)
        try validateResolvedHost(url)
        let result = try await SubscriptionFetchOperation().fetch(url)
        try Task.checkCancellation()
        return ImportedSubscriptionPayload(
            suggestedName: result.url.host ?? "远程订阅",
            source: url.absoluteString,
            payload: try inlinePayload(from: result.payload).payload
        )
    }

    @MainActor
    static func chooseClashFile() async throws -> ImportedSubscriptionPayload? {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.plainText, .data]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        panel.message = "选择 Clash / Mihomo YAML 订阅"
        guard panel.runModal() == .OK, let url = panel.url else { return nil }
        return try await Task.detached(priority: .userInitiated) {
            let values = try url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
            guard values.isRegularFile == true else {
                throw WeaveMacError.message("请选择普通配置文件")
            }
            guard let size = values.fileSize, size <= maxPayloadBytes else {
                throw WeaveMacError.message("订阅文件超过 5 MiB 限制")
            }
            let data = try Data(contentsOf: url, options: .mappedIfSafe)
            guard
                data.count <= maxPayloadBytes,
                let payload = String(data: data, encoding: .utf8),
                Data(payload.utf8) == data
            else {
                throw WeaveMacError.message("订阅文件不是严格 UTF-8 文本")
            }
            return ImportedSubscriptionPayload(
                suggestedName: url.deletingPathExtension().lastPathComponent,
                source: "file://local-import",
                payload: try inlinePayload(from: payload).payload
            )
        }.value
    }

    static func inlinePayload(from rawValue: String) throws -> ImportedSubscriptionPayload {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\u{FEFF}"))
        guard Data(value.utf8).count <= maxPayloadBytes else {
            throw WeaveMacError.message("订阅内容超过 5 MiB 限制")
        }
        let source: String
        let payload: String
        if let decoded = decodeBase64Text(value) {
            payload = try normalizedPayload(decoded)
            source = "qr://inline-base64"
        } else {
            payload = try normalizedPayload(value)
            source = "qr://inline-clash"
        }
        return ImportedSubscriptionPayload(suggestedName: "二维码订阅", source: source, payload: payload)
    }

    private static func normalizedPayload(_ value: String) throws -> String {
        let firstLine = value.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty && !$0.hasPrefix("#") } ?? ""
        if firstLine.range(of: #"^[A-Za-z][A-Za-z0-9+.-]*://"#, options: .regularExpression) != nil,
           let converted = try URIListConverter.convertIfSupported(value) {
            _ = try ClashProviderDocument(converted)
            return converted
        }
        return try ClashProviderSanitizer.sanitize(value)
    }

    private static func decodeBase64Text(_ value: String) -> String? {
        let compact = value.components(separatedBy: .whitespacesAndNewlines).joined()
            .replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        guard compact.count > 16 else { return nil }
        let padded = compact + String(repeating: "=", count: (4 - compact.count % 4) % 4)
        guard let data = Data(base64Encoded: padded), let text = String(data: data, encoding: .utf8) else { return nil }
        return text
    }

    static func validateRemoteURL(_ rawValue: String) throws -> URL {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            !trimmed.isEmpty,
            trimmed.utf8.count <= 8_192,
            let components = URLComponents(string: trimmed),
            components.scheme?.lowercased() == "https",
            components.user == nil,
            components.password == nil,
            components.fragment == nil,
            let host = components.host?.lowercased(),
            !host.isEmpty,
            host != "localhost",
            !host.hasSuffix(".localhost"),
            !host.hasSuffix(".local"),
            !PrivateIPv4.isAllowed(host),
            !host.contains(":"),
            let url = components.url
        else {
            throw WeaveMacError.message("订阅链接必须是无内嵌凭据的公网 HTTPS 地址")
        }
        return url
    }

    /// Resolve the host before URLSession follows it. A syntactically public hostname can still
    /// resolve to loopback, private, link-local, CGNAT or multicast space (including after a
    /// redirect), so fail closed before handing the request to the system networking stack.
    static func validateResolvedHost(_ url: URL) throws {
        guard let host = url.host, !host.isEmpty else {
            throw WeaveMacError.message("订阅地址没有可解析的主机名")
        }
        var hints = addrinfo()
        hints.ai_family = AF_UNSPEC
        hints.ai_socktype = Int32(SOCK_STREAM)
        hints.ai_protocol = Int32(IPPROTO_TCP)
        var result: UnsafeMutablePointer<addrinfo>?
        let status = getaddrinfo(host, nil, &hints, &result)
        guard status == 0, let first = result else {
            throw WeaveMacError.message("订阅主机无法解析")
        }
        defer { freeaddrinfo(first) }
        var found = false
        var cursor: UnsafeMutablePointer<addrinfo>? = first
        while let item = cursor {
            let info = item.pointee
            guard let address = info.ai_addr else {
                cursor = info.ai_next
                continue
            }
            var numeric = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let code = getnameinfo(
                address,
                info.ai_addrlen,
                &numeric,
                socklen_t(numeric.count),
                nil,
                0,
                NI_NUMERICHOST,
            )
            guard code == 0 else {
                cursor = info.ai_next
                continue
            }
            let value = String(decoding: numeric.prefix { $0 != 0 }.map(UInt8.init), as: UTF8.self)
            found = true
            if isUnsafeResolvedAddress(value, family: info.ai_family) {
                throw WeaveMacError.message("订阅主机解析到了本机或私有地址")
            }
            cursor = info.ai_next
        }
        guard found else {
            throw WeaveMacError.message("订阅主机没有可用地址")
        }
    }

    private static func isUnsafeResolvedAddress(_ value: String, family: Int32) -> Bool {
        if family == AF_INET {
            let parts = value.split(separator: ".").compactMap { UInt8($0) }
            guard parts.count == 4 else { return true }
            let a = parts[0]
            let b = parts[1]
            return a == 0 || a == 10 || a == 127 ||
                (a == 100 && (64...127).contains(b)) ||
                (a == 169 && b == 254) ||
                (a == 172 && (16...31).contains(b)) ||
                (a == 192 && b == 168) ||
                a >= 224
        }
        if family == AF_INET6 {
            let lower = value.lowercased()
            if lower == "::" || lower == "::1" || lower.hasPrefix("fe8") ||
                lower.hasPrefix("fe9") || lower.hasPrefix("fea") || lower.hasPrefix("feb") ||
                lower.hasPrefix("fc") || lower.hasPrefix("fd") || lower.hasPrefix("ff") {
                return true
            }
            if lower.hasPrefix("::ffff:") {
                let mapped = String(lower.dropFirst("::ffff:".count))
                return isUnsafeResolvedAddress(mapped, family: AF_INET)
            }
        }
        return false
    }
}

private enum URIListConverter {
    static func convertIfSupported(_ value: String) throws -> String? {
        let lines = value.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") }
        guard lines.contains(where: { $0.contains("://") }) else { return nil }
        let nodes = try lines.enumerated().map { index, raw -> String in
            func invalid() -> WeaveMacError {
                .message("第 \(index + 1) 个节点无效或包含暂不支持的参数，请提供完整 Clash YAML；没有导入任何节点")
            }
            let decodedURI = raw.removingPercentEncoding ?? raw
            guard !decodedURI.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else { throw invalid() }
            if raw.lowercased().hasPrefix("vmess://") {
                guard let converted = try vmess(raw, index: index) else { throw invalid() }
                return converted
            }
            guard let url = URL(string: raw), let scheme = url.scheme?.lowercased() else { throw invalid() }
            let name = decode(url.fragment) ?? "\(scheme.uppercased()) 节点 \(index + 1)"
            guard let host = url.host, let port = url.port, !host.isEmpty, (1...65_535).contains(port) else { throw invalid() }
            let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
            guard Set(items.map { $0.name.lowercased() }).count == items.count,
                  items.allSatisfy({ $0.value != nil }) else { throw invalid() }
            let query = Dictionary(uniqueKeysWithValues: items.map { ($0.name.lowercased(), $0.value!) })
            let transport: Set<String> = ["type", "network", "path", "host", "servicename", "service"]
            let tls: Set<String> = ["sni", "fp", "alpn", "allowinsecure", "insecure"]
            let allowed: Set<String>
            switch scheme {
            case "vless": allowed = transport.union(tls).union(["encryption", "security", "pbk", "sid", "flow"])
            case "trojan": allowed = transport.union(tls).union(["security"])
            case "ss", "socks", "socks5": allowed = []
            case "hy2", "hysteria2": allowed = ["sni", "insecure", "obfs", "obfs-password"]
            case "tuic": allowed = tls.union(["congestion_control", "udp_relay_mode"])
            default: throw invalid()
            }
            guard Set(query.keys).isSubset(of: allowed) else { throw invalid() }
            for flag in ["allowinsecure", "insecure"] {
                if let value = query[flag], !["0", "1", "true", "false"].contains(value) { throw invalid() }
            }
            if let first = query["allowinsecure"], let second = query["insecure"],
               ["1", "true"].contains(first) != ["1", "true"].contains(second) { throw invalid() }
            let user = decode(url.user) ?? ""
            let password = decode(url.password) ?? ""
            switch scheme {
            case "vless":
                guard UUID(uuidString: user) != nil else { throw invalid() }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: vless",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    uuid: '\(yaml(user))'",
                ]
                try appendTransport(to: &fields, query: query)
                if let encryption = query["encryption"], encryption != "none" { throw invalid() }
                if let security = query["security"], !["none", "tls", "reality"].contains(security) { throw invalid() }
                if query["security"] != "reality", query["pbk"] != nil || query["sid"] != nil { throw invalid() }
                if query["security"] != "tls" && query["security"] != "reality", !Set(query.keys).isDisjoint(with: tls) { throw invalid() }
                if let security = query["security"], security == "tls" || security == "reality" {
                    fields.append("    tls: true")
                    if let servername = query["sni"], !servername.isEmpty {
                        fields.append("    servername: '\(yaml(servername))'")
                    }
                    appendTLSOptions(to: &fields, query: query)
                    if security == "reality" {
                        guard let publicKey = query["pbk"], !publicKey.isEmpty else { throw invalid() }
                        fields.append("    reality-opts:")
                        fields.append("      public-key: '\(yaml(publicKey))'")
                        if let shortID = query["sid"] { fields.append("      short-id: '\(yaml(shortID))'") }
                    }
                }
                if let flow = query["flow"], !flow.isEmpty {
                    guard flow == "xtls-rprx-vision", ["tls", "reality"].contains(query["security"] ?? ""),
                          ["tcp", ""].contains(query["type"] ?? query["network"] ?? "") else { throw invalid() }
                    fields.append("    flow: '\(yaml(flow))'")
                }
                return fields.joined(separator: "\n")
            case "trojan":
                guard !user.isEmpty, password.isEmpty, query["security"] == nil || query["security"] == "tls" else { throw invalid() }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: trojan",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    password: '\(yaml(user))'",
                    "    tls: true",
                ]
                if let servername = query["sni"], !servername.isEmpty {
                    fields.append("    sni: '\(yaml(servername))'")
                }
                try appendTransport(to: &fields, query: query)
                appendTLSOptions(to: &fields, query: query)
                return fields.joined(separator: "\n")
            case "ss":
                let rawCredential = password.isEmpty ? user : "\(user):\(password)"
                let credential = password.isEmpty ? (decodeBase64URL(rawCredential) ?? rawCredential) : rawCredential
                guard let separator = credential.firstIndex(of: ":"),
                      !credential.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains) else { throw invalid() }
                let cipher = String(credential[..<separator])
                let secret = String(credential[credential.index(after: separator)...])
                guard !cipher.isEmpty, !secret.isEmpty, query["plugin"] == nil else { throw invalid() }
                return "  - name: '\(yaml(name))'\n    type: ss\n    server: '\(yaml(host))'\n    port: \(port)\n    cipher: '\(yaml(cipher))'\n    password: '\(yaml(secret))'"
            case "socks5", "socks":
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: socks5",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                ]
                if !user.isEmpty { fields.append("    username: '\(yaml(user))'") }
                if !password.isEmpty { fields.append("    password: '\(yaml(password))'") }
                return fields.joined(separator: "\n")
            case "hysteria2", "hy2":
                guard !user.isEmpty else { throw invalid() }
                let authentication = password.isEmpty ? user : user + ":" + password
                if query["obfs"] == nil && query["obfs-password"] != nil { throw invalid() }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: hysteria2",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    password: '\(yaml(authentication))'",
                ]
                if let sni = query["sni"], !sni.isEmpty { fields.append("    sni: '\(yaml(sni))'") }
                if query["insecure"] == "1" || query["insecure"] == "true" { fields.append("    skip-cert-verify: true") }
                if let obfs = query["obfs"] {
                    guard obfs == "salamander", let password = query["obfs-password"], !password.isEmpty else { throw invalid() }
                    fields.append("    obfs: salamander")
                    fields.append("    obfs-password: '\(yaml(password))'")
                }
                return fields.joined(separator: "\n")
            case "tuic":
                guard UUID(uuidString: user) != nil, !password.isEmpty else { throw invalid() }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: tuic",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    uuid: '\(yaml(user))'",
                    "    password: '\(yaml(password))'",
                ]
                if let sni = query["sni"], !sni.isEmpty { fields.append("    sni: '\(yaml(sni))'") }
                if let congestion = query["congestion_control"] { fields.append("    congestion-controller: '\(yaml(congestion))'") }
                if let mode = query["udp_relay_mode"] { fields.append("    udp-relay-mode: '\(yaml(mode))'") }
                appendTLSOptions(to: &fields, query: query)
                return fields.joined(separator: "\n")
            default:
                throw invalid()
            }
        }
        guard !nodes.isEmpty else { return nil }
        return "proxies:\n" + nodes.joined(separator: "\n") + "\n"
    }

    private static func appendTransport(to fields: inout [String], query: [String: String]) throws {
        let network = query["type"] ?? query["network"] ?? "tcp"
        if let type = query["type"], let alias = query["network"], type != alias {
            throw WeaveMacError.message("URI 的传输类型相互冲突")
        }
        guard ["tcp", "ws", "grpc"].contains(network) else {
            throw WeaveMacError.message("暂不支持该 URI 传输方式，请使用保留完整参数的 Clash YAML")
        }
        let wsKeys: Set<String> = ["path", "host"]
        let grpcKeys: Set<String> = ["servicename", "service"]
        guard (network == "ws" || Set(query.keys).isDisjoint(with: wsKeys)),
              (network == "grpc" || Set(query.keys).isDisjoint(with: grpcKeys)) else {
            throw WeaveMacError.message("URI 参数与传输类型不匹配，请提供完整 Clash YAML")
        }
        fields.append("    network: '\(yaml(network))'")
        if network == "ws" {
            var ws = ["    ws-opts:"]
            if let path = query["path"], !path.isEmpty { ws.append("      path: '\(yaml(path))'") }
            if let host = query["host"], !host.isEmpty {
                ws.append("      headers:")
                ws.append("        Host: '\(yaml(host))'")
            }
            fields.append(contentsOf: ws)
        } else if network == "grpc", let service = query["servicename"] ?? query["service"], !service.isEmpty {
            fields.append("    grpc-opts:")
            fields.append("      grpc-service-name: '\(yaml(service))'")
        }
    }

    private static func appendTLSOptions(to fields: inout [String], query: [String: String]) {
        if let fingerprint = query["fp"], !fingerprint.isEmpty {
            fields.append("    client-fingerprint: '\(yaml(fingerprint))'")
        }
        if let alpn = query["alpn"], !alpn.isEmpty {
            fields.append("    alpn: [" + alpn.split(separator: ",").map { "'\(yaml(String($0)))'" }.joined(separator: ", ") + "]")
        }
        let allowInsecure = query["allowinsecure"] ?? ""
        let insecure = query["insecure"] ?? ""
        if allowInsecure == "1" || allowInsecure == "true" || insecure == "1" || insecure == "true" {
            fields.append("    skip-cert-verify: true")
        }
    }

    private static func vmess(_ raw: String, index: Int) throws -> String? {
        let encoded = String(raw.dropFirst("vmess://".count))
        guard let data = decodeBase64Data(encoded),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let host = object["add"] as? String,
              let uuid = object["id"] as? String,
              let port = int(object["port"]),
              !host.isEmpty, UUID(uuidString: uuid) != nil, port > 0, port <= 65_535 else { return nil }
        guard object.values.allSatisfy({ value in
            guard let text = value as? String else { return true }
            return !text.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains)
        }) else { return nil }
        let allowed: Set<String> = ["v", "ps", "add", "port", "id", "aid", "scy", "net", "type", "host", "path", "tls", "sni", "fp", "alpn", "allowInsecure", "serviceName"]
        guard Set(object.keys).isSubset(of: allowed),
              object["type"] == nil || ["", "none"].contains(object["type"] as? String ?? "invalid"),
              object.allSatisfy({ key, value in
                  value is String || (["v", "port", "aid", "allowInsecure"].contains(key) && value is NSNumber)
              }) else { return nil }
        let name = object["ps"] as? String ?? "VMESS 节点 \(index + 1)"
        var fields = [
            "  - name: '\(yaml(name))'",
            "    type: vmess",
            "    server: '\(yaml(host))'",
            "    port: \(port)",
            "    uuid: '\(yaml(uuid))'",
            "    cipher: '\(yaml(object["scy"] as? String ?? "auto"))'",
        ]
        if object["aid"] != nil {
            guard let alterID = int(object["aid"]), (0...65_535).contains(alterID) else { return nil }
            fields.append("    alterId: \(alterID)")
        }
        if let network = object["net"] as? String, !network.isEmpty {
            var query = ["type": network]
            if network == "grpc" {
                guard (object["host"] as? String ?? "").isEmpty else { return nil }
                if let service = object["serviceName"] as? String, let path = object["path"] as? String,
                   !service.isEmpty, !path.isEmpty, service != path { return nil }
                query["servicename"] = (object["serviceName"] ?? object["path"]) as? String
            } else if network == "ws" {
                guard (object["serviceName"] as? String ?? "").isEmpty else { return nil }
                if let path = object["path"] as? String, !path.isEmpty { query["path"] = path }
                if let hostHeader = object["host"] as? String, !hostHeader.isEmpty { query["host"] = hostHeader }
            } else if ["path", "host", "serviceName"].contains(where: { (object[$0] as? String)?.isEmpty == false }) {
                return nil
            }
            try appendTransport(to: &fields, query: query)
        } else if ["path", "host", "serviceName"].contains(where: { (object[$0] as? String)?.isEmpty == false }) {
            return nil
        }
        if let tls = object["tls"] as? String, !tls.isEmpty, tls != "none" {
            guard tls == "tls" else { return nil }
            fields.append("    tls: true")
            if let sni = object["sni"] as? String, !sni.isEmpty { fields.append("    servername: '\(yaml(sni))'") }
            var options = Dictionary(uniqueKeysWithValues: ["fp", "alpn", "allowInsecure"].compactMap { key in
                (object[key] as? String).map { (key.lowercased(), $0) }
            })
            if let flag = object["allowInsecure"] as? NSNumber {
                guard flag == 0 || flag == 1 else { return nil }
                options["allowinsecure"] = flag.boolValue ? "true" : "false"
            }
            if let flag = options["allowinsecure"], !["0", "1", "true", "false"].contains(flag) { return nil }
            appendTLSOptions(to: &fields, query: options)
        } else if ["sni", "fp", "alpn", "allowInsecure"].contains(where: { object[$0] != nil && (object[$0] as? String) != "" }) {
            return nil
        }
        return fields.joined(separator: "\n")
    }

    private static func int(_ value: Any?) -> Int? {
        if let value = value as? String { return Int(value) }
        if let value = value as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID() { return Int(value.stringValue) }
        return nil
    }

    private static func decode(_ value: String?) -> String? {
        value?.removingPercentEncoding ?? value
    }

    private static func decodeBase64URL(_ value: String) -> String? {
        guard let data = decodeBase64Data(value) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func decodeBase64Data(_ value: String) -> Data? {
        let compact = value.components(separatedBy: .whitespacesAndNewlines).joined()
        let padded = compact + String(repeating: "=", count: (4 - compact.count % 4) % 4)
        return Data(
            base64Encoded: padded
                .replacingOccurrences(of: "-", with: "+")
                .replacingOccurrences(of: "_", with: "/"),
        )
    }

    private static func yaml(_ value: String) -> String {
        value.replacingOccurrences(of: "'", with: "''")
    }
}

final class SubscriptionFetchOperation: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        return queue
    }()
    private let configuration: URLSessionConfiguration

    init(configuration: URLSessionConfiguration = .ephemeral) {
        self.configuration = configuration
        super.init()
    }

    private lazy var session: URLSession = {
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        return URLSession(configuration: configuration, delegate: self, delegateQueue: queue)
    }()

    private var continuation: CheckedContinuation<(url: URL, payload: String), Error>?
    private var received = Data()
    private var finalURL: URL?
    private var redirects = 0
    private var finished = false
    private var cancelled = false
    private var started = false

    func fetch(_ url: URL) async throws -> (url: URL, payload: String) {
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                queue.addOperation {
                    guard !self.started else {
                        continuation.resume(throwing: WeaveMacError.message("订阅请求不能重复使用"))
                        return
                    }
                    self.started = true
                    self.continuation = continuation
                    guard !self.cancelled else {
                        self.finish(.failure(CancellationError()))
                        return
                    }
                    var request = URLRequest(url: url)
                    request.httpMethod = "GET"
                    request.setValue(
                        "Weave/0.1 (ClashMetaForAndroid compatible)",
                        forHTTPHeaderField: "User-Agent"
                    )
                    request.setValue(
                        "application/yaml,text/yaml,text/plain;q=0.9,*/*;q=0.1",
                        forHTTPHeaderField: "Accept"
                    )
                    self.session.dataTask(with: request).resume()
                }
            }
        } onCancel: {
            self.queue.addOperation {
                self.cancelled = true
                self.finish(.failure(CancellationError()))
            }
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        do {
            redirects += 1
            guard redirects <= 5, let url = request.url else {
                throw WeaveMacError.message("订阅重定向次数过多")
            }
            _ = try DirectSubscriptionImporter.validateRemoteURL(url.absoluteString)
            try DirectSubscriptionImporter.validateResolvedHost(url)
            completionHandler(request)
        } catch {
            completionHandler(nil)
            finish(.failure(error))
        }
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        do {
            guard
                let http = response as? HTTPURLResponse,
                (200..<300).contains(http.statusCode),
                let url = http.url
            else {
                throw WeaveMacError.message("订阅服务器没有返回成功响应")
            }
            _ = try DirectSubscriptionImporter.validateRemoteURL(url.absoluteString)
            try DirectSubscriptionImporter.validateResolvedHost(url)
            if http.expectedContentLength > Int64(DirectSubscriptionImporter.maxPayloadBytes) {
                throw WeaveMacError.message("订阅响应超过 5 MiB 限制")
            }
            finalURL = url
            completionHandler(.allow)
        } catch {
            completionHandler(.cancel)
            finish(.failure(error))
        }
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive data: Data
    ) {
        guard !finished else { return }
        guard received.count + data.count <= DirectSubscriptionImporter.maxPayloadBytes else {
            dataTask.cancel()
            finish(.failure(WeaveMacError.message("订阅响应超过 5 MiB 限制")))
            return
        }
        received.append(data)
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        guard !finished else { return }
        if let error {
            finish(.failure(error))
            return
        }
        guard
            let finalURL,
            let payload = String(data: received, encoding: .utf8),
            Data(payload.utf8) == received
        else {
            finish(.failure(WeaveMacError.message("订阅响应不是严格 UTF-8 文本")))
            return
        }
        finish(.success((finalURL, payload)))
    }

    private func finish(_ result: Result<(url: URL, payload: String), Error>) {
        guard !finished, let continuation else { return }
        finished = true
        self.continuation = nil
        continuation.resume(with: result)
        if case .failure = result { session.invalidateAndCancel() }
        else { session.finishTasksAndInvalidate() }
    }
}
