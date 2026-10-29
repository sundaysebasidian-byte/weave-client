import Foundation
import Testing
@testable import WeaveMac

@Suite("Bounded subscription requests")
struct SubscriptionFetchTests {
    @Test func cancelledFetchDoesNotWaitForNetworkTimeout() async throws {
        let operation = operation()
        let task = Task { try await operation.fetch(URL(string: "https://1.1.1.1/hang")!) }
        try await Task.sleep(for: .milliseconds(20))
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
    }

    @Test func alreadyCancelledFetchFinishesExactlyOnce() async {
        let task = Task { try await operation().fetch(URL(string: "https://1.1.1.1/hang")!) }
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
    }

    @Test(arguments: ["too-large", "invalid-utf8", "server-error"])
    func invalidResponsesAreRejected(path: String) async {
        await #expect(throws: (any Error).self) { try await operation().fetch(URL(string: "https://1.1.1.1/" + path)!) }
    }

    @Test func normalPayloadIsReturned() async throws {
        let operation = operation()
        let url = URL(string: "https://1.1.1.1/success")!
        let response = try await operation.fetch(url)
        #expect(response.payload == "proxies: []")
        await #expect(throws: (any Error).self) { try await operation.fetch(url) }
    }

    private func operation() -> SubscriptionFetchOperation {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [FixtureProtocol.self]
        return SubscriptionFetchOperation(configuration: configuration)
    }

    // Every request is intercepted; the public IP literal also avoids real DNS in URL checks.
    private final class FixtureProtocol: URLProtocol, @unchecked Sendable {
        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
            let path = request.url!.lastPathComponent
            guard path != "hang" else { return }
            let headers = path == "too-large" ? ["Content-Length": "5242881"] : [:]
            let response = HTTPURLResponse(url: request.url!, statusCode: path == "server-error" ? 503 : 200,
                                           httpVersion: "HTTP/1.1", headerFields: headers)!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: path == "invalid-utf8" ? Data([0xFF]) : Data("proxies: []".utf8))
            client?.urlProtocolDidFinishLoading(self)
        }
        override func stopLoading() {}
    }
}
