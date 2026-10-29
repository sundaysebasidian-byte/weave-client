import Combine
import Darwin
import Foundation

@MainActor
final class MihomoController: ObservableObject {
    @Published private(set) var state: MacConnectionState = .stopped
    @Published private(set) var message = "尚未连接"

    private var process: Process?
    private var runtime: MacRuntime?
    private var startTask: Task<Void, Never>?
    private var stopTask: Task<Void, Never>?
    private var operationID = UUID()
    private var activeLaunchID: UUID?
    private let systemProxy: SystemProxyManager
    private let providedExecutable: URL?
    private let runtimeRoot: URL

    init(systemProxy: SystemProxyManager = SystemProxyManager(), executable: URL? = nil, runtimeRoot: URL = RuntimeDirectory.defaultRoot) {
        self.systemProxy = systemProxy
        providedExecutable = executable
        self.runtimeRoot = runtimeRoot
    }

    func recoverSystemProxy() async {
        guard !state.isActive else { return }
        let operation = UUID()
        operationID = operation
        state = .stopping
        let result = await systemProxy.restore()
        let detail = await restorationMessage(result)
        guard operationID == operation else { return }
        message = detail
        state = result == .failed ? .failed : .stopped
    }

    var coreAvailable: Bool { executableURL != nil }
    var processIdentifier: Int32? { process?.processIdentifier }

    var subscriptionHostResolver: SubscriptionHostResolver? {
        guard state.isConnected, let runtime, let process, process.isRunning else { return nil }
        let resolver = CoreDNSResolver(controllerPort: runtime.controllerPort, secret: runtime.secret, processID: process.processIdentifier)
        return { host in try await resolver.resolve(host) }
    }

