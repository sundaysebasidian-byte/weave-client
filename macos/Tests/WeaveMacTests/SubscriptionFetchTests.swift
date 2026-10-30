import WeaveCoreRuntime
import CWeaveHTTP
import Darwin
import Foundation
import Testing
@testable import WeaveMac

@Suite("HTTPS transport and destination policy", .serialized) @MainActor
struct SubscriptionFetchTests {
    @Test func cancelledFetchDoesNotWaitForNetworkTimeout() async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        let operation = try fixture.operation()
        let task = Task { try await operation.fetch(fixture.url("hang")) }
        try await Task.sleep(for: .milliseconds(100))
        let cancelled = ContinuousClock.now
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(cancelled.duration(to: .now) < .seconds(3))
    }

    @Test func alreadyCancelledFetchDoesNotOpenSocket() async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        let operation = try fixture.operation()
        // Cancellation precedes fetch invocation, rather than racing task scheduling.
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await operation.fetch(fixture.url("hang"))
        }
        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(try fixture.events().isEmpty)
    }

    @Test func cancellationDuringCoreResolutionNeverStartsHTTPS() async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        let operation = try fixture.operation(hostResolver: { _ in
            do { try await Task.sleep(for: .seconds(30)) }
            catch { throw WeaveMacError.message("DNS query cancelled") }
            return ["8.8.8.8"]
        })
        let task = Task { try await operation.fetch(fixture.url("success")) }
        try await Task.sleep(for: .milliseconds(20))
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(try fixture.events().isEmpty)
    }

    @Test(arguments: ["too-large", "gzip-bomb", "huge-headers", "invalid-utf8", "server-error"])
    func invalidResponsesAreRejected(path: String) async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        await #expect(throws: (any Error).self) { try await fixture.operation().fetch(fixture.url(path)) }
    }

    @Test(arguments: ["success", "chunked", "redirect"])
    func TLSHostAndPayloadRemainCorrect(path: String) async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        let operation = try fixture.operation()
        let response = try await operation.fetch(fixture.url(path))
        #expect(response.payload == "proxies: []")
        #expect(response.url.host == HTTPSFixture.hostname)
        let events = try fixture.events()
        #expect(events.contains { $0["event"] == "sni" && $0["name"] == HTTPSFixture.hostname })
        #expect(events.contains { $0["event"] == "request" && $0["host"] == "\(HTTPSFixture.hostname):\(fixture.port)" })
        await #expect(throws: (any Error).self) { try await operation.fetch(fixture.url("success")) }
    }

    @Test(arguments: [false, true]) func badCertificateOrHostnameIsRejected(wrongHost: Bool) async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        let host = wrongHost ? "wrong.fixture.invalid" : HTTPSFixture.hostname
        do {
            _ = try await fixture.operation(trustFixture: wrongHost, host: host).fetch(fixture.url("success", host: host))
            Issue.record("Invalid TLS identity was accepted")
        } catch { #expect(error.localizedDescription.contains("证书")) }
        #expect(try !fixture.events().contains { $0["event"] == "request" })
    }

    @Test func actualResolvedPrivateAddressIsBlockedBeforeConnect() async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        // URL has a public-style hostname; libcurl resolves it to loopback via the test DNS entry.
        // Production's actual socket callback must refuse before the listener accepts a socket.
        do {
            _ = try await fixture.operation(allowFixtureLoopback: false).fetch(fixture.url("success"))
            Issue.record("A private connection escaped the production address filter")
        } catch { #expect(error.localizedDescription.contains("内网")) }
        #expect(try fixture.events().isEmpty)
    }

    @Test func suppliedCoreAddressIsPinnedAndPrivateResultsAreRejected() async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        // The injected test DNS entry normally points at the successful loopback TLS fixture.
        // A supplied public answer must replace it. The fixture-only socket filter then refuses
        // that public address, proving it was used without sending traffic outside the machine.
        for address in ["8.8.8.8", "127.0.0.1", "invalid,127.0.0.1"] {
            await #expect(throws: (any Error).self) {
                try await fixture.operation(hostResolver: { _ in [address] }).fetch(fixture.url("success"))
            }
        }
        #expect(try fixture.events().isEmpty)
    }

    @Test func actualCoreDNSReturnsRealAddressesInFakeIPMode() async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveCoreDNS-\(UUID())")
        defer { try? FileManager.default.removeItem(at: root) }
        let selected = MacSubscription(id: UUID(), name: "DNS fixture", source: "file://fixture",
                                       payload: "proxies: [{name: Fixture, type: socks5, server: 127.0.0.1, port: 9}]",
                                       nodeCount: 1, updatedAt: .distantPast)
        let runtime = try CoreRuntime.prepare(provider: selected.payload, selectedNodeName: "Fixture", enableTun: false, root: root)
        defer { _ = try? runtime.directory.removeAfterCoreExit() }
        let config = runtime.directory.url.appendingPathComponent("config.yaml")
        var yaml = try String(contentsOf: config, encoding: .utf8)
        let start = try #require(yaml.range(of: "  default-nameserver:"))
        let end = try #require(yaml.range(of: "sniffer:"))
        let upstream = "'127.0.0.1:\(try fixture.dnsPort())'"
        yaml.replaceSubrange(start.lowerBound..<end.lowerBound,
                             with: "  default-nameserver: [\(upstream)]\n  nameserver: [\(upstream)]\n  proxy-server-nameserver: [\(upstream)]\n")
        try yaml.write(to: config, atomically: true, encoding: .utf8)
        let core = Process()
        core.executableURL = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
        core.arguments = ["-d", runtime.directory.url.path, "-f", config.path]
        core.standardInput = runtime.directory.lease
        core.standardOutput = FileHandle.nullDevice
        core.standardError = FileHandle.nullDevice
        try core.run()
        defer { if core.isRunning { core.terminate(); core.waitUntilExit() } }
        try await CoreRuntime.waitUntilReady(runtime: runtime, pid: core.processIdentifier, requireTun: false)
        let resolver = CoreDNSResolver(controllerPort: runtime.controllerPort, secret: runtime.secret, processID: core.processIdentifier)
        #expect(try Set(await resolver.resolve("resolved.fixture.invalid")) == ["8.8.8.8", "2606:4700:4700::1111"])
        let unauthorized = CoreDNSResolver(controllerPort: runtime.controllerPort, secret: "wrong", processID: core.processIdentifier)
        await #expect(throws: (any Error).self) { try await unauthorized.resolve("resolved.fixture.invalid") }
        core.terminate()
        core.waitUntilExit()
        await #expect(throws: (any Error).self) { try await resolver.resolve("resolved.fixture.invalid") }
    }

    @Test(arguments: ["private", "downgrade", "credentials", "loop"])
    func redirectsCannotBypassURLPolicyOrLimit(path: String) async throws {
        let fixture = try HTTPSFixture()
        defer { fixture.close() }
        do {
            _ = try await fixture.operation().fetch(fixture.url(path))
            Issue.record("Unsafe redirect was accepted")
        } catch { #expect(!error.localizedDescription.contains("private-token")) }
        let requests = try fixture.events().filter { $0["event"] == "request" }
        #expect(requests.count == (path == "loop" ? 6 : 1))
    }

    @Test func sharedAddressPolicyMatchesEveryPlatformFixture() throws {
        let fixture = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("tests/fixtures/network/public-addresses.tsv")
        for line in try String(contentsOf: fixture, encoding: .utf8).split(separator: "\n") where !line.hasPrefix("#") {
            let fields = line.split(separator: "\t")
            #expect(fields.count == 2)
            let literal = String(fields[0])
            #expect(literal.withCString { weave_http_is_public_ip($0) } == (fields[1] == "true"), Comment(rawValue: literal))
        }
    }

}

