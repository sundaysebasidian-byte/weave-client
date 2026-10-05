import Combine
import Darwin
import Foundation

private let weaveTunDevice = "utun_weave"

@MainActor
final class MihomoController: ObservableObject {
    @Published private(set) var state: MacConnectionState = .stopped
    @Published private(set) var message = "全设备双栈 TUN 未就绪时拒绝连接"

    private var process: Process?
    private var startTask: Task<Void, Never>?
    private var activeLaunchID: UUID?
    private let systemProxy = SystemProxyManager()

    var coreAvailable: Bool { executableURL != nil }

    func start(
        subscriptions: [MacSubscription],
        selectedSubscriptionID: UUID?,
        selectedNodeName: String?,
        availableNodeNames: [String],
        preferTun: Bool,
    ) {
        guard process == nil else { return }
        guard let executableURL else {
            state = .failed
            message = "未找到 Apple Silicon Mihomo 核心；连接入口保持关闭"
            return
        }
        guard !subscriptions.isEmpty else {
            state = .failed
            message = "请先导入至少一个 Clash 订阅"
            return
        }
        guard let selected = subscriptions.first(where: { $0.id == selectedSubscriptionID }) else {
            state = .failed
            message = "请先选择订阅"
            return
        }
        if let selectedNodeName,
           !availableNodeNames.contains(selectedNodeName) {
            state = .failed
            message = "所选节点已不存在，请重新选择"
            return
        }
        state = .starting
        message = preferTun ? "正在准备全设备 TUN" : "正在准备本地代理"
        startTask?.cancel()
        startTask = Task {
            do {
                let runtime = try await Task.detached(priority: .userInitiated) {
                    try Self.prepareRuntime(
                        subscriptions,
                        selected: selected,
                        selectedNodeName: selectedNodeName,
                        enableTun: preferTun,
                    )
                }.value
                guard !Task.isCancelled else { return }
                try await launch(
                    executableURL: executableURL,
                    runtime: runtime,
                    enableTun: preferTun,
                )
            } catch is CancellationError {
                return
            } catch {
                // Never silently fall back while TUN is requested: that would leave IPv4,
                // IPv6 or DNS outside the selected proxy while the UI appears connected.
                state = .failed
                message = preferTun
                    ? "双栈 TUN 未启用，已拒绝连接以防 IPv4/IPv6/DNS 直连 · \(error.localizedDescription)"
                    : error.localizedDescription
            }
        }
    }

    private func launch(executableURL: URL, runtime: URL, enableTun: Bool) async throws {
        let task = Process()
        let launchID = UUID()
        task.executableURL = executableURL
        task.arguments = ["-d", runtime.path, "-f", runtime.appendingPathComponent("config.yaml").path]
        task.standardOutput = FileHandle.nullDevice
        task.standardError = FileHandle.nullDevice
        task.terminationHandler = { [weak self] _ in
            Task { @MainActor in
                guard let self, self.activeLaunchID == launchID else { return }
                self.process = nil
                self.activeLaunchID = nil
                if self.state != .stopped {
                    self.state = .failed
                    self.systemProxy.restore()
                    self.message = "Mihomo 已停止，系统代理已恢复"
                }
            }
        }
        try task.run()
        process = task
        activeLaunchID = launchID
        do {
            try await Self.waitUntilReady(requireTun: enableTun, device: weaveTunDevice)
            if !enableTun {
                try systemProxy.enable(port: 7890)
            }
        } catch {
            terminateCurrentProcess()
            systemProxy.restore()
            throw error
        }
        state = enableTun ? .tun : .localProxy
        message = enableTun
            ? "全设备 TUN 已启用 · DNS 劫持 · STUN 防泄漏"
            : "系统 HTTP/HTTPS/SOCKS 已接管 · 127.0.0.1:7890"
    }

    func stop() {
        startTask?.cancel()
        startTask = nil
        state = .stopped
        message = "代理已停止"
        systemProxy.restore()
        terminateCurrentProcess()
    }

    private func terminateCurrentProcess() {
        activeLaunchID = nil
        process?.terminate()
        process = nil
    }