    func start(
        subscriptions: [MacSubscription],
        selectedSubscriptionID: UUID?,
        selectedNodeName: String?,
        availableNodeNames: [String],
        preferTun: Bool,
    ) {
        guard process == nil, !state.isActive else { return }
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
        let operation = UUID()
        operationID = operation
        message = preferTun ? "正在准备全设备 TUN" : "正在准备本地代理"
        startTask?.cancel()
        let runtimeRoot = self.runtimeRoot
        startTask = Task {
            do {
                let runtime = try await Task.detached(priority: .userInitiated) {
                    let runtime = try Self.prepareRuntime(
                        selected: selected,
                        selectedNodeName: selectedNodeName,
                        enableTun: preferTun,
                        root: runtimeRoot,
                    )
                    do {
                        _ = try BoundedCommand.run(executable: executableURL.path, arguments: ["-t", "-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path], timeout: 10)
                        return runtime
                    } catch {
                        _ = try? runtime.directory.removeAfterCoreExit()
                        throw error
                    }
                }.value
                guard operationID == operation, !Task.isCancelled else {
                    try runtime.directory.removeAfterCoreExit()
                    return
                }
                self.runtime = runtime
                try await launch(
                    executableURL: executableURL,
                    runtime: runtime,
                    enableTun: preferTun,
                )
            } catch is CancellationError {
                return
            } catch {
                guard operationID == operation else { return }
                // Never silently fall back while TUN is requested: that would leave IPv4,
                // IPv6 or DNS outside the selected proxy while the UI appears connected.
                state = .failed
                message = preferTun
                    ? "双栈 TUN 启动失败 · \(error.localizedDescription)"
                    : error.localizedDescription
                cleanupRuntime()
            }
        }
    }

    private func launch(executableURL: URL, runtime: MacRuntime, enableTun: Bool) async throws {
        let task = Process()
        let launchID = UUID()
        task.executableURL = executableURL
        task.arguments = ["-d", runtime.directory.url.path, "-f", runtime.directory.url.appendingPathComponent("config.yaml").path]
        task.standardInput = runtime.directory.lease
        task.standardOutput = FileHandle.nullDevice
        task.standardError = FileHandle.nullDevice
        task.terminationHandler = { [weak self] _ in
            Task { @MainActor in
                guard let self, self.activeLaunchID == launchID else { return }
                self.process = nil
                self.activeLaunchID = nil
                let operation = UUID()
                self.operationID = operation
                self.startTask?.cancel()
                self.state = .stopping
                let detail = await self.restorationMessage(self.systemProxy.restore())
                guard self.operationID == operation else { return }
                self.message = "核心意外退出 · " + detail
                self.cleanupRuntime()
                self.state = .failed
            }
        }
        try task.run()
        process = task
        activeLaunchID = launchID
        do {
            try await Self.waitUntilReady(runtime: runtime, pid: task.processIdentifier, requireTun: enableTun)
            try Task.checkCancellation()
            guard activeLaunchID == launchID, task.isRunning else { throw CancellationError() }
            if !enableTun {
                try await systemProxy.enable(port: Int(runtime.mixedPort))
            }
            try Task.checkCancellation()
            guard activeLaunchID == launchID, task.isRunning else { throw CancellationError() }
        } catch {
            if activeLaunchID == launchID { await terminateCurrentProcess() }
            await systemProxy.restore()
            cleanupRuntime()
            throw error
        }
        state = enableTun ? .tun : .localProxy
        message = enableTun
            ? "双栈 TUN 路由已就绪 · 外网连通性尚未验证"
            : "系统代理已启用 · 127.0.0.1:\(runtime.mixedPort) · 外网连通性尚未验证"
    }

    func stop() async {
        if let stopTask { await stopTask.value; return }
        // Shutdown is one shared, uncancelled operation. A cancelled caller must not spin on
        // sleep or abandon restoration while another caller (including app quit) is waiting.
        operationID = UUID()
        startTask?.cancel()
        let pendingStart = startTask
        state = .stopping
        message = "正在停止代理并恢复网络设置"
        let shutdown = Task {
            await terminateCurrentProcess()
            await pendingStart?.value
            startTask = nil
            let result = await systemProxy.restore()
            message = await restorationMessage(result)
            cleanupRuntime()
            state = result == .failed ? .failed : .stopped
            if process != nil {
                state = .failed
                message = "核心尚未退出，会话配置已保留；请重试停止"
            }
        }
        stopTask = shutdown
        await shutdown.value
        stopTask = nil
    }

    private func restorationMessage(_ result: SystemProxyManager.RestoreResult) async -> String {
        switch result {
        case .restored: "代理已停止，系统代理恢复完成"
        case .preservedChanges: "代理已停止；已保留后来修改的网络设置"
        case .failed: "代理已停止，但部分系统设置尚未恢复：\(await systemProxy.lastError ?? "请检查网络设置权限")"
        }
    }

    private func terminateCurrentProcess() async {
        activeLaunchID = nil
        guard let task = process else { return }
        task.terminate()
        await Task.detached {
            for _ in 0..<30 where task.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
            if task.isRunning { kill(task.processIdentifier, SIGKILL) }
            for _ in 0..<20 where task.isRunning { try? await Task.sleep(for: .milliseconds(50)) }
        }.value
        if !task.isRunning { process = nil }
    }

    private func cleanupRuntime() {
        guard process == nil, let runtime else { return }
        do {
            if try runtime.directory.removeAfterCoreExit() { self.runtime = nil }
            else { message += " · 运行目录含未知文件，已保留" }
        } catch { message += " · 会话配置清理失败，下次启动将重试" }
    }

    private var executableURL: URL? {
        let candidates = [
            providedExecutable,
            ProcessInfo.processInfo.environment["WEAVE_MIHOMO_PATH"].map(URL.init(fileURLWithPath:)),
            Bundle.main.url(forAuxiliaryExecutable: "mihomo"),
            Bundle.main.resourceURL?.appendingPathComponent("mihomo"),
        ].compactMap { $0 }
        return candidates.first { FileManager.default.isExecutableFile(atPath: $0.path) }
    }

    nonisolated static func prepareRuntime(
        selected: MacSubscription,
        selectedNodeName: String?,
        enableTun: Bool,
        root: URL = RuntimeDirectory.defaultRoot,
    ) throws -> MacRuntime {
        _ = try RuntimeDirectory.reclaimOrphans(root: root)
        let directory = try RuntimeDirectory(root: root)
        var prepared = false
        defer { if !prepared { _ = try? directory.removeAfterCoreExit() } }
        let base = directory.url
        let mixedPort = try unusedPort()
        var controllerPort = try unusedPort()
        while controllerPort == mixedPort { controllerPort = try unusedPort() }
        let runtime = MacRuntime(directory: directory, mixedPort: mixedPort, controllerPort: controllerPort,
                                 secret: UUID().uuidString + UUID().uuidString,
                                 tunDevice: "utun\(Int.random(in: 1_000...60_000))")
        let providers = base.appendingPathComponent("providers", isDirectory: true)
        try FileManager.default.createDirectory(at: providers, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let sanitized = try ClashProviderSanitizer.sanitize(selected.payload)
        let provider = providers.appendingPathComponent("\(selected.id.uuidString).yaml")
        try Data(sanitized.utf8).write(to: provider, options: .atomic)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: provider.path)
        var yaml = """
        mixed-port: \(mixedPort)
        external-controller: '127.0.0.1:\(controllerPort)'
        secret: '\(runtime.secret)'
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
              device: \(runtime.tunDevice)
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
        yaml += """
          '\(selected.id.uuidString)':
            type: file
            path: './providers/\(selected.id.uuidString).yaml'
        proxy-groups:

        """
        if selectedNodeName == nil {
            yaml += """
              - name: '\(selected.id.uuidString).auto'
                type: url-test
                use:
                  - '\(selected.id.uuidString)'
                url: 'https://www.gstatic.com/generate_204'
                interval: 300
                lazy: true

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
          - MATCH,\(selectedTarget)
        """
        try yaml.write(
            to: base.appendingPathComponent("config.yaml"),
            atomically: true,
            encoding: .utf8,
        )
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: base.appendingPathComponent("config.yaml").path)
        prepared = true
        return runtime
    }

    nonisolated static func waitUntilReady(runtime: MacRuntime, pid: Int32, requireTun: Bool) async throws {
        let config = URLSessionConfiguration.ephemeral
        config.connectionProxyDictionary = ["HTTPEnable": 0, "HTTPSEnable": 0, "SOCKSEnable": 0]
        config.timeoutIntervalForRequest = 1
        let session = URLSession(configuration: config)
        defer { session.invalidateAndCancel() }
        for _ in 0..<30 {
            try Task.checkCancellation()
            var request = URLRequest(url: URL(string: "http://127.0.0.1:\(runtime.controllerPort)/configs")!)
            request.setValue("Bearer \(runtime.secret)", forHTTPHeaderField: "Authorization")
            if let (data, response) = try? await session.data(for: request),
               (response as? HTTPURLResponse)?.statusCode == 200,
               let values = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               values["mixed-port"] as? Int == Int(runtime.mixedPort),
               listenerBelongsToProcess(port: runtime.mixedPort, pid: pid),
               (!requireTun || (isTunInterfacePresent(runtime.tunDevice) && hasDualStackRoutes(runtime.tunDevice))) {
                return
            }
            try await Task.sleep(for: .milliseconds(200))
        }
        throw WeaveMacError.message(requireTun
            ? "本会话内核、代理端口或双栈 TUN 路由未就绪，已拒绝连接"
            : "本会话内核未能就绪，请检查端口与配置")
    }

    nonisolated static func listenerBelongsToProcess(port: UInt16, pid: Int32) -> Bool {
        guard let output = commandOutput(executable: "/usr/sbin/lsof", arguments: ["-nP", "-a", "-p", String(pid), "-iTCP:\(port)", "-sTCP:LISTEN", "-Fp"]) else { return false }
        return output.split(separator: "\n").contains("p\(pid)")
    }

    nonisolated private static func isTunInterfacePresent(_ device: String) -> Bool {
        guard let output = commandOutput(
            executable: "/sbin/ifconfig",
            arguments: [device],
        ) else { return false }
        return output.contains("\(device):")
    }

    /// Both halves of each family must use this exact interface; a private /16 is not a /1.
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

        return TunRouteCoverage.isComplete(ipv4: ipv4, ipv6: ipv6, device: device)
    }

    nonisolated private static func commandOutput(
        executable: String,
        arguments: [String],
    ) -> String? {
        try? BoundedCommand.run(executable: executable, arguments: arguments, timeout: 2)
    }

    nonisolated private static func unusedPort() throws -> UInt16 {
        let descriptor = socket(AF_INET, SOCK_STREAM, 0)
        guard descriptor >= 0 else { throw WeaveMacError.message("无法分配本地代理端口") }
        defer { close(descriptor) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_addr.s_addr = inet_addr("127.0.0.1")
        return try withUnsafeMutablePointer(to: &address) { pointer in
            try pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sock in
                guard bind(descriptor, sock, socklen_t(MemoryLayout<sockaddr_in>.size)) == 0 else {
                    throw WeaveMacError.message("无法绑定本地代理端口")
                }
                var size = socklen_t(MemoryLayout<sockaddr_in>.size)
                guard getsockname(descriptor, sock, &size) == 0 else { throw WeaveMacError.message("无法读取本地端口") }
                return UInt16(bigEndian: pointer.pointee.sin_port)
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
