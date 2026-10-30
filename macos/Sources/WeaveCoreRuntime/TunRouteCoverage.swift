import Foundation

package enum TunRouteCoverage {
    package static func isComplete(ipv4: String, ipv6: String, device: String) -> Bool {
        let v4 = destinations(in: ipv4, device: device)
        let v6 = destinations(in: ipv6, device: device)
        let lowerV4 = !v4.isDisjoint(with: ["0/1", "0.0/1", "0.0.0/1", "0.0.0.0/1"])
        let upperV4 = !v4.isDisjoint(with: ["128/1", "128.0/1", "128.0.0/1", "128.0.0.0/1"])
        return (v4.contains("default") || (lowerV4 && upperV4)) &&
            (v6.contains("default") || v6.isSuperset(of: ["::/1", "8000::/1"]))
    }

    private static func destinations(in table: String, device: String) -> Set<String> {
        var interfaceIndex: Int?
        var flagsIndex: Int?
        var result = Set<String>()
        for line in table.split(whereSeparator: \.isNewline) {
            let columns = line.split(whereSeparator: \.isWhitespace).map(String.init)
            if columns.first == "Destination" {
                interfaceIndex = columns.firstIndex(of: "Netif")
                flagsIndex = columns.firstIndex(of: "Flags")
                continue
            }
            guard let interfaceIndex, let flagsIndex,
                  columns.count > max(interfaceIndex, flagsIndex),
                  columns[interfaceIndex] == device,
                  columns[flagsIndex].contains("U"),
                  !columns[flagsIndex].contains("R"), !columns[flagsIndex].contains("B"),
                  let destination = columns.first else { continue }
            result.insert(destination)
        }
        return result
    }
}
