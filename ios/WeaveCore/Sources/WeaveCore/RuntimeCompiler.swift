import CryptoKit
import Foundation

public struct RuntimeLaunchDescriptor: Codable, Equatable, Sendable {
    public let schemaVersion: Int
    public let directoryName: String
    public let configurationSHA256: String
    public let ipv6Enabled: Bool
    public let createdAt: Date

    public init(
        schemaVersion: Int = 2,
        directoryName: String,
        configurationSHA256: String,
        ipv6Enabled: Bool,
        createdAt: Date = Date()
    ) {
        self.schemaVersion = schemaVersion
        self.directoryName = directoryName
        self.configurationSHA256 = configurationSHA256
        self.ipv6Enabled = ipv6Enabled
        self.createdAt = createdAt
    }
}

public enum RuntimeConfigCompiler {
    public static func compileYAML(
        subscriptions: [WeaveSubscription],
        selection: RuntimeSelection,
        routes: [DomainRouteRule],
        preferences: RuntimePreferences
    ) throws -> String {
        guard let selected = subscriptions.first(where: { $0.id == selection.subscriptionID }) else {
            throw WeaveError.message("所选订阅已经不存在")
        }
        let nodeNames = ClashNodeParser.parse(selected.payload)
        if let nodeName = selection.nodeName, !nodeNames.contains(nodeName) {
            throw WeaveError.message("所选节点已经不存在，请重新选择")
        }
        let byID = Dictionary(uniqueKeysWithValues: subscriptions.map { ($0.id, $0) })
        for route in routes {
            try validateDomainSuffix(route.domainSuffix)
            if let id = route.target.subscriptionID, byID[id] == nil {
                throw WeaveError.message("分流规则引用了已删除的订阅")
            }
            if case let .fixed(id, nodeName) = route.target,
               let subscription = byID[id],
               !ClashNodeParser.parse(subscription.payload).contains(nodeName) {
                throw WeaveError.message("分流规则引用了已失效的节点")
            }
        }

        let defaultTarget = try targetTag(
            selection.nodeName.map { .fixed(subscriptionID: selected.id, nodeName: $0) }
                ?? .automatic(subscriptionID: selected.id),
            subscriptions: byID
        )
        if preferences.dnsProfile == .custom {
            let endpoint = preferences.customDNSEndpoint.trimmingCharacters(
                in: .whitespacesAndNewlines
            )
            guard isEncryptedDNSEndpoint(endpoint) else {
                throw WeaveError.message("自定义 DNS 必须是无凭据、查询参数或片段的 DoH/DoT/DoQ 地址")
            }
        }
        var yaml = """
        mode: rule
        log-level: warning
        ipv6: \(preferences.ipv6Mode == .dualStack ? "true" : "false")
        unified-delay: true
        tcp-concurrent: true
        profile:
          store-selected: false
          store-fake-ip: false
        proxy-providers:

        """
        for subscription in subscriptions {
            yaml += """
              '\(subscription.id.uuidString)':
                type: file
                path: './providers/\(subscription.id.uuidString).yaml'
                health-check:
                  enable: false
                  url: https://www.gstatic.com/generate_204
                  interval: 300

            """
        }

        yaml += "proxy-groups:\n"
        for subscription in subscriptions {
            yaml += """
              - name: '\(automaticTag(subscription.id))'
                type: \(preferences.automaticStrategy.mihomoType)
                use:
                  - '\(subscription.id.uuidString)'
                url: https://www.gstatic.com/generate_204
                interval: 300

            """
            if preferences.automaticStrategy == .loadBalance {
                yaml += "    strategy: consistent-hashing\n"
            }
        }

        var fixedTargets: [(id: UUID, node: String)] = []
        if let nodeName = selection.nodeName { fixedTargets.append((selected.id, nodeName)) }
        for route in routes {
            if case let .fixed(id, nodeName) = route.target,
               !fixedTargets.contains(where: { $0.id == id && $0.node == nodeName }) {
                fixedTargets.append((id, nodeName))
            }
        }
        for fixed in fixedTargets {
            yaml += """
              - name: '\(fixedTag(fixed.id, fixed.node))'
                type: select
                use:
                  - '\(fixed.id.uuidString)'
                filter: '\(yamlSingleQuoted(exactRegex(fixed.node)))'

            """
        }

        yaml += dnsSection(preferences)
        yaml += "rules:\n"
        if preferences.blockSTUN {
            yaml += "  - AND,((NETWORK,UDP),(DST-PORT,3478-3479/19302-19309)),REJECT\n"
        }
        switch preferences.routingMode {
        case .direct:
            yaml += "  - MATCH,DIRECT\n"
        case .global:
            yaml += "  - MATCH,\(defaultTarget)\n"
        case .rule:
            for route in routes {
                let suffix = normalizedDomainSuffix(route.domainSuffix)
                let target = try targetTag(route.target, subscriptions: byID)
                yaml += "  - DOMAIN-SUFFIX,\(suffix),\(target)\n"
            }
            if preferences.directMainlandChina {
                yaml += "  - GEOSITE,cn,DIRECT\n"
                yaml += "  - GEOIP,CN,DIRECT,no-resolve\n"
            }
            yaml += "  - MATCH,\(defaultTarget)\n"
        }
        return yaml
    }

