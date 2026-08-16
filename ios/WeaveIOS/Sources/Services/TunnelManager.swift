import Foundation
import NetworkExtension
import WeaveCore

@MainActor
final class TunnelManager: ObservableObject {
    static let providerBundleIdentifier = "io.weave.client.ios.PacketTunnel"

    @Published private(set) var status: NEVPNStatus = .invalid
    @Published private(set) var message = "尚未配置系统 VPN"

    private var manager: NETunnelProviderManager?
    private var observer: NSObjectProtocol?

    init() {
        observer = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refreshStatus() }
        }
    }

    deinit {
        if let observer { NotificationCenter.default.removeObserver(observer) }
    }

    var isActive: Bool { status == .connected || status == .connecting || status == .reasserting }
    var isBusy: Bool { status == .connecting || status == .disconnecting || status == .reasserting }

    var statusLabel: String {
        switch status {
        case .connected: "已保护"
        case .connecting: "正在连接"
        case .disconnecting: "正在断开"
        case .reasserting: "正在恢复"
        case .disconnected: "未连接"
        case .invalid: "未配置"
        @unknown default: "未知状态"
        }
    }

    func prepare() async {
        do {
            let managers = try await NETunnelProviderManager.loadAllFromPreferences()
            manager = managers.first(where: {
                ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier
                    == Self.providerBundleIdentifier
            })
            refreshStatus()
        } catch {
            message = "无法读取系统 VPN 配置：\(error.localizedDescription)"
        }
    }

    func start(descriptor: RuntimeLaunchDescriptor) async throws {
        let manager = manager ?? NETunnelProviderManager()
        let tunnelProtocol = (manager.protocolConfiguration as? NETunnelProviderProtocol)
            ?? NETunnelProviderProtocol()
        tunnelProtocol.providerBundleIdentifier = Self.providerBundleIdentifier
        tunnelProtocol.serverAddress = "Weave Private Network"
        tunnelProtocol.disconnectOnSleep = false
        let descriptorData = try JSONEncoder().encode(descriptor)
        tunnelProtocol.providerConfiguration = [
            "schemaVersion": descriptor.schemaVersion,
            "runtimeDescriptor": descriptorData,
        ]
        manager.localizedDescription = "Weave"
        manager.protocolConfiguration = tunnelProtocol
        manager.isEnabled = true
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
        self.manager = manager
        do {
            try manager.connection.startVPNTunnel(options: ["runtimeDescriptor": descriptorData as NSData])
            message = "正在请求 Packet Tunnel"
            refreshStatus()
        } catch {
            message = error.localizedDescription
            throw error
        }
    }

    func stop() {
        manager?.connection.stopVPNTunnel()
        message = "正在断开"
        refreshStatus()
    }

    private func refreshStatus() {
        status = manager?.connection.status ?? .invalid
        switch status {
        case .connected: message = "系统 Packet Tunnel 正在保护流量"
        case .disconnected: message = "系统 VPN 已断开"
        case .invalid: message = "首次连接时将创建系统 VPN 配置"
        default: break
        }
    }
}
