import Foundation
import WeaveCore

enum SelfTestFailure: Error {
    case failed(String)
}

@main
struct WeaveCoreSelfTest {
    static func main() async throws {
        try testNodeParser()
        try testURIConversion()
        try testURLPolicy()
        try testTransferCodec()
        try await testSecureStore()
        try testRuntimeCompiler()
        print("WeaveCoreSelfTest: 6 checks passed")
    }

    private static func require(_ condition: @autoclosure () -> Bool, _ message: String) throws {
        guard condition() else { throw SelfTestFailure.failed(message) }
    }

    private static func testNodeParser() throws {
        let payload = """
        proxies:
          - name: '🇯🇵 JP-N1 (0.3x)'
            type: vless
          - { name: "DE-N1 (0.3x)", type: trojan, server: example.com, port: 443 }
        proxy-groups: []
        """
        try require(
            ClashNodeParser.parse(payload) == ["🇯🇵 JP-N1 (0.3x)", "DE-N1 (0.3x)"],
            "node parser"
        )
        try require(
            ClashNodeParser.displayName("🇯🇵 JP-N1 (0.3x)") == "JP-N1 (0.3x)",
            "node display name"
        )
    }

    private static func testURIConversion() throws {
        let input = """
        vless://00000000-0000-0000-0000-000000000001@example.com:443?security=tls&sni=example.com#JP-N1
        trojan://secret@example.net:443?security=tls#US-N1
        """
        let payload = try SubscriptionImporter.normalizeForMihomo(input)
        try require(payload.contains("type: vless"), "vless conversion")
        try require(payload.contains("type: trojan"), "trojan conversion")
        try require(ClashNodeParser.parse(payload) == ["JP-N1", "US-N1"], "converted nodes")
    }

    private static func testURLPolicy() throws {
        do {
            _ = try SubscriptionImporter.validateRemoteURL("https://192.168.1.1/sub")
            throw SelfTestFailure.failed("private URL accepted")
        } catch is WeaveError {}
        do {
            _ = try SubscriptionImporter.validateRemoteURL("http://example.com/sub")
            throw SelfTestFailure.failed("plain HTTP accepted")
        } catch is WeaveError {}
        let publicURL = try SubscriptionImporter.validateRemoteURL("https://example.com/sub")
        try require(publicURL.host == "example.com", "public HTTPS URL rejected")
    }

    private static func testTransferCodec() throws {
        let items = [TransferSubscription(name: "演示", source: "https://example.com", payload: "proxies: []")]
        let key = Data(repeating: 7, count: 32)
        let packet = try TransferCodec.seal(TransferCodec.encode(items), key: key)
        let decoded = try TransferCodec.decode(TransferCodec.open(packet, key: key))
        try require(decoded == items, "transfer round trip")
        var tampered = packet
        tampered[tampered.index(before: tampered.endIndex)] ^= 0x01
        do {
            _ = try TransferCodec.open(tampered, key: key)
            throw SelfTestFailure.failed("tampered transfer accepted")
        } catch let error as SelfTestFailure { throw error }
        catch {}
    }

    private static func testSecureStore() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("weave-ios-store-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let key = Data(repeating: 9, count: 32)
        let payload = """
        proxies:
          - name: secret-node
            type: trojan
            server: example.com
            port: 443
            password: top-secret
        """
        let store = try SecureSubscriptionStore(directory: directory, keyData: key)
        _ = try await store.importPayload(
            ImportedSubscriptionPayload(suggestedName: "private", source: "https://example.com", payload: payload)
        )
        let encrypted = try Data(contentsOf: directory.appendingPathComponent("subscriptions.enc"))
        try require(!String(decoding: encrypted, as: UTF8.self).contains("top-secret"), "plaintext vault")
        let reopened = try SecureSubscriptionStore(directory: directory, keyData: key)
        let reopenedItems = try await reopened.all()
        try require(reopenedItems.first?.name == "private", "vault persistence")
    }

    private static func testRuntimeCompiler() throws {
        let subscription = WeaveSubscription(
            name: "main",
            source: "https://example.com",
            payload: """
            proxies:
              - name: JP-N1
                type: trojan
                server: example.com
                port: 443
                password: secret
            """,
            nodeCount: 1
        )
        let yaml = try RuntimeConfigCompiler.compileYAML(
            subscriptions: [subscription],
            selection: RuntimeSelection(subscriptionID: subscription.id, nodeName: "JP-N1"),
            routes: [DomainRouteRule(domainSuffix: "youtube.com", target: .direct)],
            preferences: RuntimePreferences()
        )
        guard let domain = yaml.range(of: "DOMAIN-SUFFIX,youtube.com,DIRECT"),
              let fallback = yaml.range(of: "MATCH,weave.") else {
            throw SelfTestFailure.failed("runtime rules missing")
        }
        try require(domain.lowerBound < fallback.lowerBound, "runtime rule priority")
        do {
            _ = try RuntimeConfigCompiler.compileYAML(
                subscriptions: [subscription],
                selection: RuntimeSelection(subscriptionID: subscription.id),
                routes: [DomainRouteRule(domainSuffix: "example.com,REJECT", target: .direct)],
                preferences: RuntimePreferences()
            )
            throw SelfTestFailure.failed("runtime domain rule injection accepted")
        } catch let error as SelfTestFailure { throw error }
        catch is WeaveError {}
        do {
            _ = try RuntimeConfigCompiler.compileYAML(
                subscriptions: [subscription],
                selection: RuntimeSelection(subscriptionID: subscription.id),
                routes: [],
                preferences: RuntimePreferences(
                    dnsProfile: .custom,
                    customDNSEndpoint: "https://user@example.com/dns-query?token=secret"
                )
            )
            throw SelfTestFailure.failed("unsafe custom DNS accepted")
        } catch let error as SelfTestFailure { throw error }
        catch is WeaveError {}

        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("weave-ios-runtime-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let descriptor = try RuntimeConfigCompiler.stage(
            in: directory,
            subscriptions: [subscription],
            selection: RuntimeSelection(subscriptionID: subscription.id),
            routes: [],
            preferences: RuntimePreferences(ipv6Mode: .ipv4Only)
        )
        try require(descriptor.schemaVersion == 2, "runtime descriptor schema")
        try require(!descriptor.ipv6Enabled, "runtime IPv6 handoff")
        let runtime = try RuntimeConfigCompiler.validateStagedRuntime(
            descriptor: descriptor,
            in: directory
        )
        if let binary = ProcessInfo.processInfo.environment["WEAVE_MIHOMO_BINARY"] {
            let process = Process()
            let output = Pipe()
            process.executableURL = URL(fileURLWithPath: binary)
            process.arguments = [
                "-t", "-d", runtime.path,
                "-f", runtime.appendingPathComponent("config.yaml").path,
            ]
            process.standardOutput = output
            process.standardError = output
            try process.run()
            process.waitUntilExit()
            let detail = String(decoding: output.fileHandleForReading.readDataToEndOfFile(), as: UTF8.self)
            guard process.terminationStatus == 0 else {
                throw SelfTestFailure.failed("mihomo config validation: \(detail)")
            }
        }
    }
}