    private var executableURL: URL? {
        let candidates = [
            ProcessInfo.processInfo.environment["WEAVE_MIHOMO_PATH"].map(URL.init(fileURLWithPath:)),
            Bundle.main.url(forAuxiliaryExecutable: "mihomo"),
            Bundle.main.resourceURL?.appendingPathComponent("mihomo"),
        ].compactMap { $0 }
        return candidates.first { FileManager.default.isExecutableFile(atPath: $0.path) }
    }

    nonisolated private static func prepareRuntime(
        _ subscriptions: [MacSubscription],
        selected: MacSubscription,
        selectedNodeName: String?,
        enableTun: Bool,
    ) throws -> URL {
        let base = FileManager.default.urls(
            for: .cachesDirectory,
            in: .userDomainMask,
        ).first!.appendingPathComponent("Weave/runtime", isDirectory: true)
        try? FileManager.default.removeItem(at: base)
        let providers = base.appendingPathComponent("providers", isDirectory: true)
        try FileManager.default.createDirectory(at: providers, withIntermediateDirectories: true)
        for subscription in subscriptions {
            let sanitized = try ClashProviderSanitizer.sanitize(subscription.payload)
            try sanitized.write(
                to: providers.appendingPathComponent("\(subscription.id.uuidString).yaml"),
                atomically: true,
                encoding: .utf8,
            )
        }
        var yaml = """
        mixed-port: 7890
        allow-lan: false
        mode: rule
        log-level: warning
        ipv6: true
        find-process-mode: strict
        unified-delay: true
        tcp-concurrent: true

        """
        if enableTun {
            yaml += """
            tun:
              enable: true
              stack: mixed
              device: \(weaveTunDevice)
              dns-hijack:
                - 'any:53'
              auto-route: true
              auto-detect-interface: true
              strict-route: true
              mtu: 1500
              route-address:
                - 0.0.0.0/1
                - 128.0.0.0/1
                - ::/1
                - 8000::/1
              route-exclude-address:
                - 10.0.0.0/8
                - 100.64.0.0/10
                - 127.0.0.0/8
                - 169.254.0.0/16
                - 172.16.0.0/12
                - 192.168.0.0/16
                - 224.0.0.0/4
                - ::1/128
                - fe80::/10
                - fc00::/7
                - ff00::/8

            """
        }
        yaml += """
        dns:
          enable: true
          ipv6: true
          enhanced-mode: fake-ip
          fake-ip-range: 198.18.0.1/16
          fake-ip-filter:
            - '*.lan'
            - '*.local'
            - localhost
          default-nameserver:
            - 223.5.5.5
            - 119.29.29.29
          nameserver:
            - 'https://doh.pub/dns-query'
            - 'https://dns.alidns.com/dns-query'
            - 'https://1.1.1.1/dns-query'
          proxy-server-nameserver:
            - 'https://doh.pub/dns-query'
            - 'https://dns.alidns.com/dns-query'
        sniffer:
          enable: true
          force-dns-mapping: true
          parse-pure-ip: true
          override-destination: false
          sniff:
            TLS:
              ports: [443, 8443]
            HTTP:
              ports: [80, 8080-8880]
            QUIC:
              ports: [443, 8443]
        proxy-providers:

        """
        for subscription in subscriptions {
            yaml += """
              '\(subscription.id.uuidString)':
                type: file
                path: './providers/\(subscription.id.uuidString).yaml'
                health-check:
                  enable: true
                  url: 'https://www.gstatic.com/generate_204'
                  interval: 300

            """
        }
        yaml += "proxy-groups:\n"
        for subscription in subscriptions {
            yaml += """
              - name: '\(subscription.id.uuidString).auto'
                type: url-test
                use:
                  - '\(subscription.id.uuidString)'
                url: 'https://www.gstatic.com/generate_204'
                interval: 300

            """
        }
        let selectedTarget: String
        if let selectedNodeName {
            selectedTarget = "\(selected.id.uuidString).fixed"
            yaml += """
              - name: '\(selectedTarget)'
                type: select
                use:
                  - '\(selected.id.uuidString)'
                filter: '\(yamlSingleQuoted(exactRegex(selectedNodeName)))'

            """
        } else {
            selectedTarget = "\(selected.id.uuidString).auto"
        }
        yaml += """
        rules:
          # Block standard STUN ports in strict privacy mode. Chrome can fall back to TCP
          # through the proxy instead of exposing a direct public address.
          - AND,((NETWORK,UDP),(DST-PORT,3478)),REJECT
          - AND,((NETWORK,UDP),(DST-PORT,5349)),REJECT
          - AND,((NETWORK,UDP),(DST-PORT,19302-19309)),REJECT
          - DOMAIN-SUFFIX,stun.l.google.com,REJECT
          - DOMAIN-SUFFIX,stun1.l.google.com,REJECT
          - MATCH,'\(selectedTarget)'
        """
        try yaml.write(
            to: base.appendingPathComponent("config.yaml"),
            atomically: true,
            encoding: .utf8,
        )
        return base
    }