    public static func stage(
        in container: URL,
        subscriptions: [WeaveSubscription],
        selection: RuntimeSelection,
        routes: [DomainRouteRule],
        preferences: RuntimePreferences
    ) throws -> RuntimeLaunchDescriptor {
        let root = container.appendingPathComponent("Library/Application Support/Weave/runtime", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let identifier = UUID().uuidString
        let candidate = root.appendingPathComponent("candidate-\(identifier)", isDirectory: true)
        let active = root.appendingPathComponent("active-\(identifier)", isDirectory: true)
        let providers = candidate.appendingPathComponent("providers", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: providers, withIntermediateDirectories: true)
            for subscription in subscriptions {
                try writeProtected(
                    Data(subscription.payload.utf8),
                    to: providers.appendingPathComponent("\(subscription.id.uuidString).yaml")
                )
            }
            let yaml = try compileYAML(
                subscriptions: subscriptions,
                selection: selection,
                routes: routes,
                preferences: preferences
            )
            let data = Data(yaml.utf8)
            try writeProtected(data, to: candidate.appendingPathComponent("config.yaml"))
            let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
            let descriptor = RuntimeLaunchDescriptor(
                directoryName: active.lastPathComponent,
                configurationSHA256: digest,
                ipv6Enabled: preferences.ipv6Mode == .dualStack
            )
            let manifest = try JSONEncoder().encode(descriptor)
            try writeProtected(manifest, to: candidate.appendingPathComponent("manifest.json"))
            try FileManager.default.moveItem(at: candidate, to: active)
            cleanupOldRuntimeDirectories(in: root, keeping: active.lastPathComponent)
            return descriptor
        } catch {
            try? FileManager.default.removeItem(at: candidate)
            throw error
        }
    }

    public static func validateStagedRuntime(
        descriptor: RuntimeLaunchDescriptor,
        in container: URL
    ) throws -> URL {
        guard descriptor.schemaVersion == 2,
              descriptor.directoryName.range(
                  of: #"^active-[0-9A-F-]{36}$"#,
                  options: .regularExpression
              ) != nil else {
            throw WeaveError.message("隧道运行清单无效")
        }
        let directory = container.appendingPathComponent(
            "Library/Application Support/Weave/runtime/\(descriptor.directoryName)",
            isDirectory: true
        )
        let configURL = directory.appendingPathComponent("config.yaml")
        let data = try Data(contentsOf: configURL)
        let digest = SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
        guard digest == descriptor.configurationSHA256 else {
            throw WeaveError.message("隧道配置校验失败")
        }
        return directory
    }

    public static func clearRuntime(in container: URL) {
        let root = container.appendingPathComponent("Library/Application Support/Weave/runtime")
        try? FileManager.default.removeItem(at: root)
    }

