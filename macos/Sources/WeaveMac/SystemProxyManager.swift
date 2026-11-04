import WeaveCoreRuntime
import Foundation

/// Owns the macOS system HTTP/HTTPS proxy transaction used by the private desktop build.
///
/// The Mihomo process is deliberately kept on loopback. We only touch the primary network
/// service, snapshot every value we change, and restore it on stop, crash, or app termination.
/// `networksetup` is invoked directly (never through a shell), so service names cannot become
/// command input.
actor SystemProxyManager {
    enum RestoreResult: Sendable { case restored, preservedChanges, failed }
    private enum Field: String, Codable, CaseIterable {
        case web, secure, socks, auto, bypass
    }
    private struct Snapshot: Codable {
        let service: String
        let web: ProxyState
        let secure: ProxyState
        let socks: ProxyState
        let auto: AutoProxyState
        let bypassDomains: [String]
        let ownedPort: Int
        var pending: Set<Field>
        var restoring: Set<Field>

        private enum CodingKeys: String, CodingKey {
            case service, web, secure, socks, auto, bypassDomains, ownedPort, pending, restoring
        }

        init(
            service: String,
            web: ProxyState,
            secure: ProxyState,
            socks: ProxyState,
            auto: AutoProxyState,
            bypassDomains: [String],
            ownedPort: Int,
        ) {
            self.service = service
            self.web = web
            self.secure = secure
            self.socks = socks
            self.auto = auto
            self.bypassDomains = bypassDomains
            self.ownedPort = ownedPort
            pending = Set(Field.allCases)
            restoring = []
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            service = try container.decode(String.self, forKey: .service)
            web = try container.decode(ProxyState.self, forKey: .web)
            secure = try container.decode(ProxyState.self, forKey: .secure)
            // Older journals never owned fields they did not snapshot.
            socks = try container.decodeIfPresent(ProxyState.self, forKey: .socks)
                ?? ProxyState(enabled: false, server: "", port: 0)
            auto = try container.decodeIfPresent(AutoProxyState.self, forKey: .auto)
                ?? AutoProxyState(enabled: false, url: "")
            bypassDomains = try container.decodeIfPresent([String].self, forKey: .bypassDomains) ?? []
            ownedPort = try container.decodeIfPresent(Int.self, forKey: .ownedPort) ?? 7890
            var legacy: Set<Field> = [.web, .secure]
            if container.contains(.socks) { legacy.insert(.socks) }
            if container.contains(.auto) { legacy.insert(.auto) }
            if container.contains(.bypassDomains) { legacy.insert(.bypass) }
            pending = try container.decodeIfPresent(Set<Field>.self, forKey: .pending) ?? legacy
            restoring = try container.decodeIfPresent(Set<Field>.self, forKey: .restoring) ?? []
        }
    }

    private struct ProxyState: Codable, Equatable {
        let enabled: Bool
        let server: String
        let port: Int
        let authenticated: Bool

        init(enabled: Bool, server: String, port: Int, authenticated: Bool = false) {
            self.enabled = enabled
            self.server = server
            self.port = port
            self.authenticated = authenticated
        }

        private enum CodingKeys: String, CodingKey { case enabled, server, port, authenticated }
        init(from decoder: Decoder) throws {
            let values = try decoder.container(keyedBy: CodingKeys.self)
            enabled = try values.decode(Bool.self, forKey: .enabled)
            server = try values.decode(String.self, forKey: .server)
            port = try values.decode(Int.self, forKey: .port)
            authenticated = try values.decodeIfPresent(Bool.self, forKey: .authenticated) ?? false
        }
    }

    private struct AutoProxyState: Codable, Equatable {
        let enabled: Bool
        let url: String
    }

    private var snapshots: [Snapshot] = []
    private var unreadableTransaction = false
    private var journalLoaded = false
    private(set) var lastError: String?
    private let runCommand: @Sendable (String, [String]) throws -> String

    private let transactionURL: URL

    private static func defaultTransactionURL() -> URL {
        let support = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
        ).first!.appendingPathComponent("Weave", isDirectory: true)
        try? FileManager.default.createDirectory(
            at: support,
            withIntermediateDirectories: true,
        )
        return support.appendingPathComponent("system-proxy-transaction.json")
    }

    init(transactionURL: URL? = nil, runCommand: @escaping @Sendable (String, [String]) throws -> String = SystemProxyManager.executeCommand) {
        self.transactionURL = transactionURL ?? Self.defaultTransactionURL()
        self.runCommand = runCommand
    }

    var isActive: Bool { !snapshots.isEmpty }

    func enable(port: Int) throws {
        loadJournalIfNeeded()
        guard !unreadableTransaction else {
            throw WeaveMacError.message(lastError ?? "系统代理恢复记录无法读取")
        }
        guard port >= 1 && port <= 65_535 else {
            throw WeaveMacError.message("本地代理端口无效")
        }
        if !snapshots.isEmpty {
            // A failed previous restore must never be treated as an active connection. Try to
            // finish it first; if macOS still refuses the write, fail closed instead of claiming
            // that the next Mihomo process is connected without a system proxy.
            restore()
            guard snapshots.isEmpty else {
                throw WeaveMacError.message("上一次系统代理尚未恢复，请在系统设置中允许网络更改后重试")
            }
        }

        let service = try primaryService()
        let snapshot = Snapshot(
            service: service,
            web: try proxyState(service: service, secure: false),
            secure: try proxyState(service: service, secure: true),
            socks: try socksProxyState(service: service),
            auto: try autoProxyState(service: service),
            bypassDomains: try bypassDomains(service: service),
            ownedPort: port,
        )
        // networksetup exposes the authentication flag but cannot recover the original secret.
        // Do not overwrite even a disabled authenticated proxy and promise an impossible restore.
        guard ![snapshot.web, snapshot.secure, snapshot.socks].contains(where: \.authenticated) else {
            throw WeaveMacError.message("当前网络保留了需要认证的代理配置；无法安全恢复其凭据，系统代理设置保持不变")
        }
        try persist(snapshot)
        snapshots = [snapshot]
        do {
            try setProxy(service: service, secure: false, server: "127.0.0.1", port: port)
            try setProxy(service: service, secure: true, server: "127.0.0.1", port: port)
            try setSocksProxy(service: service, server: "127.0.0.1", port: port)
            try setAutoProxyState(service: service, enabled: false)
            // Existing bypass entries silently circumvent the local proxy. Clear them for the
            // session; the exact list is restored when the proxy is stopped.
            try setBypassDomains(service: service, domains: [])
        } catch {
            restore()
            throw error
        }
    }

    @discardableResult
    func restore() -> RestoreResult {
        loadJournalIfNeeded()
        guard !unreadableTransaction else { return .failed }
        guard !snapshots.isEmpty else { return .restored }
        lastError = nil
        var preserved = false
        for field in Field.allCases where snapshots[0].pending.contains(field) {
            do {
                if try stillOwned(field, snapshot: snapshots[0]) {
                    // Persist intent before the first command, so a failed second command can
                    // resume without treating our own partially restored endpoint as a new owner.
                    snapshots[0].restoring.insert(field)
                    try persist(snapshots[0])
                    try restoreField(field, snapshot: snapshots[0])
                } else {
                    preserved = true
                }
                var completed = snapshots[0]
                completed.pending.remove(field)
                completed.restoring.remove(field)
                try persist(completed)
                snapshots[0] = completed
            } catch {
                lastError = error.localizedDescription
            }
        }
        if snapshots[0].pending.isEmpty {
            do {
                try FileManager.default.removeItem(at: transactionURL)
                snapshots.removeAll()
            } catch {
                lastError = error.localizedDescription
            }
        }
        guard snapshots.isEmpty else { return .failed }
        return preserved ? .preservedChanges : .restored
    }

    private func stillOwned(_ field: Field, snapshot: Snapshot) throws -> Bool {
        let owned = ProxyState(enabled: true, server: "127.0.0.1", port: snapshot.ownedPort)
        let current: ProxyState
        let original: ProxyState
        switch field {
        case .web, .secure:
            current = try proxyState(service: snapshot.service, secure: field == .secure)
            original = field == .secure ? snapshot.secure : snapshot.web
        case .socks:
            current = try socksProxyState(service: snapshot.service)
            original = snapshot.socks
        case .auto:
            let value = try autoProxyState(service: snapshot.service)
            return value == AutoProxyState(enabled: false, url: snapshot.auto.url)
        case .bypass:
            return try bypassDomains(service: snapshot.service).isEmpty
        }
        if current == owned { return true }
        return snapshot.restoring.contains(field) && !original.server.isEmpty && original.port > 0 &&
            current == ProxyState(enabled: true, server: original.server, port: original.port)
    }

    private func restoreField(_ field: Field, snapshot: Snapshot) throws {
        switch field {
        case .web: try setProxy(service: snapshot.service, secure: false, state: snapshot.web)
        case .secure: try setProxy(service: snapshot.service, secure: true, state: snapshot.secure)
        case .socks: try setSocksProxy(service: snapshot.service, state: snapshot.socks)
        case .auto: try setAutoProxy(service: snapshot.service, state: snapshot.auto)
        case .bypass: try setBypassDomains(service: snapshot.service, domains: snapshot.bypassDomains)
        }
    }

    private func persist(_ snapshot: Snapshot) throws {
        let data = try JSONEncoder().encode(snapshot)
        try data.write(to: transactionURL, options: [.atomic, .completeFileProtection])
    }

    private func loadJournalIfNeeded() {
        guard !journalLoaded else { return }
        journalLoaded = true
        do {
            let data = try Data(contentsOf: transactionURL)
            let snapshot = try JSONDecoder().decode(Snapshot.self, from: data)
            guard (1...65_535).contains(snapshot.ownedPort), !snapshot.service.isEmpty else {
                throw WeaveMacError.message("系统代理恢复记录无效")
            }
            snapshots = [snapshot]
        } catch CocoaError.fileReadNoSuchFile {
            return
        } catch {
            unreadableTransaction = true
            lastError = "无法读取系统代理恢复记录，已禁止覆盖：\(error.localizedDescription)"
        }
    }

    private func primaryService() throws -> String {
        let services = try listServices()
        guard !services.isEmpty else {
            throw WeaveMacError.message("没有可用的 macOS 网络服务")
        }

        if let interface = try? command(
            executable: "/sbin/route",
            arguments: ["-n", "get", "default"],
        ).split(separator: "\n")
            .first(where: { $0.trimmingCharacters(in: .whitespaces).hasPrefix("interface:") })
            .map({ $0.split(separator: ":", maxSplits: 1).last?.trimmingCharacters(in: .whitespaces) })
            .flatMap({ $0 }) {
            let order = try? command(
                executable: "/usr/sbin/networksetup",
                arguments: ["-listnetworkserviceorder"],
            )
            if let match = order?.components(separatedBy: .newlines).enumerated().compactMap({ index, line -> String? in
                guard line.contains("Device: \(interface)") else { return nil }
                for previous in stride(from: index - 1, through: 0, by: -1) {
                    let candidate = order!.components(separatedBy: .newlines)[previous]
                    if let range = candidate.range(of: #"^\(\d+\) (.+)$"#, options: .regularExpression) {
                        let value = String(candidate[range]).replacingOccurrences(
                            of: #"^\(\d+\) "#,
                            with: "",
                            options: .regularExpression,
                        )
                        return value.hasPrefix("*") ? nil : value
                    }
                }
                return nil
            }).first {
                return match
            }
        }

        return services[0]
    }

    private func listServices() throws -> [String] {
        try command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-listallnetworkservices"],
        )
        .components(separatedBy: .newlines)
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty && !$0.hasPrefix("An asterisk") && !$0.hasPrefix("*") }
    }

    private func proxyState(service: String, secure: Bool) throws -> ProxyState {
        let kind = secure ? "-getsecurewebproxy" : "-getwebproxy"
        let output = try command(executable: "/usr/sbin/networksetup", arguments: [kind, service])
        return try parseProxyState(output)
    }

    private func socksProxyState(service: String) throws -> ProxyState {
        let output = try command(executable: "/usr/sbin/networksetup", arguments: ["-getsocksfirewallproxy", service])
        return try parseProxyState(output)
    }

    private func parseProxyState(_ output: String) throws -> ProxyState {
        guard let enabled = value(after: "Enabled:", in: output), ["Yes", "No"].contains(enabled),
              let server = value(after: "Server:", in: output),
              let rawPort = value(after: "Port:", in: output), let port = Int(rawPort), (0...65_535).contains(port),
              let auth = value(after: "Authenticated Proxy Enabled:", in: output), ["0", "1"].contains(auth) else {
            throw WeaveMacError.message("无法完整读取系统代理状态，未进行配置覆盖")
        }
        return ProxyState(enabled: enabled == "Yes", server: server, port: port, authenticated: auth == "1")
    }

    private func autoProxyState(service: String) throws -> AutoProxyState {
        let output = try command(executable: "/usr/sbin/networksetup", arguments: ["-getautoproxyurl", service])
        return AutoProxyState(
            enabled: value(after: "Enabled:", in: output)?.lowercased() == "yes",
            url: value(after: "URL:", in: output) ?? "",
        )
    }

    private func bypassDomains(service: String) throws -> [String] {
        let output = try command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-getproxybypassdomains", service],
        )
        return output.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter {
                !$0.isEmpty &&
                    !$0.localizedCaseInsensitiveContains("There aren't any") &&
                    !$0.localizedCaseInsensitiveContains("bypass domains")
            }
    }

    private func setProxy(service: String, secure: Bool, server: String, port: Int) throws {
        let kind = secure ? "-setsecurewebproxy" : "-setwebproxy"
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: [kind, service, server, String(port)],
        )
        let stateKind = secure ? "-setsecurewebproxystate" : "-setwebproxystate"
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: [stateKind, service, "on"],
        )
    }

    private func setSocksProxy(service: String, server: String, port: Int) throws {
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-setsocksfirewallproxy", service, server, String(port)],
        )
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-setsocksfirewallproxystate", service, "on"],
        )
    }

    private func setSocksProxy(service: String, state: ProxyState) throws {
        if !state.server.isEmpty, state.port > 0 {
            try _ = command(
                executable: "/usr/sbin/networksetup",
                arguments: ["-setsocksfirewallproxy", service, state.server, String(state.port)],
            )
        }
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-setsocksfirewallproxystate", service, state.enabled ? "on" : "off"],
        )
    }

    private func setProxy(service: String, secure: Bool, state: ProxyState) throws {
        let kind = secure ? "-setsecurewebproxy" : "-setwebproxy"
        if !state.server.isEmpty, state.port > 0 {
            try _ = command(
                executable: "/usr/sbin/networksetup",
                arguments: [kind, service, state.server, String(state.port)],
            )
        }
        let stateKind = secure ? "-setsecurewebproxystate" : "-setwebproxystate"
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: [stateKind, service, state.enabled ? "on" : "off"],
        )
    }

    private func setAutoProxyState(service: String, enabled: Bool) throws {
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-setautoproxystate", service, enabled ? "on" : "off"],
        )
    }

    private func setAutoProxy(service: String, state: AutoProxyState) throws {
        if !state.url.isEmpty {
            try _ = command(
                executable: "/usr/sbin/networksetup",
                arguments: ["-setautoproxyurl", service, state.url],
            )
        }
        try setAutoProxyState(service: service, enabled: state.enabled)
    }

    private func setBypassDomains(service: String, domains: [String]) throws {
        // Passing a single empty value is the documented networksetup representation for an
        // empty bypass list and avoids invoking a shell with a variable argument list.
        try _ = command(
            executable: "/usr/sbin/networksetup",
            arguments: ["-setproxybypassdomains", service] + (domains.isEmpty ? [""] : domains),
        )
    }

    private func value(after label: String, in output: String) -> String? {
        output.components(separatedBy: .newlines)
            .first { $0.hasPrefix(label) }
            .map { String($0.dropFirst(label.count)).trimmingCharacters(in: .whitespaces) }
    }

    @discardableResult
    private func command(executable: String, arguments: [String]) throws -> String {
        try runCommand(executable, arguments)
    }

    nonisolated private static func executeCommand(_ executable: String, _ arguments: [String]) throws -> String {
        try BoundedCommand.run(executable: executable, arguments: arguments)
    }
}
