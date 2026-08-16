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
        return ImportedSubscriptionPayload(
            suggestedName: result.url.host ?? "远程订阅",
            source: result.url.absoluteString,
            payload: result.payload
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
                payload: payload
            )
        }.value
    }

    static func inlinePayload(from rawValue: String) throws -> ImportedSubscriptionPayload {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Data(value.utf8).count <= maxPayloadBytes else {
            throw WeaveMacError.message("二维码内容超过 5 MiB 限制")
        }
        guard value.range(of: #"(?m)^\s*proxies\s*:"#, options: .regularExpression) != nil else {
            // QR codes commonly carry URI/Base64 subscriptions. Decode them locally and convert
            // only the supported URI forms to a minimal Clash provider.
            if let decoded = URIListConverter.convertIfSupported(value) ?? decodeBase64Clash(value) ?? decodeBase64URIList(value) {
                return ImportedSubscriptionPayload(
                    suggestedName: "二维码订阅",
                    source: "qr://inline-uri",
                    payload: decoded,
                )
            }
            throw WeaveMacError.message("二维码不是 HTTPS 链接、Weave 传输码或受支持的 Clash/URI 订阅")
        }
        return ImportedSubscriptionPayload(
            suggestedName: "二维码订阅",
            source: "qr://inline-clash",
            payload: value
        )
    }

    private static func decodeBase64URIList(_ value: String) -> String? {
        let compact = value.components(separatedBy: .whitespacesAndNewlines).joined()
        guard compact.count <= maxPayloadBytes * 2 else { return nil }
        let padded = compact + String(repeating: "=", count: (4 - compact.count % 4) % 4)
        guard let data = Data(base64Encoded: padded, options: [.ignoreUnknownCharacters]),
              let text = String(data: data, encoding: .utf8),
              text.split(whereSeparator: \.isNewline).contains(where: { line in
                  let lower = line.lowercased()
                  return lower.hasPrefix("ss://") || lower.hasPrefix("vless://") ||
                      lower.hasPrefix("vmess://") || lower.hasPrefix("trojan://") ||
                      lower.hasPrefix("hysteria") || lower.hasPrefix("tuic://") ||
                      lower.hasPrefix("socks5://")
              }) else { return nil }
        return URIListConverter.convertIfSupported(text)
    }

    private static func decodeBase64Clash(_ value: String) -> String? {
        let compact = value.components(separatedBy: .whitespacesAndNewlines).joined()
        guard compact.count > 16, compact.count <= maxPayloadBytes * 2 else { return nil }
        let padded = compact + String(repeating: "=", count: (4 - compact.count % 4) % 4)
        guard let data = Data(base64Encoded: padded, options: [.ignoreUnknownCharacters]),
              let text = String(data: data, encoding: .utf8),
              text.range(of: #"(?m)^\s*proxies\s*:"#, options: .regularExpression) != nil
        else { return nil }
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
    static func convertIfSupported(_ value: String) -> String? {
        let lines = value.split(whereSeparator: \.isNewline).map(String.init)
        let nodes = lines.enumerated().compactMap { index, raw -> String? in
            let raw = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !raw.isEmpty, !raw.hasPrefix("#") else { return nil }
            if raw.lowercased().hasPrefix("vmess://") {
                return vmess(raw, index: index)
            }
            guard let url = URL(string: raw), let scheme = url.scheme?.lowercased() else { return nil }
            let name = decode(url.fragment) ?? "\(scheme.uppercased()) 节点 \(index + 1)"
            guard let host = url.host, let port = url.port, !host.isEmpty else { return nil }
            let query = Dictionary(
                (URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? [])
                    .compactMap { item in item.value.map { (item.name.lowercased(), $0) } },
                uniquingKeysWith: { _, last in last },
            )
            let user = decode(url.user) ?? ""
            let password = decode(url.password) ?? ""
            switch scheme {
            case "vless":
                guard !user.isEmpty else { return nil }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: vless",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    uuid: '\(yaml(user))'",
                ]
                appendTransport(to: &fields, query: query)
                if let security = query["security"], security == "tls" || security == "reality" {
                    fields.append("    tls: true")
                    if let servername = query["sni"], !servername.isEmpty {
                        fields.append("    servername: '\(yaml(servername))'")
                    }
                }
                return fields.joined(separator: "\n")
            case "trojan":
                guard !user.isEmpty else { return nil }
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
                return fields.joined(separator: "\n")
            case "ss":
                let rawCredential = password.isEmpty ? user : "\(user):\(password)"
                guard let credential = decodeBase64URL(rawCredential) ?? Optional(rawCredential),
                      let separator = credential.firstIndex(of: ":") else { return nil }
                let cipher = String(credential[..<separator])
                let secret = String(credential[credential.index(after: separator)...])
                guard !cipher.isEmpty, !secret.isEmpty else { return nil }
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
                guard !user.isEmpty else { return nil }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: hysteria2",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    password: '\(yaml(user))'",
                ]
                if let sni = query["sni"], !sni.isEmpty { fields.append("    sni: '\(yaml(sni))'") }
                if query["insecure"] == "1" || query["insecure"] == "true" { fields.append("    skip-cert-verify: true") }
                return fields.joined(separator: "\n")
            case "tuic":
                guard !user.isEmpty, !password.isEmpty else { return nil }
                var fields = [
                    "  - name: '\(yaml(name))'",
                    "    type: tuic",
                    "    server: '\(yaml(host))'",
                    "    port: \(port)",
                    "    uuid: '\(yaml(user))'",
                    "    password: '\(yaml(password))'",
                ]
                if let sni = query["sni"], !sni.isEmpty { fields.append("    sni: '\(yaml(sni))'") }
                return fields.joined(separator: "\n")
            default:
                return nil
            }
        }
        guard !nodes.isEmpty else { return nil }
        return "proxies:\n" + nodes.joined(separator: "\n") + "\n"
    }

    private static func appendTransport(to fields: inout [String], query: [String: String]) {
        guard let network = query["type"] ?? query["network"], !network.isEmpty else { return }
        fields.append("    network: \(yaml(network))")
        if network == "ws" {
            var ws = ["    ws-opts:"]
            if let path = query["path"], !path.isEmpty { ws.append("      path: '\(yaml(path))'") }
            if let host = query["host"], !host.isEmpty {
                ws.append("      headers:")
                ws.append("        Host: '\(yaml(host))'")
            }
            fields.append(contentsOf: ws)
        } else if network == "grpc", let service = query["serviceName"] ?? query["service"], !service.isEmpty {
            fields.append("    grpc-opts:")
            fields.append("      grpc-service-name: '\(yaml(service))'")
        }
    }

    private static func vmess(_ raw: String, index: Int) -> String? {
        let encoded = String(raw.dropFirst("vmess://".count))
        guard let data = decodeBase64Data(encoded),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let host = object["add"] as? String,
              let uuid = object["id"] as? String,
              let port = int(object["port"]),
              !host.isEmpty, !uuid.isEmpty, port > 0, port <= 65_535 else { return nil }
        let name = (object["ps"] as? String).flatMap { decode($0) } ?? "VMESS 节点 \(index + 1)"
        var fields = [
            "  - name: '\(yaml(name))'",
            "    type: vmess",
            "    server: '\(yaml(host))'",
            "    port: \(port)",
            "    uuid: '\(yaml(uuid))'",
            "    cipher: auto",
        ]
        if let alterID = int(object["aid"]) { fields.append("    alterId: \(alterID)") }
        if let network = object["net"] as? String, !network.isEmpty {
            var query = ["type": network]
            if let path = object["path"] as? String { query["path"] = path }
            if let hostHeader = object["host"] as? String { query["host"] = hostHeader }
            appendTransport(to: &fields, query: query)
        }
        if let tls = object["tls"] as? String, !tls.isEmpty, tls != "none" {
            fields.append("    tls: true")
            if let sni = object["sni"] as? String, !sni.isEmpty { fields.append("    servername: '\(yaml(sni))'") }
        }
        return fields.joined(separator: "\n")
    }

    private static func int(_ value: Any?) -> Int? {
        if let value = value as? Int { return value }
        if let value = value as? NSNumber { return value.intValue }
        if let value = value as? String { return Int(value) }
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
            options: [.ignoreUnknownCharacters],
        )
    }

    private static func yaml(_ value: String) -> String {
        value.replacingOccurrences(of: "'", with: "''")
    }
}

private final class SubscriptionFetchOperation: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        return queue
    }()
    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
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

    func fetch(_ url: URL) async throws -> (url: URL, payload: String) {
        try await withCheckedThrowingContinuation { continuation in
            queue.addOperation {
                self.continuation = continuation
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
        session.finishTasksAndInvalidate()
    }
}
