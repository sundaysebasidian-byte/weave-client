import Combine
import Darwin
import Foundation

@MainActor
final class MihomoController: ObservableObject {
    @Published private(set) var state: MacConnectionState = .stopped
    @Published private(set) var message = "完整 VPN 需要已签名的 Network Extension；当前可使用本地系统代理"

    private var process: Process?
    private var startTask: Task<Void, Never>?
    private let systemProxy = SystemProxyManager()

    var coreAvailable: Bool { executableURL != nil }

    func start(
        subscriptions: [MacSubscription],
        selectedSubscriptionID: UUID?,
        selectedNodeName: String?,
        availableNodeNames: [String]
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
        message = "正在准备本地代理"
        startTask?.cancel()
        startTask = Task {
            do {
                let runtime = try await Task.detached(priority: .userInitiated) {
                    try Self.prepareRuntime(
                        subscriptions,
                        selected: selected,
                        selectedNodeName: selectedNodeName
                    )
                }.value
                guard !Task.isCancelled else { return }
                try await launch(executableURL: executableURL, runtime: runtime)
            } catch is CancellationError {
                return
            } catch {
                state = .failed
                message = error.localizedDescription
            }
        }
    }

    private func launch(executableURL: URL, runtime: URL) async throws {
        let task = Process()
        task.executableURL = executableURL
        task.arguments = ["-d", runtime.path, "-f", runtime.appendingPathComponent("config.yaml").path]
        task.standardOutput = FileHandle.nullDevice
        task.standardError = FileHandle.nullDevice
        task.terminationHandler = { [weak self] _ in
            Task { @MainActor in
                self?.process = nil
                if self?.state != .stopped {
                    self?.state = .failed
                    self?.systemProxy.restore()
                    self?.message = "Mihomo 已停止，系统代理已恢复"
                }
            }
        }
        try task.run()
        process = task
        do {
            try await Self.waitUntilReady()
            try systemProxy.enable(port: 7890)
        } catch {
            task.terminate()
            process = nil
            systemProxy.restore()
            throw error
        }
        state = .localProxy
        message = "系统 HTTP/HTTPS/SOCKS 已接管 · 127.0.0.1:7890"
    }

    func stop() {
        startTask?.cancel()
        startTask = nil
        state = .stopped
        message = "本地代理已停止"
        systemProxy.restore()
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
        selectedNodeName: String?
    ) throws -> URL {
        let base = FileManager.default.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        ).first!.appendingPathComponent("Weave/runtime", isDirectory: true)
        try? FileManager.default.removeItem(at: base)
        let providers = base.appendingPathComponent("providers", isDirectory: true)
        try FileManager.default.createDirectory(at: providers, withIntermediateDirectories: true)
        for subscription in subscriptions {
            let sanitized = try ClashProviderSanitizer.sanitize(subscription.payload)
            try sanitized.write(
                to: providers.appendingPathComponent("\(subscription.id.uuidString).yaml"),
                atomically: true,
                encoding: .utf8
            )
        }
        var yaml = """
        mixed-port: 7890
        allow-lan: false
        mode: rule
        log-level: warning
        ipv6: true
        proxy-providers:

        """
        for subscription in subscriptions {
            yaml += """
              '\(subscription.id.uuidString)':
                type: file
                path: './providers/\(subscription.id.uuidString).yaml'
                health-check:
                  enable: true
                  url: https://www.gstatic.com/generate_204
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
                url: https://www.gstatic.com/generate_204
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
          - MATCH,'\(selectedTarget)'
        """
        try yaml.write(
            to: base.appendingPathComponent("config.yaml"),
            atomically: true,
            encoding: .utf8
        )
        return base
    }

    nonisolated private static func waitUntilReady() async throws {
        for _ in 0..<40 {
            try Task.checkCancellation()
            if isPortOpen(7890) { return }
            try await Task.sleep(for: .milliseconds(100))
        }
        throw WeaveMacError.message("Mihomo 未能在本机代理端口就绪")
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
