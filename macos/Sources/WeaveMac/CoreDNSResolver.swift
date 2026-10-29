import Foundation

struct CoreDNSResolver: Sendable {
    let controllerPort: UInt16
    let secret: String
    let processID: Int32

    func resolve(_ host: String) async throws -> [String] {
        try Task.checkCancellation()
        let owned = await Task.detached(priority: .utility) {
            MihomoController.listenerBelongsToProcess(port: controllerPort, pid: processID)
        }.value
        guard owned else { throw WeaveMacError.message("本会话核心已停止，无法完成订阅 DNS 查询") }
        async let ipv4 = query(host, type: "A")
        async let ipv6 = query(host, type: "AAAA")
        let results = await [ipv4, ipv6]
        try Task.checkCancellation()
        let addresses = results.flatMap { (try? $0.get()) ?? [] }
        guard !addresses.isEmpty else { throw WeaveMacError.message("核心 DNS 没有返回可用地址，请检查网络或节点") }
        return Array(Set(addresses)).sorted()
    }

    private struct Reply: Decodable {
        let Status: Int
        let Answer: [AnswerRecord]?
    }
    private struct AnswerRecord: Decodable {
        let type: Int
        let data: String
    }

    private func query(_ host: String, type: String) async -> Result<[String], Error> {
        do {
            let configuration = URLSessionConfiguration.ephemeral
            configuration.connectionProxyDictionary = ["HTTPEnable": 0, "HTTPSEnable": 0, "SOCKSEnable": 0]
            configuration.timeoutIntervalForRequest = 5
            configuration.timeoutIntervalForResource = 5
            configuration.urlCache = nil
            configuration.httpCookieStorage = nil
            let session = URLSession(configuration: configuration, delegate: NoRedirects(), delegateQueue: nil)
            defer { session.invalidateAndCancel() }
            var components = URLComponents(string: "http://127.0.0.1:\(controllerPort)/dns/query")!
            components.queryItems = [URLQueryItem(name: "name", value: host), URLQueryItem(name: "type", value: type)]
            var request = URLRequest(url: components.url!)
            request.setValue("Bearer \(secret)", forHTTPHeaderField: "Authorization")
            let (stream, response) = try await session.bytes(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                throw WeaveMacError.message("核心 DNS 查询失败")
            }
            var data = Data()
            for try await byte in stream {
                guard data.count < 65_536 else { throw WeaveMacError.message("核心 DNS 响应超过限制") }
                data.append(byte)
            }
            let reply = try JSONDecoder().decode(Reply.self, from: data)
            guard reply.Status == 0 else { throw WeaveMacError.message("核心 DNS 查询未成功") }
            return .success((reply.Answer ?? []).filter { $0.type == (type == "A" ? 1 : 28) }.map(\.data))
        } catch { return .failure(error) }
    }

    private final class NoRedirects: NSObject, URLSessionTaskDelegate, Sendable {
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                        newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
            completionHandler(nil)
        }
    }
}
