import Foundation

public enum SubscriptionImporter {
    public static let maxPayloadBytes = 5 * 1024 * 1024

    public static func fetchHTTPS(_ rawValue: String) async throws -> ImportedSubscriptionPayload {
        let url = try validateRemoteURL(rawValue)
        let result = try await SubscriptionFetchOperation().fetch(url)
        let payload = try normalizeForMihomo(result.payload)
        return ImportedSubscriptionPayload(
            suggestedName: result.url.host ?? "远程订阅",
            source: result.url.absoluteString,
            payload: payload
        )
    }

    public static func importData(
        _ data: Data,
        suggestedName: String,
        source: String = "file://local-import"
    ) throws -> ImportedSubscriptionPayload {
        guard data.count <= maxPayloadBytes else {
            throw WeaveError.message("订阅内容超过 5 MiB 限制")
        }
        guard let text = String(data: data, encoding: .utf8), Data(text.utf8) == data else {
            throw WeaveError.message("订阅内容不是严格 UTF-8 文本")
        }
        return ImportedSubscriptionPayload(
            suggestedName: String(suggestedName.prefix(80)),
            source: source,
            payload: try normalizeForMihomo(text)
        )
    }

    public static func inlinePayload(from rawValue: String) throws -> ImportedSubscriptionPayload {
        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Data(value.utf8).count <= maxPayloadBytes else {
            throw WeaveError.message("订阅内容超过 5 MiB 限制")
        }
        return ImportedSubscriptionPayload(
            suggestedName: "导入订阅",
            source: "inline://local-import",
            payload: try normalizeForMihomo(value)
        )
    }

