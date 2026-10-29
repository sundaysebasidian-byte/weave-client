import Testing
@testable import WeaveMac

@Suite("TUN route coverage")
struct TunRouteCoverageTests {
    private let header = "Destination Gateway Flags Netif Expire\n"
    private let full4 = "0/1 198.18.0.1 UGSc utun7\n128.0/1 198.18.0.1 UGSc utun7\n"
    private let full6 = "::/1 fe80::1 UGSc utun7\n8000::/1 fe80::1 UGSc utun7\n"

    @Test func requiresBothHalvesInBothFamilies() {
        #expect(valid(full4, full6))
        #expect(!valid("0/1 198.18.0.1 UGSc utun7\n", full6))
        #expect(!valid(full4, "::/1 fe80::1 UGSc utun7\n"))
    }

    @Test func rejectsNarrowRoutesAndSimilarInterfaceNames() {
        #expect(!valid("198.18/16 198.18.0.1 UGSc utun7\n", full6))
        #expect(!valid("default 198.18.0.1 UGSc utun70\n", full6))
        #expect(!valid("default 198.18.0.1 UGRSc utun7\n", full6))
        #expect(!valid("default 198.18.0.1 GSc utun7\n", full6))
    }

    @Test func acceptsExactDefaultRoutesAndLegacyColumns() {
        #expect(valid("default 198.18.0.1 UGSc utun7\n", "default fe80::1 UGSc utun7\n"))
        let legacy = "Destination Gateway Flags Refs Use Netif Expire\ndefault 198.18.0.1 UGSc 0 0 utun7\n"
        #expect(TunRouteCoverage.isComplete(ipv4: legacy, ipv6: header + full6, device: "utun7"))
    }

    private func valid(_ v4: String, _ v6: String) -> Bool {
        TunRouteCoverage.isComplete(ipv4: header + v4, ipv6: header + v6, device: "utun7")
    }
}