@MainActor private final class HTTPSFixture {
    static let hostname = "https.fixture.invalid"
    private let directory: URL
    private let fixture: URL
    private let process = Process()
    let port: Int

    init() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("WeaveHTTPS-\(UUID())")
        fixture = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("tests/fixtures/https")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        process.executableURL = URL(fileURLWithPath: "/usr/bin/python3")
        process.arguments = [fixture.appendingPathComponent("server.py").path, directory.path]
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        let portFile = directory.appendingPathComponent("port.txt")
        var found: Int?
        for _ in 0..<500 {
            if let value = try? String(contentsOf: portFile, encoding: .utf8), let port = Int(value), port > 0 { found = port; break }
            if !process.isRunning { break }
            Thread.sleep(forTimeInterval: 0.01)
        }
        guard let found else {
            if process.isRunning { process.terminate() }
            throw WeaveMacError.message("Isolated HTTPS fixture did not start")
        }
        port = found
    }

    func url(_ path: String, host: String = hostname) -> URL { URL(string: "https://\(host):\(port)/\(path)")! }

    func operation(trustFixture: Bool = true, allowFixtureLoopback: Bool = true, host: String = hostname,
                   hostResolver: SubscriptionHostResolver? = nil) throws -> SubscriptionFetchOperation {
        try fixture.appendingPathComponent("test-only-cert.pem").path.withCString { ca in
            try "\(host):\(port):127.0.0.1".withCString { resolve in
                let options = WeaveHTTPOptions(certificate_authority_file: trustFixture ? ca : nil, resolve_entry: resolve,
                                               address_filter: allowFixtureLoopback ? fixtureLoopback : nil)
                return try SubscriptionFetchOperation(options: options, hostResolver: hostResolver)
            }
        }
    }

    func dnsPort() throws -> Int { try Int(String(contentsOf: directory.appendingPathComponent("dns-port.txt"), encoding: .utf8))! }

    func events() throws -> [[String: String]] {
        let path = directory.appendingPathComponent("events.jsonl")
        guard FileManager.default.fileExists(atPath: path.path) else { return [] }
        return try String(contentsOf: path, encoding: .utf8).split(separator: "\n").map {
            try JSONDecoder().decode([String: String].self, from: Data($0.utf8))
        }
    }

    func close() {
        if process.isRunning {
            process.terminate()
            process.waitUntilExit()
        }
        try? FileManager.default.removeItem(at: directory)
    }
}

private func fixtureLoopback(_ address: UnsafePointer<sockaddr>?, _ length: socklen_t) -> Bool {
    guard let address, address.pointee.sa_family == AF_INET, length >= MemoryLayout<sockaddr_in>.size else { return false }
    return address.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr == inet_addr("127.0.0.1") }
}
