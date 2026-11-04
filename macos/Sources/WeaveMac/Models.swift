import Foundation

struct MacSubscription: Codable, Identifiable, Hashable, Sendable {
    var id: UUID
    var name: String
    var source: String
    var payload: String
    var nodeCount: Int
    var updatedAt: Date
}

struct TransferSubscription: Equatable, Sendable {
    let name: String
    let source: String
    let payload: String
}

enum MacConnectionState: String {
    case stopped = "未连接"
    case starting = "启动中"
    case stopping = "恢复网络设置中"
    case tun = "全设备 TUN"
    case localProxy = "本地代理"
    case failed = "错误"
}

extension MacConnectionState {
    var isActive: Bool { isConnected || self == .starting || self == .stopping }

    var isConnected: Bool { self == .tun || self == .localProxy }
}
