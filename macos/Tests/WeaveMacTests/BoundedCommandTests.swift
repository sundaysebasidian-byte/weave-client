import WeaveCoreRuntime
import Foundation
import Testing
@testable import WeaveMac

@Suite("Bounded child commands")
struct BoundedCommandTests {
    @Test func outputLargerThanPipeCapacityDoesNotDeadlock() throws {
        let text = String(repeating: "x", count: 100_000)
        let result = try BoundedCommand.run(executable: "/usr/bin/printf", arguments: ["%s", text])
        #expect(result == String(text.prefix(65_536)))
    }

    @Test func hungCommandTimesOut() {
        let start = Date()
        #expect(throws: (any Error).self) {
            try BoundedCommand.run(executable: "/bin/sleep", arguments: ["5"], timeout: 0.05)
        }
        #expect(Date().timeIntervalSince(start) < 2)
    }

    @Test func failureDoesNotExposeCommandOutput() {
        do {
            _ = try BoundedCommand.run(executable: "/bin/sh", arguments: ["-c", "printf 'credential-fixture'; exit 1"])
            Issue.record("Expected command failure")
        } catch {
            #expect(!error.localizedDescription.contains("credential-fixture"))
        }
    }
}