    private static func dnsSection(_ preferences: RuntimePreferences) -> String {
        let nameservers: [String]
        switch preferences.dnsProfile {
        case .privacy:
            nameservers = ["https://1.1.1.1/dns-query", "https://dns.google/dns-query"]
        case .adBlock:
            nameservers = ["https://dns.adguard-dns.com/dns-query"]
        case .family:
            nameservers = ["https://family.adguard-dns.com/dns-query"]
        case .custom:
            let endpoint = preferences.customDNSEndpoint.trimmingCharacters(in: .whitespacesAndNewlines)
            nameservers = [endpoint]
        }
        var yaml = """
        dns:
          enable: true
          ipv6: \(preferences.ipv6Mode == .dualStack ? "true" : "false")
          enhanced-mode: fake-ip
          fake-ip-range: 198.18.0.1/16
          fake-ip-filter:
            - '*.lan'
            - '*.local'
            - 'localhost.ptlogin2.qq.com'
          default-nameserver:
            - 1.1.1.1
            - 8.8.8.8
          nameserver:

        """
        for server in nameservers { yaml += "    - '\(yamlSingleQuoted(server))'\n" }
        return yaml
    }

    private static func isEncryptedDNSEndpoint(_ value: String) -> Bool {
        guard let components = URLComponents(string: value),
              let scheme = components.scheme?.lowercased(),
              ["https", "tls", "quic"].contains(scheme),
              components.host?.isEmpty == false,
              components.user == nil,
              components.password == nil,
              components.query == nil,
              components.fragment == nil else { return false }
        return true
    }

    private static func targetTag(
        _ target: RouteTarget,
        subscriptions: [UUID: WeaveSubscription]
    ) throws -> String {
        switch target {
        case .direct: return "DIRECT"
        case .block: return "REJECT"
        case let .automatic(id):
            guard subscriptions[id] != nil else { throw WeaveError.message("规则订阅已失效") }
            return automaticTag(id)
        case let .fixed(id, node):
            guard let subscription = subscriptions[id],
                  ClashNodeParser.parse(subscription.payload).contains(node) else {
                throw WeaveError.message("规则节点已失效")
            }
            return fixedTag(id, node)
        }
    }

    private static func automaticTag(_ id: UUID) -> String { "weave.\(id.uuidString).auto" }

    private static func fixedTag(_ id: UUID, _ node: String) -> String {
        let digest = SHA256.hash(data: Data(node.utf8)).prefix(8)
            .map { String(format: "%02x", $0) }.joined()
        return "weave.\(id.uuidString).fixed.\(digest)"
    }

    private static func normalizedDomainSuffix(_ raw: String) -> String {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if value.hasPrefix("*.") { value.removeFirst(2) }
        while value.hasPrefix(".") { value.removeFirst() }
        return value
    }

    private static func validateDomainSuffix(_ raw: String) throws {
        let value = normalizedDomainSuffix(raw)
        guard value.range(
            of: #"^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$"#,
            options: .regularExpression
        ) != nil else {
            throw WeaveError.message("域名规则必须是有效的域名后缀")
        }
    }

    private static func exactRegex(_ value: String) -> String {
        let meta = CharacterSet(charactersIn: "\\.^$|?*+()[]{}")
        return "^" + value.unicodeScalars.map {
            meta.contains($0) ? "\\\($0)" : String($0)
        }.joined() + "$"
    }

    private static func yamlSingleQuoted(_ value: String) -> String {
        value.replacingOccurrences(of: "'", with: "''")
    }

    private static func writeProtected(_ data: Data, to url: URL) throws {
        #if os(iOS)
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        #else
        try data.write(to: url, options: .atomic)
        #endif
    }

    private static func cleanupOldRuntimeDirectories(in root: URL, keeping name: String) {
        guard let entries = try? FileManager.default.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: nil
        ) else { return }
        for entry in entries where entry.lastPathComponent != name {
            try? FileManager.default.removeItem(at: entry)
        }
    }
}
