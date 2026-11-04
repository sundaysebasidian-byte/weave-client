import Darwin
import Foundation
import Testing
@testable import WeaveCoreBroker
@testable import WeaveCoreService

@Suite("Production host boundary", .serialized)
struct CoreServiceHostTests {
    @Test func launchdContextRequiresRootAndItsOwnProcessGroup() throws {
        try CoreHostEnvironment.validate(userID: 0, processID: 100, parentID: 1, groupID: 100)
        for (uid, pid, parent, group): (uid_t, pid_t, pid_t, pid_t) in [
            (501, 100, 1, 100), (0, 100, 2, 100), (0, 100, 1, 99), (0, 1, 1, 1),
        ] {
            #expect(throws: BrokerFailure.unauthorized) {
                try CoreHostEnvironment.validate(userID: uid, processID: pid, parentID: parent, groupID: group)
            }
        }
        #expect(throws: BrokerFailure.unauthorized) { try CoreHostEnvironment.validateCurrentProcess() }
    }

    @Test func sourceComesOnlyFromTheFixedHostBundleLayout() throws {
        let path = URL(fileURLWithPath: "/Applications/Weave.app/Contents/MacOS/WeaveCoreServiceHost")
        #expect(try CoreHostBundle.sourceBundle(executable: path).path == "/Applications/Weave.app")
        for value in ["/tmp/WeaveCoreServiceHost", "/Applications/Weave.app/Contents/Resources/WeaveCoreServiceHost",
                      "/Applications/Weave.app/Contents/MacOS/WeaveMac", "/Applications/Other/Contents/MacOS/WeaveCoreServiceHost"] {
            #expect(throws: InstallationError.unsafePath) { try CoreHostBundle.sourceBundle(executable: URL(fileURLWithPath: value)) }
        }
        #expect(throws: InstallationError.unsafePath) {
            try CoreHostBundle.sourceBundle(executable: URL(string: "https://example.invalid/Weave.app/Contents/MacOS/WeaveCoreServiceHost")!)
        }
    }

    @Test(arguments: ["valid", "program", "service", "identity", "abandon", "keepalive", "timeout", "environment", "arguments", "oversize"])
    func bundledPolicyCannotAlterPrivilegeOrProcessOwnership(_ field: String) throws {
        let source = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Resources/" + BrokerIdentity.serviceName + ".plist")
        try CoreInstallationTests().withSignedFixture { app in
            var values = try #require(PropertyListSerialization.propertyList(from: Data(contentsOf: source), format: nil) as? [String: Any])
            switch field {
            case "program": values["BundleProgram"] = "/tmp/other"
            case "service": values["MachServices"] = ["other.service": true]
            case "identity": values["UserName"] = "nobody"
            case "abandon": values["AbandonProcessGroup"] = true
            case "keepalive": values["KeepAlive"] = true
            case "timeout": values["ExitTimeOut"] = 0
            case "environment": values["EnvironmentVariables"] = ["DYLD_INSERT_LIBRARIES": "/tmp/library"]
            case "arguments": values["ProgramArguments"] = ["--core", "/tmp/other"]
            case "oversize": values["extra"] = String(repeating: "x", count: 16_384)
            default: break
            }
            let file = app.appendingPathComponent(CoreHostBundle.plist)
            try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
            try PropertyListSerialization.data(fromPropertyList: values, format: .xml, options: 0).write(to: file)
            if field == "valid" { try CoreHostBundle.validate(app) }
            else { #expect(throws: (any Error).self) { try CoreHostBundle.validate(app) } }
        }
    }

    @Test func ordinaryEntryCannotPrepareOrRunAProductionHost() async throws {
        try #require(geteuid() != 0)
        await #expect(throws: BrokerFailure.unauthorized) { try await CoreServiceHost.run() }
    }
}
