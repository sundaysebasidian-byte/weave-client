import Foundation

/// Owns the macOS system HTTP/HTTPS proxy transaction used by the private desktop build.
///
/// The Mihomo process is deliberately kept on loopback. We only touch the primary network
/// service, snapshot every value we change, and restore it on stop, crash, or app termination.
/// `networksetup` is invoked directly (never through a shell), so service names cannot become
/// command input.
@MainActor
final class SystemProxyManager {
    private struct Snapshot: Codable {
        let service: String
        let web: ProxyState
        let secure: ProxyState
        let socks: ProxyState
        let auto: AutoProxyState
        let bypassDomains: [String]

        private enum CodingKeys: String, CodingKey {
            case service, web, secure, socks, auto, bypassDomains
        }

        init(
            service: String,
            web: ProxyState,
            secure: ProxyState,
            socks: ProxyState,
            auto: AutoProxyState,
            bypassDomains: [String],
        ) {
            self.service = service
            self.web = web
            self.secure = secure
            self.socks = socks
            self.auto = auto
            self.bypassDomains = bypassDomains
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            service = try container.decode(String.self, forKey: .service)
            web = try container.decode(ProxyState.self, forKey: .web)
            secure = try container.decode(ProxyState.self, forKey: .secure)
            // alpha05 transactions did not snapshot SOCKS or PAC. Disabled empty values are
            // safe defaults and allow those transactions to be recovered on the next launch.
            socks = try container.decodeIfPresent(ProxyState.self, forKey: .socks)
                ?? ProxyState(enabled: false, server: "", port: 0)
            auto = try container.decodeIfPresent(AutoProxyState.self, forKey: .auto)
                ?? AutoProxyState(enabled: false, url: "")
            bypassDomains = try container.decodeIfPresent([String].self, forKey: .bypassDomains) ?? []
        }
    }

    private struct ProxyState: Codable {
        let enabled: Bool
        let server: String
        let port: Int
    }

    private struct AutoProxyState: Codable {
        let enabled: Bool
        let url: String
    }

    private var snapshots: [Snapshot] = []

    private let transactionURL: URL = {
        let support = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
        ).first!.appendingPathComponent("Weave", isDirectory: true)
        try? FileManager.default.createDirectory(
            at: support,
            withIntermediateDirectories: true,
        )
        return support.appendingPathComponent("system-proxy-transaction.json")
    }()

    init() {
        // A crash can happen after networksetup changes but before Process termination. Restore
        // the last transaction on the next launch before accepting a new connection.
        restoreOrphanedTransaction()
    }

    var isActive: Bool { !snapshots.isEmpty }

    func enable(port: Int) throws {
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
        )
        snapshots = [snapshot]
        try persist(snapshot)
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

    func restore() {
        let pending = snapshots
        guard !pending.isEmpty else {
            try? FileManager.default.removeItem(at: transactionURL)
            return
        }
        var restored = true
        for snapshot in pending {
            do {
                try setProxy(
                    service: snapshot.service,
                    secure: false,
                    state: snapshot.web,
                )
                try setProxy(
                    service: snapshot.service,
                    secure: true,
                    state: snapshot.secure,
                )
                try setSocksProxy(service: snapshot.service, state: snapshot.socks)
                try setAutoProxy(service: snapshot.service, state: snapshot.auto)
                try setBypassDomains(service: snapshot.service, domains: snapshot.bypassDomains)
            } catch {
                restored = false
            }
        }
        if restored {
            snapshots.removeAll()
            try? FileManager.default.removeItem(at: transactionURL)
        }
    }

    private func persist(_ snapshot: Snapshot) throws {
        let data = try JSONEncoder().encode(snapshot)
        try data.write(to: transactionURL, options: [.atomic, .completeFileProtection])
    }

    private func restoreOrphanedTransaction() {
        guard
            let data = try? Data(contentsOf: transactionURL),
            let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data)
        else { return }
        // Mark it active so restore() follows the same cleanup path, but do not expose the
        // previous proxy as active to the UI after startup.
        snapshots = [snapshot]
        restore()
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
        return ProxyState(
            enabled: value(after: "Enabled:", in: output)?.lowercased() == "yes",
            server: value(after: "Server:", in: output) ?? "",
            port: Int(value(after: "Port:", in: output) ?? "0") ?? 0,
        )
    }

    private func socksProxyState(service: String) throws -> ProxyState {
        let output = try command(executable: "/usr/sbin/networksetup", arguments: ["-getsocksfirewallproxy", service])
        return ProxyState(
            enabled: value(after: "Enabled:", in: output)?.lowercased() == "yes",
            server: value(after: "Server:", in: output) ?? "",
            port: Int(value(after: "Port:", in: output) ?? "0") ?? 0,
        )
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
        let process = Process()
        let output = Pipe()
        let errors = Pipe()
        process.executableURL = URL(fileURLWithPath: executable)
        process.arguments = arguments
        process.standardOutput = output
        process.standardError = errors
        try process.run()
        process.waitUntilExit()
        let stdout = output.fileHandleForReading.readDataToEndOfFile()
        let stderr = errors.fileHandleForReading.readDataToEndOfFile()
        guard process.terminationStatus == 0 else {
            let detail = String(data: stderr, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let lower = detail?.lowercased() ?? ""
            if lower.contains("root") || lower.contains("authoriz") || lower.contains("permission") {
                throw WeaveMacError.message("macOS 拒绝修改系统代理；请允许网络设置或用有管理员权限的账户运行")
            }
            throw WeaveMacError.message(detail?.isEmpty == false ? detail! : "macOS 网络代理设置失败")
        }
        return String(data: stdout, encoding: .utf8) ?? ""
    }
}
