import AppKit
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
        guard value.range(
            of: #"(?m)^\s*proxies\s*:"#,
            options: .regularExpression
        ) != nil else {
            throw WeaveMacError.message("二维码不是 HTTPS 链接、Weave 传输码或 Clash YAML")
        }
        return ImportedSubscriptionPayload(
            suggestedName: "二维码订阅",
            source: "qr://inline-clash",
            payload: value
        )
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
