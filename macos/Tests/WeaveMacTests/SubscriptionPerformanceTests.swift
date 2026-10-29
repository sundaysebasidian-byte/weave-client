import Darwin
import Foundation
import Testing
@testable import WeaveMac

@Suite("Subscription performance", .serialized, .enabled(if: ProcessInfo.processInfo.environment["WEAVE_RUN_BENCHMARKS"] == "1"))
struct SubscriptionPerformanceTests {
    @Test(arguments: [5_000, 50_000]) func parseAndSerialize(nodes: Int) throws {
        let input = "proxies:\n" + (0..<nodes).map {
            "- {server: example.invalid, port: 1080, type: socks5, name: 'Node \($0)'}"
        }.joined(separator: "\n")
        let clock = ContinuousClock()
        let start = clock.now
        let document = try ClashProviderDocument(input)
        let parsed = clock.now
        let output = try document.sanitized()
        let finished = clock.now
        #expect(document.names.count == nodes)
        var usage = rusage()
        getrusage(RUSAGE_SELF, &usage)
        let record: [String: Any] = [
            "nodes": nodes, "input_bytes": input.utf8.count, "output_bytes": output.utf8.count,
            "parse_seconds": seconds(start.duration(to: parsed)),
            "serialize_seconds": seconds(parsed.duration(to: finished)),
            "process_peak_rss_bytes": usage.ru_maxrss,
        ]
        print("WEAVE_BENCHMARK " + String(decoding: try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys]), as: UTF8.self))
    }

    private func seconds(_ duration: Duration) -> Double {
        Double(duration.components.seconds) + Double(duration.components.attoseconds) / 1e18
    }
}
