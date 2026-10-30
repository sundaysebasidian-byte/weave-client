import Darwin
import Foundation
import Testing
import WeaveCoreRuntime
@testable import WeaveCoreBroker
@testable import WeaveCoreService

@Suite("Managed core snapshot", .serialized)
struct ManagedCoreInstallationTests {
    @Test func productionEntryRejectsAnOrdinaryProcess() throws {
        try #require(geteuid() != 0)
        #expect(throws: BrokerFailure.unauthorized) {
            try ManagedCoreInstallation.prepare(sourceBundle: URL(fileURLWithPath: "/nonexistent/Weave.app"))
        }
    }

    @Test func verifiedSnapshotIsIndependentAndReusesTheSameInode() throws {
        try CoreInstallationTests().withSignedFixture { app in
            let root = app.deletingLastPathComponent().appendingPathComponent("store")
            let first = try prepare(app, root)
            let original = try Data(contentsOf: first.coreURL)
            var before = stat(), after = stat()
            #expect(lstat(first.coreURL.path, &before) == 0)
            #expect(before.st_mode & 0o777 == 0o700)
            #expect(throws: BrokerFailure.busy) { try prepare(app, root) }
            try first.lease.close()
            let second = try prepare(app, root)
            #expect(first.coreURL == second.coreURL)
            #expect(lstat(second.coreURL.path, &after) == 0)
            #expect(before.st_dev == after.st_dev && before.st_ino == after.st_ino)
            try Data("changed source".utf8).write(to: app.appendingPathComponent("Contents/Resources/mihomo"))
            #expect(try Data(contentsOf: second.coreURL) == original)
            _ = try BoundedCommand.run(executable: second.coreURL.path, arguments: [])
            try second.lease.close()
            #expect(throws: InstallationError.invalidSignature) { try prepare(app, root) }
            #expect(try Data(contentsOf: first.coreURL) == original)
            try expectNoStage(root)
        }
    }

    @Test(arguments: ["content", "symlink", "hardlink", "mode", "acl"])
    func corruptPublishedVersionIsPreservedAndRejected(_ kind: String) throws {
        try CoreInstallationTests().withSignedFixture { app in
            let root = app.deletingLastPathComponent().appendingPathComponent("store")
            let installed = try prepare(app, root)
            try installed.lease.close()
            let core = installed.coreURL
            let sentinel = root.appendingPathComponent("keep")
            try Data("unknown".utf8).write(to: sentinel)
            switch kind {
            case "content": try Data("corrupt".utf8).write(to: core)
            case "symlink":
                try FileManager.default.removeItem(at: core)
                try FileManager.default.createSymbolicLink(at: core, withDestinationURL: sentinel)
            case "hardlink": try FileManager.default.linkItem(at: core, to: root.appendingPathComponent("linked-core"))
            case "mode": #expect(chmod(core.path, 0o770) == 0)
            default: _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow write", core.path])
            }
            var before = stat(), after = stat()
            #expect(lstat(core.path, &before) == 0)
            let contents = try Data(contentsOf: core)
            #expect(throws: (any Error).self) { try prepare(app, root) }
            #expect(lstat(core.path, &after) == 0)
            #expect(before.st_ino == after.st_ino && before.st_mode == after.st_mode)
            #expect(try Data(contentsOf: core) == contents)
            #expect(try String(contentsOf: sentinel, encoding: .utf8) == "unknown")
            try expectNoStage(root)
        }
    }

    @Test(arguments: ["symlink", "hardlink", "fifo", "bytes", "depth", "entries"])
    func untrustedSourceIsBoundedAndNeverPublished(_ kind: String) throws {
        try CoreInstallationTests().withSignedFixture { app in
            let root = app.deletingLastPathComponent().appendingPathComponent("store")
            let extra = app.appendingPathComponent("extra")
            switch kind {
            case "symlink": try FileManager.default.createSymbolicLink(at: extra, withDestinationURL: app)
            case "hardlink": try FileManager.default.linkItem(at: app.appendingPathComponent("Contents/Resources/mihomo"), to: extra)
            case "fifo": #expect(mkfifo(extra.path, 0o600) == 0)
            case "bytes":
                let fd = open(extra.path, O_CREAT | O_EXCL | O_WRONLY, 0o600)
                try #require(fd >= 0)
                defer { close(fd) }
                #expect(ftruncate(fd, 513 * 1024 * 1024) == 0)
            case "depth":
                var leaf = app
                for _ in 0..<33 {
                    leaf.appendPathComponent("child")
                    try FileManager.default.createDirectory(at: leaf, withIntermediateDirectories: false)
                }
            default:
                try FileManager.default.createDirectory(at: extra, withIntermediateDirectories: false)
                for index in 0..<2048 { try Data().write(to: extra.appendingPathComponent("\(index)")) }
            }
            #expect(throws: InstallationError.unsafePath) { try prepare(app, root) }
            try expectNoStage(root)
            #expect(try FileManager.default.contentsOfDirectory(atPath: root.appendingPathComponent("versions").path).isEmpty)
        }
    }

    @Test(arguments: ["store-mode", "store-acl", "ancestor-mode", "ancestor-link", "versions-link", "runtime-mode"])
    func unsafeDestinationIsRejectedWithoutRepairingIt(_ kind: String) throws {
        try CoreInstallationTests().withSignedFixture { app in
            let parent = app.deletingLastPathComponent().appendingPathComponent("destination")
            let root = parent.appendingPathComponent("store")
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
            let sentinel = root.appendingPathComponent("keep")
            try Data("unknown".utf8).write(to: sentinel)
            var destination = root
            switch kind {
            case "store-mode": #expect(chmod(root.path, 0o750) == 0)
            case "store-acl": _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow delete_child", root.path])
            case "ancestor-mode": #expect(chmod(parent.path, 0o770) == 0)
            case "ancestor-link":
                let link = app.deletingLastPathComponent().appendingPathComponent("link")
                try FileManager.default.createSymbolicLink(at: link, withDestinationURL: parent)
                destination = link.appendingPathComponent("store")
            case "versions-link": try FileManager.default.createSymbolicLink(at: root.appendingPathComponent("versions"), withDestinationURL: app)
            default: try FileManager.default.createDirectory(at: root.appendingPathComponent("runtime"), withIntermediateDirectories: false, attributes: [.posixPermissions: 0o755])
            }
            var before = stat(), after = stat()
            #expect(lstat(root.path, &before) == 0)
            #expect(throws: (any Error).self) { try prepare(app, destination) }
            #expect(lstat(root.path, &after) == 0)
            #expect(before.st_ino == after.st_ino && before.st_mode == after.st_mode)
            #expect(try String(contentsOf: sentinel, encoding: .utf8) == "unknown")
            try expectNoStage(root)
        }
    }

    @Test func actualSignedCoreRetainsInstallationLeaseAfterParentHandlesClose() async throws {
        let macOS = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let parent = macOS.appendingPathComponent(".build/managed-core-fixtures/\(UUID())")
        try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let root = parent.appendingPathComponent("store")
        var installed: ManagedCoreInstallation?
        try CoreInstallationTests().withSignedFixture(coreExecutable: macOS.appendingPathComponent("Resources/mihomo")) { app in
            installed = try prepare(app, root)
        }
        // The original source bundle no longer exists when the real core is executed.
        let installation = try #require(installed)
        let service = try ServiceMachineLease(root: installation.runtimeRoot)
        var runtime = try CoreRuntime.prepare(provider: "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]",
                                              selectedNodeName: "fixture", enableTun: false, root: installation.runtimeRoot)
        runtime.additionalInheritedLeases = [installation.lease, service.handle]
        let checker = try CoreProcess(executable: installation.coreURL, runtime: runtime, mode: .checkConfiguration, environment: .isolated, onExit: {})
        do { #expect(try await checker.waitForExit(timeout: .seconds(10)) == 0) }
        catch { #expect(await checker.stop()); throw error }
        let child = try CoreProcess(executable: installation.coreURL, runtime: runtime, environment: .isolated, onExit: {})
        do {
            try await CoreRuntime.waitUntilReady(runtime: runtime, pid: child.processIdentifier, requireTun: false)
            try installation.lease.close()
            try service.close()
            try runtime.directory.lease.close()
            #expect(throws: BrokerFailure.busy) { try ServiceMachineLease(root: root) }
            #expect(throws: BrokerFailure.busy) { try ServiceMachineLease(root: installation.runtimeRoot) }
            #expect(try RuntimeDirectory.reclaimOrphans(root: installation.runtimeRoot) == 0)
            try #require(await child.stop())
            let next = try ServiceMachineLease(root: root)
            #expect(try RuntimeDirectory.reclaimOrphans(root: installation.runtimeRoot) == 1)
            try next.close()
        } catch {
            #expect(await child.stop())
            throw error
        }
        try FileManager.default.removeItem(at: parent)
    }

    @Test func serviceFactoryAlwaysPassesTheInstallationLeaseToItsCore() async throws {
        let macOS = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let parent = macOS.appendingPathComponent(".build/managed-core-fixtures/\(UUID())")
        try FileManager.default.createDirectory(at: parent, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let root = parent.appendingPathComponent("store")
        var installed: ManagedCoreInstallation?
        try CoreInstallationTests().withSignedFixture(coreExecutable: macOS.appendingPathComponent("Resources/mihomo")) { app in
            installed = try prepare(app, root)
        }
        let installation = try #require(installed)
        let service = try CoreService(installation: installation, enableTun: false)
        let session = service.makeSession(for: BrokerPeer(userID: getuid(), sessionID: 1, processID: getpid()))
        do {
            let response = await session.handle(.start(provider: "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]", node: "fixture"))
            guard case .state(.running, _) = response else {
                Issue.record("Managed service did not start: \(response)")
                #expect(await service.shutdown())
                return
            }
            try installation.lease.close()
            #expect(throws: BrokerFailure.busy) { try ServiceMachineLease(root: root) }
            #expect(await session.handle(.stop) == .state(.stopped, mixedPort: nil))
            let next = try ServiceMachineLease(root: root)
            try next.close()
            try expectNoStage(root)
        } catch { #expect(await service.shutdown()); throw error }
        try #require(await service.shutdown())
        try FileManager.default.removeItem(at: parent)
    }

    private func prepare(_ app: URL, _ root: URL) throws -> ManagedCoreInstallation {
        try ManagedCoreInstallation.prepare(sourceBundle: app, storeRoot: root, owner: geteuid(),
            appRequirement: "identifier \"\(BrokerIdentity.appIdentifier)\"",
            coreRequirement: "identifier \"\(CoreIntegrityManifest.identifier)\"")
    }

    private func expectNoStage(_ root: URL) throws {
        #expect(try !FileManager.default.contentsOfDirectory(atPath: root.path).contains { $0.hasPrefix(".staging-") })
    }
}
