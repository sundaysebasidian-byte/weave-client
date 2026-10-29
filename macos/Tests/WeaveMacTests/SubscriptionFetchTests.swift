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
        let runtime = try MihomoController.prepareRuntime(selected: selected, selectedNodeName: "Fixture", enableTun: false, root: root)
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
        try await MihomoController.waitUntilReady(runtime: runtime, pid: core.processIdentifier, requireTun: false)
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

    @Test(arguments: [
        "0.0.0.0", "10.1.2.3", "127.0.0.1", "169.254.169.254", "172.16.1.1", "172.31.255.255",
        "192.168.1.1", "100.64.0.1", "100.127.255.255", "224.0.0.1", "255.255.255.255",
        "198.18.0.1", "198.19.255.255", "192.0.2.1", "198.51.100.1", "203.0.113.1",
        "::", "::1", "fc00::1", "fd00::1", "fe80::1", "fec0::1", "ff02::1",
        "::ffff:127.0.0.1", "::ffff:10.0.0.1", "64:ff9b::a00:1", "64:ff9b:1::1",
        "2002:7f00:1::", "2001:db8::1", "2001::1", "2001:20::1", "3fff::1",
    ])
    func specialUseAddressCannotBecomeASubscriptionSocket(address: String) {
        #expect(!isPublic(address))
    }

    @Test(arguments: ["1.1.1.1", "8.8.8.8", "172.15.255.255", "172.32.0.0", "100.63.255.255", "100.128.0.0",
                      "2606:4700:4700::1111", "2001:4860:4860::8888", "::ffff:8.8.8.8", "64:ff9b::808:808"])
    func globalAddressesRemainAvailable(address: String) { #expect(isPublic(address)) }

    private func isPublic(_ text: String) -> Bool {
        if text.contains(":") {
            var address = sockaddr_in6()
            address.sin6_family = sa_family_t(AF_INET6)
            guard inet_pton(AF_INET6, text, &address.sin6_addr) == 1 else { return false }
            return withUnsafePointer(to: &address) { pointer in
                pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                    weave_http_is_public_address($0, socklen_t(MemoryLayout<sockaddr_in6>.size))
                }
            }
        }
        var address = sockaddr_in()
        address.sin_family = sa_family_t(AF_INET)
        guard inet_pton(AF_INET, text, &address.sin_addr) == 1 else { return false }
        return withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                weave_http_is_public_address($0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
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
        fixture = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("Fixtures/HTTPS")
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
