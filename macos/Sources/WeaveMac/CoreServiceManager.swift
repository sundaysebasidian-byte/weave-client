import Combine
import Foundation
import ServiceManagement
import WeaveCoreBroker
import WeaveCoreRuntime

@MainActor
protocol CoreServiceRegistration {
    var status: SMAppService.Status { get }
    func register() throws
    func unregister() async throws
    func openSettings()
}

@MainActor
private final class SystemCoreServiceRegistration: CoreServiceRegistration {
    private let service = SMAppService.daemon(plistName: BrokerIdentity.serviceName + ".plist")
    var status: SMAppService.Status { service.status }
    func register() throws { try service.register() }
    func unregister() async throws { try await service.unregister() }
    func openSettings() { SMAppService.openSystemSettingsLoginItems() }
}

@MainActor
final class CoreServiceManager: ObservableObject {
    @Published private(set) var status: SMAppService.Status = .notRegistered
    @Published private(set) var busy = false
    @Published private(set) var message = ""
    @Published private(set) var identityValid = false
    private let registration: any CoreServiceRegistration
    private let validateIdentity: () throws -> Void

    init(registration: (any CoreServiceRegistration)? = nil,
         validateIdentity: @escaping () throws -> Void = {
             _ = try BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.serviceName,
                                                          ownIdentifier: BrokerIdentity.appIdentifier)
         }) {
        self.registration = registration ?? SystemCoreServiceRegistration()
        self.validateIdentity = validateIdentity
        refresh()
    }

    func refresh() {
        status = registration.status
        do { try validateIdentity(); identityValid = true }
        catch { identityValid = false }
        guard identityValid else {
            message = "应用签名未通过正式身份验证，TUN 服务不可用；本地系统代理仍可使用"
            return
        }
        message = switch status {
        case .notRegistered: "尚未启用 TUN 服务"
        case .enabled: "TUN 服务已获授权"
        case .requiresApproval: "请在系统设置的登录项与扩展中允许 Weave 后台服务"
        case .notFound: "TUN 服务文件不可用，请重新安装完整应用"
        @unknown default: "无法确认 TUN 服务状态"
        }
    }

    func requireEnabled() throws {
        refresh()
        guard !busy else { throw WeaveMacError.message("服务状态正在变更，请稍后再连接") }
        guard identityValid, status == .enabled else { throw WeaveMacError.message(message) }
    }

    func enable() {
        guard !busy else { return }
        refresh()
        guard identityValid else { return }
        busy = true
        defer { busy = false }
        do {
            if status == .notRegistered { try registration.register() }
            refresh()
            if status == .requiresApproval { registration.openSettings() }
        } catch { refresh(); message = "启用 TUN 服务失败：\(error.localizedDescription)" }
    }

    func disable() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        do {
            if registration.status != .notRegistered { try await registration.unregister() }
            refresh()
        } catch { refresh(); message = "停用 TUN 服务失败：\(error.localizedDescription)" }
    }

    func openSettings() { registration.openSettings() }
}

protocol TunBrokerChannel: Sendable {
    func send(_ request: BrokerRequest) async throws -> BrokerReply
    func close()
}
extension BrokerClient: TunBrokerChannel {}

extension BrokerFailure: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .busy: "另一连接正在使用 TUN 服务，或上一会话尚在退出"
        case .unauthorized: "TUN 服务身份验证失败，请安装同一开发者签名的完整版本"
        case .invalidRequest, .invalidProfile: "订阅配置不受 TUN 服务支持，请检查节点配置"
        case .startFailed: "TUN 核心或双栈路由未能就绪"
        case .stopFailed: "TUN 核心尚未确认退出，请重试停止"
        case .dnsFailed: "当前 TUN 会话的 DNS 查询失败"
        case .unavailable: "TUN 服务不可用，请检查后台服务授权"
        }
    }
}
