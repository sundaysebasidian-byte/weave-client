import Foundation

enum ClashNodeNames {
    static func parse(_ payload: String) -> [String] {
        var inProxies = false
        var result: [String] = []

        for rawLine in payload.components(separatedBy: .newlines) {
            let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
            if !inProxies {
                guard trimmed.hasPrefix("proxies:") else { continue }
                inProxies = true
                let remainder = String(trimmed.dropFirst("proxies:".count))
                result.append(contentsOf: flowNames(in: remainder))
                continue
            }

            if !rawLine.isEmpty,
               rawLine.first?.isWhitespace == false,
               trimmed.range(of: #"^[A-Za-z0-9_-]+\s*:"#,
                             options: .regularExpression) != nil {
                break
            }
            if let value = blockName(in: trimmed) {
                result.append(value)
            } else if trimmed.hasPrefix("- {") || trimmed.hasPrefix("{") {
                result.append(contentsOf: flowNames(in: trimmed))
            }
        }

        var seen = Set<String>()
        return result.filter {
            !$0.isEmpty && $0.utf8.count <= 320 && seen.insert($0).inserted
        }
    }

    static func display(_ raw: String) -> String {
        let original = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !original.isEmpty else { return "未命名节点" }
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

    private static func blockName(in line: String) -> String? {
        guard line.hasPrefix("-") else { return nil }
        let item = line.dropFirst().trimmingCharacters(in: .whitespaces)
        guard item.hasPrefix("name:") else { return nil }
        return scalar(from: String(item.dropFirst("name:".count)))
    }

    private static func flowNames(in text: String) -> [String] {
        guard let expression = try? NSRegularExpression(
            pattern: #"(?:^|[\{,])\s*name\s*:\s*"#
        ) else { return [] }
        let range = NSRange(text.startIndex..., in: text)
        return expression.matches(in: text, range: range).compactMap { match in
            guard let start = Range(match.range, in: text)?.upperBound else { return nil }
            return scalar(from: String(text[start...]), stopAtFlowDelimiter: true)
        }
    }

    private static func scalar(from input: String, stopAtFlowDelimiter: Bool = false) -> String? {
        let text = input.trimmingCharacters(in: .whitespaces)
        guard let first = text.first else { return nil }
        if first == "\"" {
            var escaped = false
            for index in text.indices.dropFirst() {
                let character = text[index]
                if character == "\"" && !escaped {
                    let literal = String(text[...index])
                    if let data = "[\(literal)]".data(using: .utf8),
                       let array = try? JSONSerialization.jsonObject(with: data) as? [String] {
                        return array.first
                    }
                    return String(literal.dropFirst().dropLast())
                }
                escaped = character == "\\" && !escaped
                if character != "\\" { escaped = false }
            }
        }
        if first == "'" {
            var index = text.index(after: text.startIndex)
            var value = ""
            while index < text.endIndex {
                if text[index] == "'" {
                    let next = text.index(after: index)
                    if next < text.endIndex, text[next] == "'" {
                        value.append("'")
                        index = text.index(after: next)
                        continue
                    }
                    return value
                }
                value.append(text[index])
                index = text.index(after: index)
            }
        }

        var value = text
        if stopAtFlowDelimiter, let end = value.firstIndex(where: { $0 == "," || $0 == "}" }) {
            value = String(value[..<end])
        } else if let comment = value.range(of: #"\s+#"#, options: .regularExpression) {
            value = String(value[..<comment.lowerBound])
        }
        return value.trimmingCharacters(in: .whitespacesAndNewlines)
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
