import WeaveCoreRuntime
import Foundation
import Testing
@testable import WeaveMac

@Suite("Proxy transaction ownership") @MainActor
struct SystemProxyManagerTests {
    @Test func restoresOnlyFieldsStillOwnedByThisSession() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let manager = fixture.manager()
        try await manager.enable(port: 17890)
        fixture.network.web = .init(enabled: true, server: "new.proxy", port: 9000)
        fixture.network.autoURL = "https://new.invalid/proxy.pac"
        fixture.network.autoEnabled = true
        fixture.network.bypass = ["new.internal"]
        #expect(await manager.restore() == .preservedChanges)
        #expect(fixture.network.web.server == "new.proxy")
        #expect(fixture.network.secure == FakeNetwork.original)
        #expect(fixture.network.socks == FakeNetwork.original)
        #expect(fixture.network.autoURL == "https://new.invalid/proxy.pac")
        #expect(fixture.network.autoEnabled)
        #expect(fixture.network.bypass == ["new.internal"])
        #expect(!FileManager.default.fileExists(atPath: fixture.journal.path))
    }

    @Test func partialRestoreSurvivesRestartWithoutOverwritingResolvedFields() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let manager = fixture.manager()
        try await manager.enable(port: 17890)
        fixture.network.failNextOff = "-setwebproxystate"
        #expect(await manager.restore() == .failed)
        #expect(fixture.network.web.server == FakeNetwork.original.server)
        #expect(fixture.network.web.enabled)
        #expect(FileManager.default.fileExists(atPath: fixture.journal.path))
        // HTTPS restoration already completed. A subsequent external change must survive retry.
        fixture.network.secure = .init(enabled: true, server: "later.proxy", port: 9001)
        let recovered = fixture.manager()
        #expect(await recovered.restore() == .restored)
        #expect(await recovered.isActive == false)
        #expect(fixture.network.web == FakeNetwork.original)
        #expect(fixture.network.secure.server == "later.proxy")
        #expect(fixture.network.bypass == ["old.internal"])
    }

    @Test func failedEnableRollsBackAndReportsFailure() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let manager = fixture.manager()
        fixture.network.failNextCommand = "-setsecurewebproxy"
        await #expect(throws: (any Error).self) { try await manager.enable(port: 17890) }
        #expect(fixture.network.web == FakeNetwork.original)
        #expect(fixture.network.secure == FakeNetwork.original)
        #expect(await manager.isActive == false)
    }

    @Test func unreadableJournalIsNeverReplaced() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let bytes = Data("broken transaction".utf8)
        try bytes.write(to: fixture.journal)
        let manager = fixture.manager()
        #expect(await manager.restore() == .failed)
        await #expect(throws: (any Error).self) { try await manager.enable(port: 7890) }
        #expect(try Data(contentsOf: fixture.journal) == bytes)
        #expect(fixture.network.commands.isEmpty)
    }

    @Test func authenticatedProxyIsNeverOverwritten() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        fixture.network.web.authenticated = true
        let original = fixture.network.web
        let manager = fixture.manager()
        await #expect(throws: (any Error).self) { try await manager.enable(port: 17890) }
        #expect(fixture.network.web == original)
        #expect(!fixture.network.commands.contains { $0[0].hasPrefix("-set") })
        #expect(!FileManager.default.fileExists(atPath: fixture.journal.path))
    }

    @Test func addedAuthenticationDuringSessionIsPreserved() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let manager = fixture.manager()
        try await manager.enable(port: 17890)
        fixture.network.web.authenticated = true
        let changed = fixture.network.web
        #expect(await manager.restore() == .preservedChanges)
        #expect(fixture.network.web == changed)
    }

    @Test func incompleteProxySnapshotCannotLeadToWrites() async throws {
        let fixture = try Fixture()
        defer { fixture.close() }
        let manager = SystemProxyManager(transactionURL: fixture.journal, runCommand: { path, args in
            if args[0] == "-getwebproxy" { return "Enabled: No\nServer: old.proxy\nPort: 8080\n" }
            return try fixture.network.run(path, args)
        })
        await #expect(throws: (any Error).self) { try await manager.enable(port: 17890) }
        #expect(!fixture.network.commands.contains { $0[0].hasPrefix("-set") })
    }

    private struct Fixture {
        let directory: URL
        let network = FakeNetwork()
        var journal: URL { directory.appendingPathComponent("transaction.json") }
        init() throws {
            directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        }
        @MainActor func manager() -> SystemProxyManager {
            SystemProxyManager(transactionURL: journal, runCommand: network.run)
        }
        func close() { try? FileManager.default.removeItem(at: directory) }
    }

    // Each fixture is accessed only between awaited, serialized manager operations.
    private final class FakeNetwork: @unchecked Sendable {
        struct Proxy: Equatable {
            var enabled: Bool
            var server: String
            var port: Int
            var authenticated = false
            var text: String { "Enabled: \(enabled ? "Yes" : "No")\nServer: \(server)\nPort: \(port)\nAuthenticated Proxy Enabled: \(authenticated ? 1 : 0)\n" }
        }
        static let original = Proxy(enabled: false, server: "old.proxy", port: 8080)
        var web = original
        var secure = original
        var socks = original
        var autoURL = "https://old.invalid/proxy.pac"
        var autoEnabled = true
        var bypass = ["old.internal"]
        var commands: [[String]] = []
        var failNextOff: String?
        var failNextCommand: String?

        func run(_ executable: String, _ args: [String]) throws -> String {
            commands.append(args)
            let command = args[0]
            if command == failNextCommand || (command == failNextOff && args.last == "off") {
                failNextCommand = nil
                failNextOff = nil
                throw WeaveMacError.message("fixture write failure")
            }
            switch command {
            case "-listallnetworkservices": return "An asterisk denotes disabled services\nWi-Fi\n"
            case "-n": return "interface: en0\n"
            case "-listnetworkserviceorder": return "(1) Wi-Fi\n(Hardware Port: Wi-Fi, Device: en0)\n"
            case "-getwebproxy": return web.text
            case "-getsecurewebproxy": return secure.text
            case "-getsocksfirewallproxy": return socks.text
            case "-getautoproxyurl": return "Enabled: \(autoEnabled ? "Yes" : "No")\nURL: \(autoURL)\n"
            case "-getproxybypassdomains": return bypass.joined(separator: "\n")
            case "-setwebproxy": web = .init(enabled: true, server: args[2], port: Int(args[3])!)
            case "-setsecurewebproxy": secure = .init(enabled: true, server: args[2], port: Int(args[3])!)
            case "-setsocksfirewallproxy": socks = .init(enabled: true, server: args[2], port: Int(args[3])!)
            case "-setwebproxystate": web.enabled = args[2] == "on"
            case "-setsecurewebproxystate": secure.enabled = args[2] == "on"
            case "-setsocksfirewallproxystate": socks.enabled = args[2] == "on"
            case "-setautoproxyurl": autoURL = args[2]
            case "-setautoproxystate": autoEnabled = args[2] == "on"
            case "-setproxybypassdomains": bypass = args[2] == "" ? [] : Array(args.dropFirst(2))
            default: throw WeaveMacError.message("Unexpected fixture command")
            }
            return ""
        }
    }
}
