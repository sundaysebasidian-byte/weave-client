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
}
