import Foundation
import Testing
@testable import WeaveMac

@Suite("Bounded structured YAML providers")
struct ClashProviderDocumentTests {
    @Test(arguments: [
        #"{"proxies":[{"type":"socks5","port":1080,"server":"example.invalid","name":"JSON, 节点"}],"external-controller":"0.0.0.0:9090"}"#,
        "\"proxies\":\n- type: socks5\n  port: 1080\n  server: example.invalid\n  name: 'JSON, 节点'\nexternal-controller: 0.0.0.0:9090\n",
    ])
    func jsonAndReorderedKeysUseOneImportPipeline(input: String) throws {
        for text in [input, Data(input.utf8).base64EncodedString()] {
            let normalized = try DirectSubscriptionImporter.inlinePayload(from: text).payload
            #expect(ClashNodeNames.parse(normalized) == ["JSON, 节点"])
            #expect(!normalized.contains("external-controller"))
        }
    }

    @Test func mergePrecedenceAndAnchorsAreResolvedWithoutRetainingControlRoots() throws {
        let input = """
        listeners: &defaults {type: socks5, server: first.invalid, port: 1080, password: 'yes'}
        other: &other {server: second.invalid, port: 9999}
        proxies:
        - <<: [*defaults, *other]
          name: 'O''Brien, #1'
          port: 1081
        external-controller: 0.0.0.0:9090
        """
        let clean = try ClashProviderSanitizer.sanitize(input)
        #expect(ClashNodeNames.parse(clean) == ["O'Brien, #1"])
        #expect(clean.contains("first.invalid"))
        #expect(clean.contains("1081"))
        #expect(!clean.contains("second.invalid"))
        #expect(!clean.contains("listeners"))
        #expect(!clean.contains("external-controller"))
        #expect(!clean.contains("*defaults"))
        #expect(try ClashProviderSanitizer.sanitize(clean) == clean)
    }

    @Test(arguments: [
        "proxies: []\n---\nproxies: []",
        "proxies: []\nproxies: []",
        "proxies: &loop [*loop]",
        "proxies: [*missing]",
        "proxies: !!python/object:danger {}",
        "? [complex, key]\n: value\nproxies: []",
        "proxies: [{name: same, type: socks5}, {name: same, type: socks5}]",
        "proxies: [{name: first, name: second, type: socks5}]",
        "proxies: [{name: first, type: socks5, <<: 42}]",
        "proxies: [{name: first, type: socks5}, {type: socks5}]",
    ])
    func malformedOrAmbiguousInputIsRejected(input: String) {
        #expect(throws: (any Error).self) { try ClashProviderSanitizer.sanitize(input) }
    }

    @Test func hostileDepthAndAliasExpansionAreRejectedBeforeComposition() {
        let deep = "proxies: " + String(repeating: "[", count: 10_000) + "0" + String(repeating: "]", count: 10_000)
        #expect(throws: (any Error).self) { try ClashProviderSanitizer.sanitize(deep) }
        var bomb = "a0: &a0 ['value']\n"
        for index in 1...12 {
            bomb += "a\(index): &a\(index) [" + Array(repeating: "*a\(index - 1)", count: 10).joined(separator: ",") + "]\n"
        }
        bomb += "proxies: *a12\n"
        #expect(throws: (any Error).self) { try ClashProviderSanitizer.sanitize(bomb) }
    }

    @Test func parserErrorsNeverQuoteSourceCredentials() {
        let secret = "do-not-echo-password"
        do {
            _ = try ClashProviderSanitizer.sanitize("proxies: [{name: \(secret), type: socks5, password: [}")
            Issue.record("Malformed YAML was accepted")
        } catch { #expect(!error.localizedDescription.contains(secret)) }
    }

    @Test func fiveThousandNodesRemainComplete() throws {
        let nodes = (0..<5_000).map { "- {server: example.invalid, port: 1080, type: socks5, name: 'Node \($0)'}" }
        let document = try ClashProviderDocument("proxies:\n" + nodes.joined(separator: "\n"))
        #expect(document.names.count == 5_000)
        #expect(document.names.last == "Node 4999")
    }
}
