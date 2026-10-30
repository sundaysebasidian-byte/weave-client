import WeaveCoreRuntime
import CWeaveHTTP
import Foundation

typealias SubscriptionHostResolver = @Sendable (String) async throws -> [String]

final class SubscriptionFetchOperation: @unchecked Sendable {
    private let request: OpaquePointer
    private let lock = NSLock()
    private let hostResolver: SubscriptionHostResolver?
    private var started = false

    init(options: WeaveHTTPOptions? = nil, hostResolver: SubscriptionHostResolver? = nil) throws {
        self.hostResolver = hostResolver
        let created: OpaquePointer?
        if var options { created = weave_http_create(&options) }
        else { created = weave_http_create(nil) }
        guard let created else { throw WeaveMacError.message("无法初始化 HTTPS 请求") }
        request = created
    }

    deinit { weave_http_destroy(request) }

    func fetch(_ url: URL) async throws -> (url: URL, payload: String) {
        guard lock.withLock({
            if started { return false }
            started = true
            return true
        }) else { throw WeaveMacError.message("订阅请求不能重复使用") }
        return try await withTaskCancellationHandler {
            do { return try await fetchValidated(url) }
            catch {
                try Task.checkCancellation()
                throw error
            }
        } onCancel: {
            weave_http_cancel(self.request)
        }
    }

    private struct Response: Sendable {
        let status: Int
        let redirect: String?
        let body: Data
    }

    private func perform(_ url: URL, timeout: Int, resolved: String?) async throws -> Response {
        try await withCheckedThrowingContinuation { continuation in
            // Blocking socket/TLS I/O must not occupy Swift's cooperative executor.
            DispatchQueue.global(qos: .userInitiated).async {
                continuation.resume(with: Result {
                    let result = url.absoluteString.withCString { urlPointer in
                        if let resolved {
                            return resolved.withCString { weave_http_perform(self.request, urlPointer, timeout, $0) }
                        }
                        return weave_http_perform(self.request, urlPointer, timeout, nil)
                    }
                    switch result {
                    case WEAVE_HTTP_OK: break
                    case WEAVE_HTTP_CANCELLED: throw CancellationError()
                    case WEAVE_HTTP_TOO_LARGE: throw WeaveMacError.message("订阅响应超过 5 MiB 或响应头超过安全限制")
                    case WEAVE_HTTP_PRIVATE_ADDRESS: throw WeaveMacError.message("订阅连接目标是本机、内网或保留地址，已阻止连接")
                    case WEAVE_HTTP_TLS_FAILED: throw WeaveMacError.message("订阅 HTTPS 证书或 TLS 连接校验失败")
                    case WEAVE_HTTP_TIMEOUT: throw WeaveMacError.message("订阅下载超时")
                    default: throw WeaveMacError.message("订阅 HTTPS 连接失败，请检查网络和服务器状态")
                    }
                    let size = weave_http_body_size(self.request)
                    return Response(status: weave_http_status(self.request),
                                    redirect: weave_http_redirect(self.request).map { String(cString: $0) },
                                    body: size == 0 ? Data() : Data(bytes: weave_http_body(self.request)!, count: size))
                })
            }
        }
    }

    private func fetchValidated(_ initialURL: URL) async throws -> (url: URL, payload: String) {
        let deadline = ContinuousClock.now.advanced(by: .seconds(30))
        var url = initialURL
        for redirect in 0...5 {
            if weave_http_cancelled(request) { throw CancellationError() }
            guard ContinuousClock.now < deadline else { throw WeaveMacError.message("订阅下载超时") }
            url = try DirectSubscriptionImporter.validateRemoteURL(url.absoluteString)
            var resolved: String?
            if let hostResolver, let host = url.host {
                let addresses = try await hostResolver(host)
                try Task.checkCancellation()
                guard addresses.count <= 32, !addresses.isEmpty,
                      addresses.allSatisfy({ $0.withCString { weave_http_is_public_ip($0) } }) else {
                    throw WeaveMacError.message("核心 DNS 未返回有效的公网地址，已阻止订阅连接")
                }
                resolved = "\(host):\(url.port ?? 443):" + addresses.map { $0.contains(":") ? "[\($0)]" : $0 }.joined(separator: ",")
            }
            let remaining = ContinuousClock.now.duration(to: deadline)
            guard remaining > .zero else { throw WeaveMacError.message("订阅下载超时") }
            let milliseconds = max(1, Int(remaining.components.seconds * 1_000 + remaining.components.attoseconds / 1_000_000_000_000_000))
            let response = try await perform(url, timeout: milliseconds, resolved: resolved)
            let status = response.status
            if [301, 302, 303, 307, 308].contains(status) {
                guard redirect < 5, let location = response.redirect else {
                    throw WeaveMacError.message("订阅重定向无效或次数过多")
                }
                url = try DirectSubscriptionImporter.validateRemoteURL(location)
                continue
            }
            guard (200..<300).contains(status) else {
                throw WeaveMacError.message("订阅服务器返回 HTTP \(status)")
            }
            let bytes = response.body
            guard let payload = String(data: bytes, encoding: .utf8), Data(payload.utf8) == bytes else {
                throw WeaveMacError.message("订阅响应不是严格 UTF-8 文本")
            }
            if weave_http_cancelled(request) { throw CancellationError() }
            return (url, payload)
        }
        throw WeaveMacError.message("订阅重定向次数过多")
    }
}