    nonisolated private static func waitUntilReady(
        requireTun: Bool,
        device: String,
    ) async throws {
        for _ in 0..<80 {
            try Task.checkCancellation()
            if isPortOpen(7890) &&
                (!requireTun || (isTunInterfacePresent(device) && hasDualStackRoutes(device))) {
                return
            }
            try await Task.sleep(for: .milliseconds(100))
        }
        if requireTun {
            throw WeaveMacError.message(
                "Mihomo 代理端口已启动，但 macOS 双栈 TUN 或 IPv4/IPv6 路由未就绪；已拒绝连接",
            )
        }
        throw WeaveMacError.message("Mihomo 未能在本机代理端口就绪")
    }

    nonisolated private static func isTunInterfacePresent(_ device: String) -> Bool {
        guard let output = commandOutput(
            executable: "/sbin/ifconfig",
            arguments: [device],
        ) else { return false }
        return output.contains("\(device):")
    }

    /// A TUN device alone is not enough: a failed auto-route can leave IPv4 or IPv6 on the
    /// physical interface while the UI says connected. Require a broad route in both family
    /// tables before exposing a successful state. Mihomo may render /1 as `0/1`, `128.0/1`,
    /// `8000::/1`, or a platform-specific equivalent, so the check intentionally matches any
    /// /1 route attached to our device rather than one exact netstat spelling.
    nonisolated private static func hasDualStackRoutes(_ device: String) -> Bool {
        guard
            let ipv4 = commandOutput(
                executable: "/usr/sbin/netstat",
                arguments: ["-rn", "-f", "inet"],
            ),
            let ipv6 = commandOutput(
                executable: "/usr/sbin/netstat",
                arguments: ["-rn", "-f", "inet6"],
            )
        else { return false }

        func hasBroadRoute(_ table: String) -> Bool {
            table.split(whereSeparator: \.isNewline).contains { line in
                let text = String(line)
                return text.contains(device) && (text.contains("/1") || text.contains("default"))
            }
        }
        return hasBroadRoute(ipv4) && hasBroadRoute(ipv6)
    }

    nonisolated private static func commandOutput(
        executable: String,
        arguments: [String],
    ) -> String? {
        let task = Process()
        let pipe = Pipe()
        task.executableURL = URL(fileURLWithPath: executable)
        task.arguments = arguments
        task.standardOutput = pipe
        task.standardError = FileHandle.nullDevice
        do {
            try task.run()
            task.waitUntilExit()
        } catch {
            return nil
        }
        guard task.terminationStatus == 0 else { return nil }
        return String(
            data: pipe.fileHandleForReading.readDataToEndOfFile(),
            encoding: .utf8,
        )
    }

    nonisolated private static func isPortOpen(_ port: UInt16) -> Bool {
        let descriptor = socket(AF_INET, SOCK_STREAM, 0)
        guard descriptor >= 0 else { return false }
        defer { close(descriptor) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = port.bigEndian
        "127.0.0.1".withCString { pointer in
            _ = inet_pton(AF_INET, pointer, &address.sin_addr)
        }
        return withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                connect(descriptor, $0, socklen_t(MemoryLayout<sockaddr_in>.size)) == 0
            }
        }
    }

    nonisolated private static func exactRegex(_ value: String) -> String {
        let meta = CharacterSet(charactersIn: "\\.^$|?*+()[]{}")
        return "^" + value.unicodeScalars.map {
            meta.contains($0) ? "\\\($0)" : String($0)
        }.joined() + "$"
    }

    nonisolated private static func yamlSingleQuoted(_ value: String) -> String {
        value.replacingOccurrences(of: "'", with: "''")
    }
}
