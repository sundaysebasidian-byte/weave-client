import CryptoKit
import Darwin
import Foundation
import Testing
import WeaveCoreRuntime
@testable import WeaveCoreBroker
@testable import WeaveCoreService

@Suite("Core installation integrity", .serialized)
struct CoreInstallationTests {
    private let appID = BrokerIdentity.appIdentifier
    private let coreID = CoreIntegrityManifest.identifier

    @Test func actualRootOwnedSystemExecutablePassesPathPolicy() throws {
        try ProtectedInstallationPath.validateTree(URL(fileURLWithPath: "/usr/bin/true"))
    }

    @Test func sourceAndSignedDigestsAreDifferentAndTheSignedBytesAreVerified() throws {
        try withSignedFixture { app in
            let manifest = try validate(app)
            #expect(manifest.sourceCoreSHA256 != manifest.signedCoreSHA256)
            try ProtectedInstallationPath.validateTree(app, owner: getuid())
            #expect(throws: InstallationError.unsafePath) { try CoreInstallation.validateManagedBundle(app) }
        }
    }

    @Test func adHocCannotSatisfyProductionDeveloperIDRequirements() throws {
        try withSignedFixture { app in
            let appRequirement = try BrokerIdentity.developerIDRequirement(team: "AAAAAAAAAA", identifier: appID)
            let coreRequirement = try BrokerIdentity.developerIDRequirement(team: "AAAAAAAAAA", identifier: coreID)
            #expect(throws: InstallationError.invalidSignature) {
                try CoreInstallation.validateContents(app, appRequirement: appRequirement, coreRequirement: coreRequirement)
            }
        }
    }

    @Test(arguments: ["unsealed-manifest", "unsealed-core", "wrong-core-identity", "replaced-signed-core", "changed-lock", "missing-manifest", "non-executable-core"])
    func tamperingAndInconsistentSignedPackagesAreRejected(_ kind: String) throws {
        try withSignedFixture { app in
            let resources = app.appendingPathComponent("Contents/Resources")
            let core = resources.appendingPathComponent("mihomo")
            switch kind {
            case "unsealed-manifest":
                try Data("{}".utf8).write(to: resources.appendingPathComponent("core-integrity.json"))
            case "unsealed-core":
                let handle = try FileHandle(forWritingTo: core)
                try handle.seekToEnd(); try handle.write(contentsOf: Data([1])); try handle.close()
            case "wrong-core-identity":
                try sign(core, identifier: "io.weave.wrong-core")
                try sign(app, identifier: appID)
            case "replaced-signed-core":
                try FileManager.default.removeItem(at: core)
                try FileManager.default.copyItem(atPath: "/usr/bin/false", toPath: core.path)
                try sign(core, identifier: coreID)
                try sign(app, identifier: appID)
            case "changed-lock":
                try Data("mihomo.darwin-arm64.sha256=wrong\n".utf8).write(to: resources.appendingPathComponent("core-lock.properties"))
                try sign(app, identifier: appID)
            case "non-executable-core": #expect(chmod(core.path, 0o600) == 0)
            default:
                try FileManager.default.removeItem(at: resources.appendingPathComponent("core-integrity.json"))
                try sign(app, identifier: appID)
            }
            let expected: InstallationError
            switch kind {
            case "replaced-signed-core": expected = .changedCore
            case "changed-lock": expected = .invalidManifest
            case "missing-manifest", "non-executable-core": expected = .unsafePath
            default: expected = .invalidSignature
            }
            #expect(throws: expected) { try validate(app) }
        }
    }

    @Test(arguments: ["schema", "architecture", "coreIdentifier", "sourceCoreSHA256", "signedCoreSHA256", "coreCommit", "oversize"])
    func invalidManifestFieldsAreRejected(_ field: String) throws {
        var values: [String: Any] = ["schema": 1, "architecture": "arm64", "coreIdentifier": coreID,
            "sourceCoreSHA256": String(repeating: "a", count: 64), "signedCoreSHA256": String(repeating: "b", count: 64),
            "coreCommit": String(repeating: "c", count: 40)]
        values[field] = field == "schema" ? 2 : "invalid"
        if field == "oversize" { values[field] = String(repeating: "x", count: 4096) }
        let data = try JSONSerialization.data(withJSONObject: values)
        #expect(throws: (any Error).self) { try CoreIntegrityManifest(data: data) }
    }

    @Test(arguments: ["writable-file", "writable-parent", "symlink", "hardlink", "fifo", "write-acl", "inherit-delete-acl"])
    func unsafeFilesystemEntriesAreRejected(_ kind: String) throws {
        try withDirectory { root in
            let file = root.appendingPathComponent("data")
            try Data("fixture".utf8).write(to: file)
            try ProtectedInstallationPath.validateTree(root, owner: getuid())
            switch kind {
            case "writable-file": #expect(chmod(file.path, 0o666) == 0)
            case "writable-parent": #expect(chmod(root.path, 0o770) == 0)
            case "symlink": try FileManager.default.createSymbolicLink(atPath: root.appendingPathComponent("link").path, withDestinationPath: file.path)
            case "hardlink": try FileManager.default.linkItem(at: file, to: root.appendingPathComponent("link"))
            case "fifo": #expect(mkfifo(root.appendingPathComponent("fifo").path, 0o600) == 0)
            case "write-acl": _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow write", file.path])
            default: _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow delete_child,directory_inherit,only_inherit", root.path])
            }
            #expect(throws: InstallationError.unsafePath) { try ProtectedInstallationPath.validateTree(root, owner: getuid()) }
        }
    }

    @Test func readOnlyACLIsAcceptedButAnAncestorLinkIsRejected() throws {
        try withDirectory { root in
            let data = root.appendingPathComponent("data")
            try Data("fixture".utf8).write(to: data)
            _ = try BoundedCommand.run(executable: "/bin/chmod", arguments: ["+a", "everyone allow read", data.path])
            try ProtectedInstallationPath.validateTree(root, owner: getuid())
            let link = root.appendingPathComponent("ancestor")
            try FileManager.default.createSymbolicLink(at: link, withDestinationURL: root)
            #expect(throws: InstallationError.unsafePath) {
                try ProtectedInstallationPath.validateTree(link.appendingPathComponent("data"), owner: getuid())
            }
        }
    }

    @Test(arguments: ["bytes", "depth", "entries"])
    func oversizedInstallationTreesAreRejectedWithoutReadingTheirContent(_ limit: String) throws {
        try withDirectory { root in
            if limit == "bytes" {
                let descriptor = open(root.appendingPathComponent("sparse").path, O_CREAT | O_EXCL | O_WRONLY, 0o600)
                #expect(descriptor >= 0)
                guard descriptor >= 0 else { return }
                defer { close(descriptor) }
                #expect(ftruncate(descriptor, 513 * 1024 * 1024) == 0)
            } else if limit == "depth" {
                var leaf = root
                for _ in 0..<33 {
                    leaf.appendPathComponent("child")
                    try FileManager.default.createDirectory(at: leaf, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
                }
            } else {
                for index in 0..<2048 { try Data().write(to: root.appendingPathComponent("\(index)")) }
            }
            #expect(throws: InstallationError.unsafePath) { try ProtectedInstallationPath.validateTree(root, owner: getuid()) }
        }
    }

    @Test(.enabled(if: ProcessInfo.processInfo.environment["WEAVE_TEST_PACKAGED_BUNDLE"] != nil))
    func packagedPreviewPassesTheNativeContentVerifier() throws {
        let path = try #require(ProcessInfo.processInfo.environment["WEAVE_TEST_PACKAGED_BUNDLE"])
        let manifest = try validate(URL(fileURLWithPath: path))
        #expect(manifest.sourceCoreSHA256 == "0cf93bb94fdb322e91120b8c6b67e35997a0962ba7cea7fe1f988a1c7060ee1a")
        let version = try BoundedCommand.run(executable: path + "/Contents/Resources/mihomo", arguments: ["-v"])
        #expect(version.contains("Mihomo"))
        try withDirectory { root in
            let snapshot = try ManagedCoreInstallation.prepare(sourceBundle: URL(fileURLWithPath: path),
                storeRoot: root.appendingPathComponent("store"), owner: geteuid(),
                appRequirement: "identifier \"\(appID)\"", coreRequirement: "identifier \"\(coreID)\"")
            #expect(try BoundedCommand.run(executable: snapshot.coreURL.path, arguments: ["-v"]) == version)
            try snapshot.lease.close()
        }
        print("Verified packaged core: \(version.trimmingCharacters(in: .whitespacesAndNewlines))")
    }

    private func validate(_ app: URL) throws -> CoreIntegrityManifest {
        try CoreInstallation.validateContents(app, appRequirement: "identifier \"\(appID)\"", coreRequirement: "identifier \"\(coreID)\"")
    }

    private func sign(_ url: URL, identifier: String) throws {
        _ = try BoundedCommand.run(executable: "/usr/bin/codesign", arguments: ["--force", "--sign", "-", "--identifier", identifier, url.path])
    }

    func withSignedFixture(coreExecutable: URL = URL(fileURLWithPath: "/usr/bin/true"), _ body: (URL) throws -> Void) throws {
        try withDirectory { root in
            let app = root.appendingPathComponent("Weave.app")
            let resources = app.appendingPathComponent("Contents/Resources")
            let macOS = app.appendingPathComponent("Contents/MacOS")
            try FileManager.default.createDirectory(at: resources, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
            try FileManager.default.createDirectory(at: macOS, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
            let plist: [String: Any] = ["CFBundleIdentifier": appID, "CFBundleExecutable": "WeaveMac", "CFBundlePackageType": "APPL", "CFBundleVersion": "1"]
            try PropertyListSerialization.data(fromPropertyList: plist, format: .xml, options: 0).write(to: app.appendingPathComponent("Contents/Info.plist"))
            try FileManager.default.copyItem(atPath: "/usr/bin/true", toPath: macOS.appendingPathComponent("WeaveMac").path)
            let core = resources.appendingPathComponent("mihomo")
            try FileManager.default.copyItem(at: coreExecutable, to: core)
            let sourceHash = try hash(core)
            try sign(core, identifier: coreID)
            let commit = String(repeating: "a", count: 40)
            let values: [String: Any] = ["schema": 1, "architecture": "arm64", "coreIdentifier": coreID,
                "sourceCoreSHA256": sourceHash, "signedCoreSHA256": try hash(core), "coreCommit": commit]
            try JSONSerialization.data(withJSONObject: values).write(to: resources.appendingPathComponent("core-integrity.json"))
            try "mihomo.darwin-arm64.sha256=\(sourceHash)\nmihomo.commit=\(commit)\n".write(to: resources.appendingPathComponent("core-lock.properties"), atomically: true, encoding: .utf8)
            try sign(app, identifier: appID)
            try body(app)
        }
    }

    private func hash(_ url: URL) throws -> String { SHA256.hash(data: try Data(contentsOf: url)).map { String(format: "%02x", $0) }.joined() }

    private func withDirectory(_ body: (URL) throws -> Void) throws {
        let macOS = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let root = macOS.appendingPathComponent(".build/core-integrity-fixtures/\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        defer {
            do { try FileManager.default.removeItem(at: root) }
            catch { Issue.record(error, "Could not remove the owned installation fixture") }
        }
        try body(root)
    }
}
