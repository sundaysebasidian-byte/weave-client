import WeaveCoreRuntime
import Combine
import Foundation

@MainActor
final class MihomoController: ObservableObject {
    @Published private(set) var state: MacConnectionState = .stopped
    @Published private(set) var message = "尚未连接"

    private var process: CoreProcess?
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
                    let runtime = try CoreRuntime.prepare(
                        provider: try ClashProviderSanitizer.sanitize(selected.payload),
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
        let launchID = UUID()
        let task = try CoreProcess(executable: executableURL, runtime: runtime) { [weak self] in
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
        process = task
        activeLaunchID = launchID
        do {
            try await CoreRuntime.waitUntilReady(runtime: runtime, pid: task.processIdentifier, requireTun: enableTun)
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
        if await task.stop() { process = nil }
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

}
