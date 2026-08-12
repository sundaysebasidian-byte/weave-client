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
    case localProxy = "本地代理"
    case failed = "错误"
}

enum WeaveMacError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case let .message(message): message
        }
    }
}
