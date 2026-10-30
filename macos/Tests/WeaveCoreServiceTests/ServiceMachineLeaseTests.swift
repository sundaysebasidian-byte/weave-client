import Darwin
import Foundation
import Testing
import WeaveCoreRuntime
@testable import WeaveCoreBroker
@testable import WeaveCoreService

@Suite("Cross-process service lease", .serialized)
struct ServiceMachineLeaseTests {
    @Test func closingParentDescriptorsLeavesBothLeasesWithTheActualCore() async throws {
        let root = temporaryRoot()
        let lease = try ServiceMachineLease(root: root)
        var runtime = try CoreRuntime.prepare(provider: provider, selectedNodeName: "fixture", enableTun: false, root: root)
        runtime.additionalInheritedLeases = [lease.handle]
        let unrelatedPath = root.appendingPathComponent("unrelated")
        let unrelated = open(unrelatedPath.path, O_CREAT | O_RDWR, 0o600)
        try #require(unrelated >= 0)
        let unrelatedHandle = FileHandle(fileDescriptor: unrelated, closeOnDealloc: true)
        #expect(flock(unrelated, LOCK_EX | LOCK_NB) == 0)
        let child = try CoreProcess(executable: coreURL, runtime: runtime, environment: .isolated, onExit: {})
        do {
            try await CoreRuntime.waitUntilReady(runtime: runtime, pid: child.processIdentifier, requireTun: false)
            try lease.close()
            try runtime.directory.lease.close()
            try unrelatedHandle.close()
            // Only the two explicit leases survive exec. An unrelated descriptor without
            // FD_CLOEXEC must not leak into the core and hold its lock indefinitely.
            let probe = open(unrelatedPath.path, O_RDWR)
            try #require(probe >= 0)
            defer { Darwin.close(probe) }
            #expect(flock(probe, LOCK_EX | LOCK_NB) == 0)
            #expect(throws: BrokerFailure.busy) { try ServiceMachineLease(root: root) }
            #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 0)
            #expect(await child.stop())
            #expect(!child.isRunning)
            let next = try ServiceMachineLease(root: root)
            #expect(try RuntimeDirectory.reclaimOrphans(root: root) == 1)
            #expect(try Data(contentsOf: root.appendingPathComponent(".service-lease")).isEmpty)
            try next.close()
        } catch {
            if await child.stop() { try? FileManager.default.removeItem(at: root) }
            throw error
        }
        try FileManager.default.removeItem(at: root)
    }

    @Test func releaseReusesTheSameInodeAndRejectsASecondOpen() throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let first = try ServiceMachineLease(root: root)
        var before = stat(), after = stat()
        #expect(fstat(first.handle.fileDescriptor, &before) == 0)
        #expect(throws: BrokerFailure.busy) { try ServiceMachineLease(root: root) }
        try first.close()
        let second = try ServiceMachineLease(root: root)
        #expect(fstat(second.handle.fileDescriptor, &after) == 0)
        #expect(before.st_dev == after.st_dev && before.st_ino == after.st_ino)
        try second.close()
    }

    @Test(arguments: ["directory-mode", "file-mode", "nonempty", "symlink", "hardlink", "fifo", "directory-acl", "file-acl"])
    func unsafeLeaseLocationsAreRejectedWithoutChangingTheirContent(_ kind: String) throws {
        let root = temporaryRoot()
        defer { try? FileManager.default.removeItem(at: root) }
        let lease = try ServiceMachineLease(root: root)
        try lease.close()
        let path = root.appendingPathComponent(".service-lease")
        switch kind {
        case "directory-mode": #expect(chmod(root.path, 0o750) == 0)
        case "file-mode": #expect(chmod(path.path, 0o640) == 0)
        case "nonempty": try Data("unknown".utf8).write(to: path)
        case "symlink":
            let target = root.appendingPathComponent("target")
            try Data("keep".utf8).write(to: target)
            try FileManager.default.removeItem(at: path)
            try FileManager.default.createSymbolicLink(at: path, withDestinationURL: target)
        case "hardlink": try FileManager.default.linkItem(at: path, to: root.appendingPathComponent("link"))
        case "fifo":
            try FileManager.default.removeItem(at: path)
            #expect(mkfifo(path.path, 0o600) == 0)
        case "directory-acl": _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow delete_child", root.path])
        default: _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow write", path.path])
        }
        #expect(throws: (any Error).self) { try ServiceMachineLease(root: root) }
        if kind == "nonempty" { #expect(try String(contentsOf: path, encoding: .utf8) == "unknown") }
        if kind == "symlink" { #expect(try String(contentsOf: root.appendingPathComponent("target"), encoding: .utf8) == "keep") }
    }

    private func temporaryRoot() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent("WeaveMachineLease-\(UUID())") }
    private var provider: String { "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]" }
    private var coreURL: URL {
        URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/mihomo")
    }
}
