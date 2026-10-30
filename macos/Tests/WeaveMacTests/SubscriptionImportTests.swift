import WeaveCoreRuntime
import Foundation
import Testing
@testable import WeaveMac

@Suite("Subscription import semantics")
struct SubscriptionImportTests {
    private let vless = "vless://12345678-1234-1234-1234-123456789abc@example.invalid:443"

    @Test func grpcPreservesServiceName() throws {
        let imported = try DirectSubscriptionImporter.inlinePayload(from: vless + "?type=grpc&serviceName=my-service&security=tls#GRPC")
        #expect(imported.payload.contains("grpc-service-name: 'my-service'"))
    }

    @Test func realityPreservesAuthenticationAndTLSOptions() throws {
        let imported = try DirectSubscriptionImporter.inlinePayload(from: vless + "?type=tcp&security=reality&pbk=test-public-key&sid=aabb&flow=xtls-rprx-vision&fp=chrome&sni=example.invalid&alpn=h2,http/1.1#Reality")
        for expected in ["reality-opts:", "public-key: 'test-public-key'", "short-id: 'aabb'", "flow: 'xtls-rprx-vision'", "client-fingerprint: 'chrome'", "alpn: ['h2', 'http/1.1']"] {
            #expect(imported.payload.contains(expected))
        }
    }