    public static func validateRemoteURL(_ rawValue: String) throws -> URL {
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
            throw WeaveError.message("订阅链接必须是无内嵌凭据的公网 HTTPS 地址")
        }
        return url
    }

    public static func normalizeForMihomo(_ rawValue: String) throws -> String {
        var value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("\u{FEFF}") { value.removeFirst() }
        guard !value.isEmpty else { throw WeaveError.message("订阅内容为空") }
        let lowered = value.prefix(512).lowercased()
        guard !lowered.contains("<html"), !lowered.contains("<!doctype html") else {
            throw WeaveError.message("订阅地址返回的是网页，不是节点配置")
        }
        if value.range(of: #"(?m)^\s*proxies\s*:"#, options: .regularExpression) != nil {
            guard !ClashNodeParser.parse(value).isEmpty else {
                throw WeaveError.message("Clash 配置中没有识别到有效节点")
            }
            return value
        }

        let uriText: String
        if value.components(separatedBy: .newlines).contains(where: isProxyURI) {
            uriText = value
        } else if let decoded = decodeBase64Text(value) {
            uriText = decoded
        } else {
            throw WeaveError.message("未识别到 Clash YAML、URI 或 Base64 节点列表")
        }

        let specs = try uriText.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") }
            .map(parseURI)
        guard !specs.isEmpty else { throw WeaveError.message("订阅中没有可导入的节点") }
        return render(specs)
    }

    private struct ProxySpec {
        var name: String
        var type: String
        var fields: [(String, YAMLValue)]
        var nested: [(String, [(String, YAMLValue)])] = []
    }

    private enum YAMLValue {
        case text(String)
        case raw(String)
    }

    private static func isProxyURI(_ line: String) -> Bool {
        guard let scheme = line.split(separator: ":", maxSplits: 1).first?.lowercased() else {
            return false
        }
        return [
            "vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "tuic",
            "socks", "socks5", "http", "https", "anytls",
        ].contains(scheme)
    }

    private static func parseURI(_ raw: String) throws -> ProxySpec {
        guard let scheme = raw.split(separator: ":", maxSplits: 1).first?.lowercased() else {
            throw WeaveError.message("节点链接缺少协议")
        }
        if scheme == "vmess" { return try parseVMess(raw) }
        if scheme == "ss" { return try parseShadowsocks(raw) }
        guard let components = URLComponents(string: raw),
              let host = components.host,
              !host.isEmpty,
              let port = components.port,
              (1...65_535).contains(port) else {
            throw WeaveError.message("\(scheme.uppercased()) 节点缺少有效地址或端口")
        }
        let query = Dictionary(
            components.queryItems?.compactMap { item in
                item.value.map { (item.name.lowercased(), $0) }
            } ?? [],
            uniquingKeysWith: { first, _ in first }
        )
        let name = components.fragment?.removingPercentEncoding?.nilIfBlank
            ?? "\(scheme.uppercased()) 节点"
        let user = components.user?.removingPercentEncoding ?? components.user ?? ""
        let password = components.password?.removingPercentEncoding ?? components.password ?? ""
        var spec: ProxySpec
        switch scheme {
        case "vless":
            guard !user.isEmpty else { throw WeaveError.message("\(name) 缺少 UUID") }
            spec = ProxySpec(name: name, type: "vless", fields: [
                ("server", .text(host)), ("port", .raw(String(port))),
                ("uuid", .text(user)), ("udp", .raw("true")),
            ])
            if let flow = query["flow"], !flow.isEmpty { spec.fields.append(("flow", .text(flow))) }
        case "trojan":
            guard !user.isEmpty else { throw WeaveError.message("\(name) 缺少密码") }
            spec = ProxySpec(name: name, type: "trojan", fields: [
                ("server", .text(host)), ("port", .raw(String(port))),
                ("password", .text(user)), ("udp", .raw("true")),
            ])
        case "hysteria2", "hy2":
            guard !user.isEmpty else { throw WeaveError.message("\(name) 缺少密码") }
            spec = ProxySpec(name: name, type: "hysteria2", fields: [
                ("server", .text(host)), ("port", .raw(String(port))),
                ("password", .text(user)),
            ])
            if let obfs = query["obfs"] { spec.fields.append(("obfs", .text(obfs))) }
            if let obfsPassword = query["obfs-password"] {
                spec.fields.append(("obfs-password", .text(obfsPassword)))
            }
        case "tuic":
            guard !user.isEmpty, !password.isEmpty else {
                throw WeaveError.message("\(name) 缺少 UUID 或密码")
            }
            spec = ProxySpec(name: name, type: "tuic", fields: [
                ("server", .text(host)), ("port", .raw(String(port))),
                ("uuid", .text(user)), ("password", .text(password)),
            ])
        case "socks", "socks5", "http", "https":
            spec = ProxySpec(
                name: name,
                type: scheme == "socks" || scheme == "socks5" ? "socks5" : "http",
                fields: [("server", .text(host)), ("port", .raw(String(port)))]
            )
            if !user.isEmpty { spec.fields.append(("username", .text(user))) }
            if !password.isEmpty { spec.fields.append(("password", .text(password))) }
            if scheme == "https" { spec.fields.append(("tls", .raw("true"))) }
        case "anytls":
            guard !user.isEmpty else { throw WeaveError.message("\(name) 缺少密码") }
            spec = ProxySpec(name: name, type: "anytls", fields: [
                ("server", .text(host)), ("port", .raw(String(port))),
                ("password", .text(user)),
            ])
        default:
            throw WeaveError.message("暂不支持将 \(scheme) URI 转换为 Mihomo")
        }
        addTLSAndTransport(to: &spec, query: query)
        return spec
    }

    private static func parseVMess(_ raw: String) throws -> ProxySpec {
        let body = String(raw.dropFirst("vmess://".count)).split(separator: "#", maxSplits: 1)[0]
        guard let data = decodeBase64Data(String(body)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let host = json["add"] as? String,
              !host.isEmpty,
              let port = intValue(json["port"]),
              let uuid = json["id"] as? String,
              !uuid.isEmpty else {
            throw WeaveError.message("VMess 节点参数不是有效 Base64 JSON")
        }
        let fragment = raw.split(separator: "#", maxSplits: 1).dropFirst().first
            .flatMap { String($0).removingPercentEncoding }
        var spec = ProxySpec(name: fragment?.nilIfBlank ?? (json["ps"] as? String)?.nilIfBlank ?? "VMess 节点", type: "vmess", fields: [
            ("server", .text(host)), ("port", .raw(String(port))),
            ("uuid", .text(uuid)), ("alterId", .raw(String(intValue(json["aid"]) ?? 0))),
            ("cipher", .text((json["scy"] as? String)?.nilIfBlank ?? "auto")),
            ("udp", .raw("true")),
        ])
        let network = (json["net"] as? String)?.lowercased()
        if let network, !network.isEmpty { spec.fields.append(("network", .text(network))) }
        if (json["tls"] as? String)?.lowercased() == "tls" {
            spec.fields.append(("tls", .raw("true")))
        }
        if let sni = (json["sni"] as? String)?.nilIfBlank {
            spec.fields.append(("servername", .text(sni)))
        }
        if network == "ws" {
            var values: [(String, YAMLValue)] = []
            if let path = (json["path"] as? String)?.nilIfBlank { values.append(("path", .text(path))) }
            if let hostHeader = (json["host"] as? String)?.nilIfBlank {
                values.append(("headers", .raw("{ Host: \(quoted(hostHeader)) }")))
            }
            if !values.isEmpty { spec.nested.append(("ws-opts", values)) }
        }
        return spec
    }

    private static func parseShadowsocks(_ raw: String) throws -> ProxySpec {
        let withoutFragment = String(raw.split(separator: "#", maxSplits: 1)[0])
        let name = raw.split(separator: "#", maxSplits: 1).dropFirst().first
            .flatMap { String($0).removingPercentEncoding }?.nilIfBlank ?? "SS 节点"
        let body = String(
            String(withoutFragment.dropFirst("ss://".count))
                .split(separator: "?", maxSplits: 1)[0]
        )
        let decoded: String
        if body.contains("@") {
            let pieces = body.split(separator: "@", maxSplits: 1).map(String.init)
            let credentials = decodeBase64Text(pieces[0]) ?? pieces[0].removingPercentEncoding ?? pieces[0]
            decoded = credentials + "@" + pieces[1]
        } else {
            guard let value = decodeBase64Text(body) else {
                throw WeaveError.message("SS 节点参数不是有效 Base64")
            }
            decoded = value
        }
        let sides = decoded.split(separator: "@", maxSplits: 1).map(String.init)
        guard sides.count == 2 else { throw WeaveError.message("\(name) 缺少服务器地址") }
        let credential = sides[0].split(separator: ":", maxSplits: 1).map(String.init)
        let address = sides[1].split(separator: ":", maxSplits: 1).map(String.init)
        guard credential.count == 2, address.count == 2,
              let port = Int(address[1]), (1...65_535).contains(port) else {
            throw WeaveError.message("\(name) 的加密方式、密码或端口无效")
        }
        return ProxySpec(name: name, type: "ss", fields: [
            ("server", .text(address[0])), ("port", .raw(String(port))),
            ("cipher", .text(credential[0])), ("password", .text(credential[1])),
            ("udp", .raw("true")),
        ])
    }

    private static func addTLSAndTransport(
        to spec: inout ProxySpec,
        query: [String: String]
    ) {
        let security = query["security"]?.lowercased()
        if security == "tls" || security == "reality" {
            spec.fields.append(("tls", .raw("true")))
        }
        if let sni = query["sni"]?.nilIfBlank { spec.fields.append(("servername", .text(sni))) }
        if let fingerprint = query["fp"]?.nilIfBlank {
            spec.fields.append(("client-fingerprint", .text(fingerprint)))
        }
        if security == "reality" {
            var values: [(String, YAMLValue)] = []
            if let key = query["pbk"]?.nilIfBlank { values.append(("public-key", .text(key))) }
            if let id = query["sid"]?.nilIfBlank { values.append(("short-id", .text(id))) }
            if !values.isEmpty { spec.nested.append(("reality-opts", values)) }
        }
        let transport = query["type"]?.lowercased()
        if transport == "ws" {
            spec.fields.append(("network", .text("ws")))
            var values: [(String, YAMLValue)] = []
            if let path = query["path"]?.nilIfBlank { values.append(("path", .text(path))) }
            if let host = query["host"]?.nilIfBlank {
                values.append(("headers", .raw("{ Host: \(quoted(host)) }")))
            }
            if !values.isEmpty { spec.nested.append(("ws-opts", values)) }
        } else if transport == "grpc" {
            spec.fields.append(("network", .text("grpc")))
            let service = query["servicename"] ?? query["serviceName"]
            if let service = service?.nilIfBlank {
                spec.nested.append(("grpc-opts", [("grpc-service-name", .text(service))]))
            }
        }
        if let insecure = query["insecure"]?.lowercased(), insecure == "1" || insecure == "true" {
            spec.fields.append(("skip-cert-verify", .raw("true")))
        }
    }

    private static func render(_ specs: [ProxySpec]) -> String {
        var result = "proxies:\n"
        for spec in specs {
            result += "  - name: \(quoted(String(spec.name.prefix(160))))\n"
            result += "    type: \(spec.type)\n"
            for (key, value) in spec.fields {
                result += "    \(key): \(render(value))\n"
            }
            for (key, values) in spec.nested {
                result += "    \(key):\n"
                for (nestedKey, value) in values {
                    result += "      \(nestedKey): \(render(value))\n"
                }
            }
        }
        return result
    }

    private static func render(_ value: YAMLValue) -> String {
        switch value {
        case let .text(text): quoted(text)
        case let .raw(text): text
        }
    }

    private static func quoted(_ value: String) -> String {
        "'" + value.replacingOccurrences(of: "'", with: "''") + "'"
    }

    private static func intValue(_ value: Any?) -> Int? {
        if let value = value as? Int { return value }
        if let value = value as? String { return Int(value) }
        return nil
    }

    private static func decodeBase64Text(_ value: String) -> String? {
        guard let data = decodeBase64Data(value),
              let text = String(data: data, encoding: .utf8), Data(text.utf8) == data else {
            return nil
        }
        return text
    }

    private static func decodeBase64Data(_ value: String) -> Data? {
        let compact = value.filter { !$0.isWhitespace }
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padded = compact + String(repeating: "=", count: (4 - compact.count % 4) % 4)
        return Data(base64Encoded: padded)
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
                request.setValue("Weave-iOS/0.1 (ClashMeta compatible)", forHTTPHeaderField: "User-Agent")
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
                throw WeaveError.message("订阅重定向次数过多")
            }
            _ = try SubscriptionImporter.validateRemoteURL(url.absoluteString)
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
            guard let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode),
                  let url = http.url else {
                throw WeaveError.message("订阅服务器没有返回成功响应")
            }
            _ = try SubscriptionImporter.validateRemoteURL(url.absoluteString)
            if http.expectedContentLength > Int64(SubscriptionImporter.maxPayloadBytes) {
                throw WeaveError.message("订阅响应超过 5 MiB 限制")
            }
            finalURL = url
            completionHandler(.allow)
        } catch {
            completionHandler(.cancel)
            finish(.failure(error))
        }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard !finished else { return }
        guard received.count + data.count <= SubscriptionImporter.maxPayloadBytes else {
            dataTask.cancel()
            finish(.failure(WeaveError.message("订阅响应超过 5 MiB 限制")))
            return
        }
        received.append(data)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard !finished else { return }
        if let error { finish(.failure(error)); return }
        guard let finalURL,
              let payload = String(data: received, encoding: .utf8),
              Data(payload.utf8) == received else {
            finish(.failure(WeaveError.message("订阅响应不是严格 UTF-8 文本")))
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

private extension String {
    var nilIfBlank: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
