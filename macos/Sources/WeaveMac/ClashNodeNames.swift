import WeaveCoreRuntime
import Foundation

enum ClashProviderSanitizer {
    static func sanitize(_ payload: String) throws -> String {
        try ClashProviderDocument(payload).sanitized()
    }
}

enum ClashNodeNames {
    static func parse(_ payload: String) -> [String] {
        (try? ClashProviderDocument(payload).names) ?? []
    }

    static func display(_ raw: String) -> String {
        let original = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !original.isEmpty else { return "未命名节点" }
        let escapedPrefix = #"^(?:\\u[0-9A-Za-z]{4,12}|\\U[0-9A-Za-z]{8,12})+"#
        let escaped = original.replacingOccurrences(
            of: escapedPrefix,
            with: "",
            options: .regularExpression,
        )
        if escaped != original {
            return display(escaped)
        }
        var scalars = original.unicodeScalars[...]
        var removed = false
        while let first = scalars.first, isLeadingDecoration(first.value) {
            scalars = scalars.dropFirst()
            removed = true
        }
        let value = String(scalars).trimmingCharacters(
            in: removed
                ? CharacterSet.whitespaces.union(CharacterSet(charactersIn: "·|-_:："))
                : .whitespaces
        )
        return value.isEmpty ? original : value
    }

    private static func isLeadingDecoration(_ value: UInt32) -> Bool {
        (0x1F000...0x1FAFF).contains(value) ||
            (0x2300...0x27FF).contains(value) ||
            (0xE0020...0xE007F).contains(value) ||
            value == 0x200D ||
            value == 0xFE0F ||
            value == 0x20E3
    }
}
