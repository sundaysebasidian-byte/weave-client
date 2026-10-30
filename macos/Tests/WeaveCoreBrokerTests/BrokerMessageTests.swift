import Foundation
import Security
import Testing
@testable import WeaveCoreBroker

@Suite("Mac broker wire boundary")
struct BrokerMessageTests {
    @Test func roundTripsOnlyStructuredRequestsAndReplies() throws {
        let requests: [BrokerRequest] = [.status, .stop, .resolve(host: "xn--bcher-kva.example"), .resolve(host: "::1"),
            .start(provider: "proxies: [{name: 東京, type: socks5, server: example.invalid, port: 9}]", node: "東京"),
            .start(provider: "proxies: []", node: nil)]
        for request in requests { #expect(try BrokerMessage.decodeRequest(BrokerMessage.encode(request)) == request) }
        let replies: [BrokerReply] = [.state(.stopped, mixedPort: nil), .state(.running, mixedPort: 65535),
            .addresses(["8.8.8.8", "2001:4860:4860::8888"]), .failure(.invalidProfile)]
        for reply in replies { #expect(try BrokerMessage.decodeReply(BrokerMessage.encode(reply)) == reply) }
    }

    @Test func truncatedUnknownVersionAndTrailingDataAreRejected() throws {
        let original = try BrokerMessage.encode(BrokerRequest.start(provider: "proxies: []", node: "Test"))
        for size in 0..<original.count {
            #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeRequest(original.prefix(size)) }
        }
        for position in [0, 4, 5] {
            var frame = original
            frame[position] = 0xff
            #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeRequest(frame) }
        }
        #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeRequest(original + Data([0])) }
        #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeReply(original) }
        var slice = Data([0]) + original
        slice.removeFirst()
        #expect(try BrokerMessage.decodeRequest(slice) == .start(provider: "proxies: []", node: "Test"))
    }

    @Test func sizeBudgetsAndInvalidUTF8CannotBecomeAllocationsOrPartialProfiles() throws {
        let maximum = String(repeating: "a", count: BrokerMessage.maxProviderBytes)
        #expect(try BrokerMessage.decodeRequest(BrokerMessage.encode(.start(provider: maximum, node: nil))) == .start(provider: maximum, node: nil))
        #expect(throws: BrokerFailure.self) { try BrokerMessage.encode(BrokerRequest.start(provider: maximum + "a", node: nil)) }
        #expect(throws: BrokerFailure.self) { try BrokerMessage.encode(BrokerRequest.start(provider: "x", node: String(repeating: "界", count: 107))) }
        var overflow = try BrokerMessage.encode(BrokerRequest.start(provider: "a", node: nil))
        overflow.replaceSubrange(6..<10, with: [255, 255, 255, 255])
        #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeRequest(overflow) }
        var invalidUTF8 = try BrokerMessage.encode(BrokerRequest.start(provider: "a", node: nil))
        invalidUTF8[10] = 0xff
        #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeRequest(invalidUTF8) }
        #expect(throws: BrokerFailure.self) { try BrokerMessage.decodeRequest(Data(repeating: 0, count: BrokerMessage.maxFrameBytes + 1)) }
    }

    @Test func rejectsURLsControlCharactersAndMalformedResponses() throws {
        for host in ["", "https://example.com", "example.com/path", "a..b", "-name.test", "name-.test", "x\n", "fe80::1%lo0", String(repeating: "a", count: 64) + ".test"] {
            #expect(throws: BrokerFailure.self) { try BrokerMessage.encode(BrokerRequest.resolve(host: host)) }
        }
        for reply: BrokerReply in [.state(.running, mixedPort: nil), .state(.running, mixedPort: 0), .state(.stopped, mixedPort: 9),
                                   .addresses([]), .addresses(["evil.invalid"]), .addresses(Array(repeating: "8.8.8.8", count: 65))] {
            #expect(throws: BrokerFailure.self) { try BrokerMessage.encode(reply) }
        }
        #expect(throws: BrokerFailure.self) { try BrokerMessage.encode(BrokerRequest.start(provider: "a\0", node: nil)) }
        #expect(throws: BrokerFailure.self) { try BrokerMessage.encode(BrokerRequest.start(provider: "a", node: "a\n")) }
    }

    @Test func productionRequirementIsClosedAndParsesWithSecurityFramework() throws {
        let requirement = try BrokerIdentity.developerIDRequirement(team: "0123456789", identifier: BrokerIdentity.appIdentifier)
        #expect(requirement.contains("anchor apple generic"))
        #expect(requirement.contains("get-task-allow"))
        #expect(throws: BrokerFailure.self) { try BrokerIdentity.developerIDRequirement(team: "\" or true", identifier: "app") }
        #expect(throws: BrokerFailure.self) { try BrokerIdentity.developerIDRequirement(team: "0123456789", identifier: "app\" or true") }
        #expect(throws: BrokerFailure.self) { try BrokerIdentity.validate("malformed signing requirement") }
        // Test runners are ad-hoc; production must not turn their missing team into a wildcard.
        #expect(throws: BrokerFailure.self) { try BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.serviceName) }
    }
}