    @Test func unsupportedMixedInputIsNeverSilentlyTruncated() {
        #expect(throws: (any Error).self) {
            try DirectSubscriptionImporter.inlinePayload(from: "socks5://user:pass@example.invalid:1080#Good\nunsupported://user@example.invalid:443#Bad")
        }
    }

    @Test func unsupportedTransportAndIncompleteRealityAreRejected() {
        for suffix in ["?type=xhttp", "?security=reality", "?security=unknown", "?encryption=unknown"] {
            #expect(throws: (any Error).self) { try DirectSubscriptionImporter.inlinePayload(from: vless + suffix) }
        }
    }

    @Test func base64HasSameSemantics() throws {
        let uri = vless + "?type=grpc&serviceName=base64-service&security=tls#GRPC"
        let encoded = Data(uri.utf8).base64EncodedString()
        #expect(try DirectSubscriptionImporter.inlinePayload(from: encoded).payload == DirectSubscriptionImporter.inlinePayload(from: uri).payload)
    }

    @Test func trojanWebSocketIsPreserved() throws {
        let imported = try DirectSubscriptionImporter.inlinePayload(from: "trojan://secret@example.invalid:443?type=ws&path=%2Fws&host=cdn.invalid&sni=tls.invalid#Trojan")
        #expect(imported.payload.contains("network: 'ws'"))
        #expect(imported.payload.contains("path: '/ws'"))
        #expect(imported.payload.contains("Host: 'cdn.invalid'"))
    }

    @Test func hysteriaObfuscationIsPreserved() throws {
        let imported = try DirectSubscriptionImporter.inlinePayload(from: "hy2://secret@example.invalid:443?obfs=salamander&obfs-password=obfuscation#HY2")
        #expect(imported.payload.contains("obfs-password: 'obfuscation'"))
    }

    @Test func unknownAndConflictingParametersAreRejected() {
        for suffix in ["?unknown=setting", "?type=ws&type=grpc", "?type=tcp&path=%2Fignored", "?pbk=ignored", "?type=ws&network=grpc", "?security=tls&insecure=maybe"] {
            #expect(throws: (any Error).self) { try DirectSubscriptionImporter.inlinePayload(from: vless + suffix) }
        }
    }

    @Test func shadowsocksCredentialsAreDecodedExactlyOnce() throws {
        let raw = "aes-128-gcm:secret:with:colon"
        for credential in [raw, Data(raw.utf8).base64EncodedString()] {
            let imported = try DirectSubscriptionImporter.inlinePayload(from: "ss://\(credential)@example.invalid:443#SS")
            #expect(imported.payload.contains("cipher: 'aes-128-gcm'"))
            #expect(imported.payload.contains("password: 'secret:with:colon'"))
        }
    }

    @Test func vmessGrpcAndTLSOptionsArePreserved() throws {
        let json = #"{"v":"2","ps":"Example%20Name","add":"example.invalid","port":"443","id":"12345678-1234-1234-1234-123456789abc","aid":"0","net":"grpc","path":"grpc-service","tls":"tls","scy":"auto","fp":"chrome","alpn":"h2","allowInsecure":true}"#
        let imported = try DirectSubscriptionImporter.inlinePayload(from: "vmess://" + Data(json.utf8).base64EncodedString())
        #expect(imported.payload.contains("grpc-service-name: 'grpc-service'"))
        #expect(imported.payload.contains("client-fingerprint: 'chrome'"))
        #expect(imported.payload.contains("skip-cert-verify: true"))
        #expect(imported.payload.contains("Example%20Name"))
    }

    @Test func decodedVmessControlCharactersCannotInjectYAML() throws {
        let object = ["add": "example.invalid", "port": "443", "id": "12345678-1234-1234-1234-123456789abc", "ps": "first\n  external-controller: bad"]
        let data = try JSONSerialization.data(withJSONObject: object)
        #expect(throws: (any Error).self) { try DirectSubscriptionImporter.inlinePayload(from: "vmess://" + data.base64EncodedString()) }
    }

    @Test func vmessNumbersAndOptionsCannotBeSilentlyCoerced() throws {
        let base: [String: Any] = ["add": "example.invalid", "port": 443, "id": "12345678-1234-1234-1234-123456789abc"]
        let invalid: [[String: Any]] = [["port": true], ["port": 443.5], ["aid": 0.5], ["aid": -1],
                                       ["sni": "ignored.invalid"], ["host": "ignored.invalid"],
                                       ["tls": "tls", "allowInsecure": 5], ["tls": "tls", "allowInsecure": "maybe"]]
        for changes in invalid {
            let data = try JSONSerialization.data(withJSONObject: base.merging(changes) { _, new in new })
            #expect(throws: (any Error).self) { try DirectSubscriptionImporter.inlinePayload(from: "vmess://" + data.base64EncodedString()) }
        }
    }

    @Test func supportedProtocolFixturesAreAcceptedByActualCore() throws {
        let vmess = #"{"add":"example.invalid","port":443,"id":"12345678-1234-1234-1234-123456789abc","aid":0,"net":"grpc","path":"grpc-service","tls":"tls","scy":"auto","ps":"VMess"}"#
        let uris = [
            "ss://aes-128-gcm:fixture@example.invalid:443#SS",
            "socks5://user:fixture@example.invalid:1080#SOCKS",
            "trojan://fixture@example.invalid:443?type=ws&path=%2Fws&host=cdn.invalid&sni=tls.invalid#Trojan",
            vless + "?type=grpc&serviceName=service&security=tls#VLESS",
            vless + "?type=tcp&security=reality&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=aabb&flow=xtls-rprx-vision&fp=chrome&sni=example.invalid#Reality",
            "hy2://fixture@example.invalid:443?obfs=salamander&obfs-password=obfuscation#Hysteria",
            "tuic://12345678-1234-1234-1234-123456789abc:fixture@example.invalid:443?congestion_control=bbr&udp_relay_mode=native&alpn=h3#TUIC",
            "vmess://" + Data(vmess.utf8).base64EncodedString(),
        ]
        let converted = try DirectSubscriptionImporter.inlinePayload(from: uris.joined(separator: "\n"))
        let names = try ClashProviderDocument(converted.payload).names
        #expect(names.count == uris.count)
        let selected = MacSubscription(id: UUID(), name: "Protocol fixtures", source: "file://fixture", payload: converted.payload, nodeCount: names.count, updatedAt: .distantPast)
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try CoreRuntime.prepare(provider: selected.payload, selectedNodeName: nil, enableTun: false, root: root)
        defer { _ = try? runtime.directory.removeAfterCoreExit() }
        let executable = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
        _ = try BoundedCommand.run(executable: executable.path,
                                  arguments: ["-t", "-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path], timeout: 10)
    }

    @Test func unindentedYAMLSequenceRetainsEveryNodeAndRemovesControls() throws {
        let input = "proxies:\n- {name: first, type: socks5, server: example.invalid, port: 1080}\n- name: second\n  type: socks5\n  server: example.invalid\n  port: 1080\nexternal-controller: 0.0.0.0:9090\n"
        let clean = try ClashProviderSanitizer.sanitize(input)
        #expect(ClashNodeNames.parse(clean) == ["first", "second"])
        #expect(!clean.contains("external-controller"))
    }
}
