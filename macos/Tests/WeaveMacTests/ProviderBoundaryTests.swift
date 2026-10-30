import CryptoKit
import Darwin
import Foundation
import Testing
import WeaveCoreRuntime
import Yams

@Suite("Runtime provider capabilities", .serialized)
struct ProviderBoundaryTests {
    @Test(arguments: [
        ("ssh", "private-key: '/Users/other/.ssh/id_rsa'"),
        ("http", "certificate: 'config.yaml'"),
        ("socks5", "private-key: '../key.pem'"),
        ("trojan", "interface-name: en0"),
        ("trojan", "tls: false"),
        ("http", "dialer-proxy: DIRECT"),
        ("http", "routing-mark: 1"),
        ("http", "future-core-option: {path: config.yaml}"),
        ("openvpn", "config: 'vpn.conf'"),
        ("ss", "plugin: external-command, plugin-opts: {path: fixture}"),
        ("ss", "plugin: v2ray-plugin, plugin-opts: {certificate: 'nested.pem'}"),
        ("vless", "xhttp-opts: {download-settings: {private-key: 'nested.pem'}}"),
        ("hysteria2", "realm-opts: {certificate: 'nested.pem'}"),
        ("trojan", "certificate: '-----BEGIN CERTIFICATE----- fake -----END CERTIFICATE-----'"),
        ("ssh", "private-key: 'not-a-key PRIVATE KEY'"),
        ("http", "headers: {Host: \"injected\\r\\nHeader: value\"}"),
        ("vmess", "ws-opts: {future: {private-key: file}}"),
        ("wireguard", "private-key: '/private/key'"),
    ])
    func unreviewedOrFileOptionsAreRejectedBeforeWriting(_ entry: (String, String)) throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        #expect(throws: (any Error).self) { try prepare(node(entry.0, entry.1), root: root) }
        #expect(!FileManager.default.fileExists(atPath: root.path))
    }

    @Test func bypassingImportCannotAddControlPlaneRoots() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        #expect(throws: (any Error).self) {
            try prepare(node("http", "") + "\nproxy-providers: {outside: {type: file, path: secret.yaml}}", root: root)
        }
        #expect(!FileManager.default.fileExists(atPath: root.path))
    }

    @Test func actualGeneratedConfigurationRejectsInvalidNodeSemantics() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(node("vmess", "uuid: 11111111-1111-4111-8111-111111111111, cipher: auto"), root: root)
        #expect(throws: (any Error).self) { try validate(runtime) }
    }

    @Test(arguments: [
        ("http", "username: fixture, password: fixture"),
        ("socks5", "username: fixture, password: fixture, udp: true"),
        ("ss", "cipher: aes-128-gcm, password: fixture, plugin: v2ray-plugin, plugin-opts: {mode: websocket, host: example.invalid, path: /transport}"),
        ("ssr", "cipher: aes-128-cfb, password: fixture, obfs: plain, protocol: origin"),
        ("snell", "psk: isolated-fixture, version: 3, udp: true"),
        ("vmess", "uuid: 11111111-1111-4111-8111-111111111111, alterId: 0, cipher: auto, tls: true, servername: example.invalid, network: ws, ws-opts: {path: /transport, headers: {Host: example.invalid}}"),
        ("vless", "uuid: 11111111-1111-4111-8111-111111111111, tls: true, servername: example.invalid, network: grpc, grpc-opts: {grpc-service-name: fixture}"),
        ("trojan", "password: fixture, sni: example.invalid, network: ws, ws-opts: {path: /transport}"),
        ("hysteria", "auth-str: fixture, up: 10, down: 50"),
        ("hysteria2", "password: fixture, ports: 443-445, up: 10, down: 50"),
        ("tuic", "uuid: 11111111-1111-4111-8111-111111111111, password: fixture, congestion-controller: bbr"),
        ("anytls", "password: fixture, sni: example.invalid"),
        ("ssh", "username: fixture, password: fixture"),
        ("wireguard", "ip: 10.9.0.1, private-key: AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=, public-key: AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=, udp: true"),
    ])
    func supportedProtocolsAreParsedByTheActualCore(_ entry: (String, String)) throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(node(entry.0, entry.1), root: root)
        try validate(runtime)
        let config = try readConfig(runtime)
        #expect(config["proxies"]?.sequence?.count == 1)
        #expect(config["proxy-providers"] == nil)
    }

    @Test(arguments: [false, true])
    func exactNodeNamesSurviveGroupGeneration(automatic: Bool) throws {
        let name = "O'Brien, #1[.]\\$"
        let payload = String(decoding: try JSONSerialization.data(withJSONObject: ["proxies": [
            ["name": name, "type": "socks5", "server": "127.0.0.1", "port": 9],
            ["name": "another", "type": "socks5", "server": "127.0.0.1", "port": 9],
        ]]), as: UTF8.self)
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try CoreRuntime.prepare(provider: payload, selectedNodeName: automatic ? nil : name, enableTun: false, root: root)
        try validate(runtime)
        let config = try readConfig(runtime)
        let groups = try #require(config["proxy-groups"]?.sequence)
        #expect(groups.first?["proxies"]?.sequence?.compactMap(\.string) == (automatic ? [name, "another"] : [name]))
    }

    @Test(arguments: [("rsa", "pkcs1"), ("rsa", "pkcs8"), ("ecdsa", "sec1"), ("ecdsa", "pkcs8"), ("ed25519", "pkcs8")])
    func clientMaterialUsesOnlyGeneratedPrivateFiles(_ format: (String, String)) throws {
        let certificate = try material(format.0 + "-cert")
        let key = try material(format.0 + "-" + format.1)
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let proxy: [String: Any] = ["name": "fixture", "type": "http", "server": "127.0.0.1", "port": 443,
                                    "tls": true, "certificate": certificate, "private-key": key]
        let runtime = try prepare(jsonProvider([proxy]), root: root)
        try validate(runtime)
        let node = try #require(try readConfig(runtime)["proxies"]?.sequence?.first)
        try assertMaterialPaths(node, runtime: runtime, certificate: certificate, key: key)
        #expect(try runtime.directory.removeAfterCoreExit())
        #expect(!FileManager.default.fileExists(atPath: runtime.directory.url.path))
    }

    @Test(arguments: ["v2ray-plugin", "gost-plugin", "shadow-tls", "xhttp-download", "hysteria2-realm"])
    func nestedMaterialCannotRetainCallerPaths(location: String) throws {
        let certificate = try material("rsa-cert"), key = try material("rsa-pkcs8")
        var proxy: [String: Any] = ["name": "fixture", "server": "127.0.0.1", "port": 443]
        var pair: [String: Any] = ["certificate": certificate, "private-key": key]
        let path: [String]
        switch location {
        case "xhttp-download":
            pair["tls"] = true
            proxy.merge(["type": "vless", "uuid": "11111111-1111-4111-8111-111111111111", "network": "xhttp",
                         "xhttp-opts": ["download-settings": pair]]) { _, next in next }
            path = ["xhttp-opts", "download-settings"]
        case "hysteria2-realm":
            pair.merge(["enable": true, "server-url": "https://127.0.0.1:9", "token": "fixture", "realm-id": "fixture"]) { _, next in next }
            proxy.merge(["type": "hysteria2", "password": "fixture", "realm-opts": pair]) { _, next in next }
            path = ["realm-opts"]
        default:
            pair.merge(location == "shadow-tls" ? ["host": "example.invalid", "password": "fixture", "version": 3]
                : ["mode": "websocket", "host": "example.invalid", "tls": true]) { _, next in next }
            proxy.merge(["type": "ss", "cipher": "aes-128-gcm", "password": "fixture", "plugin": location, "plugin-opts": pair]) { _, next in next }
            path = ["plugin-opts"]
        }
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(jsonProvider([proxy]), root: root)
        try validate(runtime)
        var node = try #require(try readConfig(runtime)["proxies"]?.sequence?.first)
        for key in path { node = try #require(node[key]) }
        try assertMaterialPaths(node, runtime: runtime, certificate: certificate, key: key)
    }

    @Test(arguments: [false, true])
    func coreRejectsMalformedOrMismatchedMaterial(mismatch: Bool) throws {
        let certificate = try material("rsa-cert")
        let key = mismatch ? try material("ecdsa-pkcs8") : "-----BEGIN PRIVATE KEY-----\nAQID\n-----END PRIVATE KEY-----\n"
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let proxy: [String: Any] = ["name": "fixture", "type": "http", "server": "127.0.0.1", "port": 443,
                                    "tls": true, "certificate": certificate, "private-key": key]
        let runtime = try prepare(jsonProvider([proxy]), root: root)
        #expect(throws: (any Error).self) { try validate(runtime) }
        let node = try #require(try readConfig(runtime)["proxies"]?.sequence?.first)
        try assertMaterialPaths(node, runtime: runtime, certificate: certificate, key: key)
    }

    @Test func repeatedMaterialIsDeduplicatedAndUnknownFilesSurviveCleanup() throws {
        let certificate = try material("rsa-cert"), key = try material("rsa-pkcs8")
        let proxies: [[String: Any]] = ["fixture", "second"].map { name in
            ["name": name, "type": "http", "server": "127.0.0.1", "port": 443, "tls": true, "certificate": certificate, "private-key": key]
        }
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(jsonProvider(proxies), root: root)
        let folder = runtime.directory.url.appendingPathComponent("tls-materials")
        #expect(try FileManager.default.contentsOfDirectory(atPath: folder.path).count == 2)
        let unknown = folder.appendingPathComponent("user.pem")
        try Data("retain".utf8).write(to: unknown)
        #expect(try !runtime.directory.removeAfterCoreExit())
        #expect(try Data(contentsOf: unknown) == Data("retain".utf8))
    }

    @Test func headerAndTransportPathsRemainData() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(node("vmess", "uuid: 11111111-1111-4111-8111-111111111111, alterId: 0, cipher: auto, network: ws, ws-opts: {path: /private-key, headers: {certificate: '/some/file'}}"), root: root)
        try validate(runtime)
        let ws = try #require(try readConfig(runtime)["proxies"]?.sequence?.first?["ws-opts"])
        #expect(ws["path"]?.string == "/private-key")
        #expect(ws["headers"]?["certificate"]?.string == "/some/file")
        #expect(!FileManager.default.fileExists(atPath: runtime.directory.url.appendingPathComponent("tls-materials").path))
    }

    @Test func sshPrivateKeysStayOnTheInlineLoadingBranch() throws {
        let key = try material("rsa-pkcs1")
        let proxy: [String: Any] = ["name": "fixture", "type": "ssh", "server": "127.0.0.1", "port": 22, "username": "fixture", "private-key": key]
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(jsonProvider([proxy]), root: root)
        try validate(runtime)
        let node = try #require(try readConfig(runtime)["proxies"]?.sequence?.first)
        #expect(node["private-key"]?.string == key)
    }

    @Test(arguments: [false, true])
    func encryptedSSHMaterialRequiresTheActualPassphrase(wrong: Bool) throws {
        let key = try material("ssh-encrypted")
        let proxy: [String: Any] = ["name": "fixture", "type": "ssh", "server": "127.0.0.1", "port": 22,
                                    "username": "fixture", "private-key": key, "private-key-passphrase": wrong ? "wrong" : "weave-public-fixture"]
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(jsonProvider([proxy]), root: root)
        if wrong { #expect(throws: (any Error).self) { try validate(runtime) } }
        else { try validate(runtime) }
    }

    @Test func materialCountIsBoundedBeforeWrites() throws {
        let certificate = try material("rsa-cert")
        let proxies: [[String: Any]] = (0..<256).map { index in
            let body = Data("synthetic-\(index)".utf8).base64EncodedString()
            return ["name": index == 0 ? "fixture" : "node-\(index)", "type": "http", "server": "127.0.0.1", "port": 443,
                    "tls": true, "certificate": certificate, "private-key": "-----BEGIN PRIVATE KEY-----\n\(body)\n-----END PRIVATE KEY-----\n"]
        }
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        #expect(throws: (any Error).self) { try prepare(jsonProvider(proxies), root: root) }
        #expect(!FileManager.default.fileExists(atPath: root.path))
    }

    @Test(arguments: ["scalar", "list", "headers", "total", "expanded"])
    func providerBudgetsAreEnforcedBeforeWrites(limit: String) throws {
        let payload: String
        switch limit {
        case "scalar": payload = node("http", "password: '" + String(repeating: "a", count: 65_537) + "'")
        case "list": payload = node("trojan", "password: fixture, alpn: [" + Array(repeating: "h2", count: 1025).joined(separator: ",") + "]")
        case "headers": payload = node("http", "headers: {" + (0..<129).map { "X-\($0): value" }.joined(separator: ",") + "}")
        case "total": payload = String(repeating: "a", count: 5 * 1024 * 1024 + 1)
        default:
            payload = "proxies:\n- {name: fixture, type: http, server: 127.0.0.1, port: 443, password: &shared '" + String(repeating: "a", count: 50_000) + "'}\n" +
                (1..<128).map { "- {name: node-\($0), type: http, server: 127.0.0.1, port: 443, password: *shared}" }.joined(separator: "\n")
        }
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        #expect(throws: (any Error).self) { try prepare(payload, root: root) }
        #expect(!FileManager.default.fileExists(atPath: root.path))
    }

    @Test func materialSymlinksPreventSessionCleanup() throws {
        let certificate = try material("rsa-cert"), key = try material("rsa-pkcs8")
        let proxy: [String: Any] = ["name": "fixture", "type": "http", "server": "127.0.0.1", "port": 443,
                                    "tls": true, "certificate": certificate, "private-key": key]
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let runtime = try prepare(jsonProvider([proxy]), root: root)
        let config = try readConfig(runtime)
        let path = try #require(config["proxies"]?.sequence?.first?["certificate"]?.string)
        let file = runtime.directory.url.appendingPathComponent(path)
        let sentinel = root.appendingPathComponent("sentinel")
        try Data("retain".utf8).write(to: sentinel)
        try FileManager.default.removeItem(at: file)
        try FileManager.default.createSymbolicLink(at: file, withDestinationURL: sentinel)
        #expect(try !runtime.directory.removeAfterCoreExit())
        #expect(try Data(contentsOf: sentinel) == Data("retain".utf8))
    }

    @Test(arguments: ["rsa", "ecdsa", "ed25519", "missing"])
    func actualForwardingRequiresTheSelectedClientCertificate(algorithm: String) async throws {
        let root = temporaryRoot()
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: false)
        let logURL = root.appendingPathComponent("server.log")
        try Data().write(to: logURL)
        let log = try FileHandle(forWritingTo: logURL)
        defer { try? log.close() }
        let server = Process()
        var core: CoreProcess?
        var failure: (any Error)?
        server.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        server.arguments = ["python3", fixtureRoot.appendingPathComponent("server.py").path, root.path]
        server.standardOutput = log
        server.standardError = log
        server.terminationHandler = { _ in }
        do {
            try server.run()
            var port: Int?
            for _ in 0..<300 {
                if let data = try? Data(contentsOf: root.appendingPathComponent("port.json")),
                   let value = try? JSONDecoder().decode(Int.self, from: data) { port = value; break }
                if !server.isRunning { break }
                try await Task.sleep(for: .milliseconds(10))
            }
            var proxy: [String: Any] = ["name": "fixture", "type": "http", "server": "127.0.0.1", "port": try #require(port),
                                        "tls": true, "skip-cert-verify": true]
            if algorithm != "missing" {
                proxy["certificate"] = try material(algorithm + "-cert")
                proxy["private-key"] = try material(algorithm + "-pkcs8")
            }
            let runtime = try prepare(jsonProvider([proxy]), root: root.appendingPathComponent("sessions"))
            try validate(runtime)
            let child = try CoreProcess(executable: coreURL, runtime: runtime, onExit: {})
            core = child
            try await CoreRuntime.waitUntilReady(runtime: runtime, pid: child.processIdentifier, requireTun: false)
            let response = await Task.detached {
                Result {
                    try BoundedCommand.run(executable: "/usr/bin/curl", arguments: ["--silent", "--show-error", "--fail", "--max-time", "4",
                        "--noproxy", "", "--proxy", "http://127.0.0.1:\(runtime.mixedPort)", "http://198.51.100.42/material-check"], timeout: 6)
                }
            }.value
            if algorithm == "missing" {
                #expect(throws: (any Error).self) { try response.get() }
                #expect(!FileManager.default.fileExists(atPath: root.appendingPathComponent("peer.sha256").path))
            } else {
                #expect(try response.get() == "weave-client-certificate-verified")
                let pem = try material(algorithm + "-cert")
                let base64 = pem.components(separatedBy: "\n").filter { !$0.hasPrefix("-----") }.joined()
                let der = try #require(Data(base64Encoded: base64))
                let expected = SHA256.hash(data: der).map { String(format: "%02x", $0) }.joined()
                #expect(try String(contentsOf: root.appendingPathComponent("peer.sha256"), encoding: .utf8) == expected)
            }
            #expect(await child.stop())
            #expect(try runtime.directory.removeAfterCoreExit())
        } catch {
            for file in ["server.log", "tls-errors.txt"] {
                if let diagnostic = try? String(contentsOf: root.appendingPathComponent(file), encoding: .utf8), !diagnostic.isEmpty {
                    Issue.record("Synthetic TLS fixture: \(diagnostic.prefix(2048))")
                }
            }
            failure = error
        }
        let coreStopped = await core?.stop() ?? true
        let serverStopped = await Task.detached {
            if server.isRunning { server.terminate() }
            for _ in 0..<30 where server.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
            if server.isRunning { kill(server.processIdentifier, SIGKILL) }
            for _ in 0..<20 where server.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
            return !server.isRunning
        }.value
        #expect(coreStopped)
        #expect(serverStopped)
        if coreStopped && serverStopped { try FileManager.default.removeItem(at: root) }
        if let failure { throw failure }
    }

    private func material(_ name: String) throws -> String {
        try String(contentsOf: fixtureRoot.appendingPathComponent("\(name).pem"), encoding: .utf8)
    }
    private var fixtureRoot: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().appendingPathComponent("tests/fixtures/provider-materials")
    }
    private func jsonProvider(_ proxies: [[String: Any]]) throws -> String {
        String(decoding: try JSONSerialization.data(withJSONObject: ["proxies": proxies]), as: UTF8.self)
    }
    private func assertMaterialPaths(_ node: Node, runtime: MacRuntime, certificate: String, key: String) throws {
        for (field, original) in [("certificate", certificate), ("private-key", key)] {
            let path = try #require(node[field]?.string)
            #expect(path.hasPrefix("./tls-materials/"))
            let file = runtime.directory.url.appendingPathComponent(path)
            #expect(UUID(uuidString: file.deletingPathExtension().lastPathComponent) != nil)
            #expect(try String(contentsOf: file, encoding: .utf8) == original)
            let mode = try FileManager.default.attributesOfItem(atPath: file.path)[.posixPermissions] as? NSNumber
            #expect(mode?.intValue == 0o600)
        }
        let folder = runtime.directory.url.appendingPathComponent("tls-materials")
        let mode = try FileManager.default.attributesOfItem(atPath: folder.path)[.posixPermissions] as? NSNumber
        #expect(mode?.intValue == 0o700)
    }

    private func node(_ type: String, _ options: String) -> String {
        "proxies: [{name: fixture, type: \(type), server: 127.0.0.1, port: 443" + (options.isEmpty ? "" : ", " + options) + "}]"
    }
    private func temporaryRoot() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("WeaveProvider-\(UUID())")
    }
    private func prepare(_ provider: String, root: URL) throws -> MacRuntime {
        try CoreRuntime.prepare(provider: provider, selectedNodeName: "fixture", enableTun: false, root: root)
    }
    private func readConfig(_ runtime: MacRuntime) throws -> Node {
        let parsed = try Yams.compose(yaml: String(contentsOf: runtime.directory.url.appendingPathComponent("config.yaml"), encoding: .utf8))
        return try #require(parsed)
    }
    private func validate(_ runtime: MacRuntime) throws {
        _ = try BoundedCommand.run(executable: coreURL.path,
            arguments: ["-t", "-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path], timeout: 10)
    }
    private var coreURL: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
    }
}
