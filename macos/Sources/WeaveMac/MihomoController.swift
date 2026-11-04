import WeaveCoreRuntime
import WeaveCoreBroker
import Combine
import Foundation

@MainActor
final class MihomoController: ObservableObject {
    @Published private(set) var state: MacConnectionState = .stopped
    @Published private(set) var message = "尚未连接"

    private var process: CoreProcess?
    private var runtime: MacRuntime?
    private var startTask: Task<Void, Never>?
    private var stopTask: (id: UUID, task: Task<Void, Never>)?
    private var operationID = UUID()
    private var activeLaunchID: UUID?
    private let systemProxy: SystemProxyManager
    private let providedExecutable: URL?
    private let runtimeRoot: URL
    let serviceManager: CoreServiceManager
    private let brokerFactory: (@escaping @Sendable () -> Void) throws -> any TunBrokerChannel
    private var broker: (any TunBrokerChannel)?
    private var brokerID: UUID?
    private var serviceMonitor: Task<Void, Never>?
    private var serviceStopUnconfirmed = false

    init(systemProxy: SystemProxyManager = SystemProxyManager(), executable: URL? = nil, runtimeRoot: URL = RuntimeDirectory.defaultRoot,
         serviceManager: CoreServiceManager? = nil,
         brokerFactory: @escaping (@escaping @Sendable () -> Void) throws -> any TunBrokerChannel = { try BrokerClient(onDisconnect: $0) }) {
        self.systemProxy = systemProxy
        providedExecutable = executable
        self.runtimeRoot = runtimeRoot
        self.serviceManager = serviceManager ?? CoreServiceManager()
        self.brokerFactory = brokerFactory
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

    var hasOwnedSession: Bool { process != nil || broker != nil }

    var coreAvailable: Bool { executableURL != nil }
    var processIdentifier: Int32? { process?.processIdentifier }

    var subscriptionHostResolver: SubscriptionHostResolver? {
        if state == .tun, let broker {
            return { host in
                switch try await broker.send(.resolve(host: host)) {
                case let .addresses(addresses): return addresses
                case let .failure(error): throw error
                default: throw BrokerTransportError.invalidReply
                }
            }
        }
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
        guard process == nil, broker == nil, stopTask == nil, !state.isActive else { return }
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
        if serviceStopUnconfirmed && !preferTun {
            state = .failed
            message = "上一 TUN 会话尚未确认停止，请先检查后台服务后再切换模式"
            return
        }
        if preferTun {
            startService(provider: selected.payload, node: selectedNodeName)
            return
        }
        guard let executableURL else {
            state = .failed
            message = "未找到 Apple Silicon Mihomo 核心；连接入口保持关闭"
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

    private func startService(provider: String, node: String?) {
        do { try serviceManager.requireEnabled() }
        catch { state = .failed; message = error.localizedDescription; return }
        state = .starting
        message = "正在通过已授权服务建立双栈 TUN"
        let operation = UUID()
        operationID = operation
        let identity = UUID()
        brokerID = identity
        startTask = Task {
            do {
                let channel = try brokerFactory { [weak self] in
                    Task { @MainActor in self?.serviceDisconnected(identity) }
                }
                broker = channel
                let provider = try await Task.detached(priority: .userInitiated) {
                    try ClashProviderSanitizer.sanitize(provider)
                }.value
                try Task.checkCancellation()
                let reply = try await channel.send(.start(provider: provider, node: node))
                try Task.checkCancellation()
                guard operationID == operation, brokerID == identity else { return }
                switch reply {
                case .state(.running, _):
                    serviceStopUnconfirmed = false
                    state = .tun
                    message = "双栈 TUN 路由已就绪 · 外网连通性尚未验证"
                    serviceMonitor = Task { [weak self] in
                        while !Task.isCancelled {
                            do { try await Task.sleep(for: .seconds(2)) } catch { return }
                            guard let self, self.brokerID == identity else { return }
                            await self.checkServiceConnection()
                        }
                    }
                case let .failure(error): throw error
                default: throw BrokerTransportError.invalidReply
                }
            } catch {
                guard operationID == operation, brokerID == identity else { return }
                // Transport failure may precede remote cleanup; never claim it is stopped.
                if error as? BrokerFailure == .stopFailed {
                    serviceStopUnconfirmed = true
                } else {
                    serviceStopUnconfirmed = !(error is BrokerFailure)
                    closeBroker()
                }
                state = .failed
                message = "TUN 启动失败 · " + error.localizedDescription
            }
        }
    }

    func checkServiceConnection() async {
        guard state == .tun, let broker, let identity = brokerID else { return }
        do {
            let reply = try await broker.send(.status)
            guard brokerID == identity, state == .tun else { return }
            guard case .state(.running, _) = reply else {
                serviceStopUnconfirmed = reply != .state(.stopped, mixedPort: nil)
                serviceMonitor?.cancel()
                serviceMonitor = nil
                if !serviceStopUnconfirmed { closeBroker() }
                state = .failed
                message = "TUN 核心已停止或发生故障，请检查服务后重新连接"
                return
            }
        } catch {
            // Stopping cancels this query while preserving the channel for the stop RPC.
            guard !Task.isCancelled, state == .tun, brokerID == identity else { return }
            serviceDisconnected(identity)
        }
    }

    private func serviceDisconnected(_ identity: UUID) {
        guard brokerID == identity else { return }
        serviceStopUnconfirmed = true
        closeBroker()
        state = .failed
        serviceManager.refresh()
        message = "TUN 服务连接中断，停止结果尚未确认；请检查后台服务状态"
    }

    private func closeBroker() {
        brokerID = nil
        let channel = broker
        broker = nil
        serviceMonitor?.cancel()
        serviceMonitor = nil
        channel?.close()
    }

    func stop() async {
        let operation: (id: UUID, task: Task<Void, Never>)
        if let stopTask { operation = stopTask }
        else {
            operationID = UUID()
            startTask?.cancel()
            let pendingStart = startTask
            serviceMonitor?.cancel()
            state = .stopping
            message = "正在停止代理并恢复网络设置"
            let shutdown = Task {
                await terminateCurrentProcess()
                await pendingStart?.value
                startTask = nil
                if let channel = broker {
                    do {
                        let reply = try await channel.send(.stop)
                        guard reply == .state(.stopped, mixedPort: nil) else {
                            state = .failed
                            message = "TUN 核心尚未确认退出，请重试停止"
                            return
                        }
                        serviceStopUnconfirmed = false
                        closeBroker()
                    } catch {
                        serviceStopUnconfirmed = true
                        closeBroker()
                    }
                }
                let result = await systemProxy.restore()
                message = await restorationMessage(result)
                cleanupRuntime()
                state = result == .failed ? .failed : .stopped
                if process != nil || serviceStopUnconfirmed {
                    state = .failed
                    message = "核心尚未确认退出；请检查服务状态或重试停止"
                }
            }
            operation = (UUID(), shutdown)
            stopTask = operation
        }
        await operation.task.value
        if stopTask?.id == operation.id { stopTask = nil }
    }

    func disableService() async {
        await stop()
        guard state == .stopped else {
            message = "请先确认当前核心已停止，再停用 TUN 服务"
            return
        }
        await serviceManager.disable()
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
