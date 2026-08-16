import Foundation

public enum WeaveError: LocalizedError, Equatable, Sendable {
    case message(String)

    public var errorDescription: String? {
        switch self {
        case let .message(message): message
        }
    }
}

public struct WeaveSubscription: Codable, Identifiable, Hashable, Sendable {
    public var id: UUID
    public var name: String
    public var source: String
    public var payload: String
    public var nodeCount: Int
    public var updatedAt: Date

    public init(
        id: UUID = UUID(),
        name: String,
        source: String,
        payload: String,
        nodeCount: Int,
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.source = source
        self.payload = payload
        self.nodeCount = nodeCount
        self.updatedAt = updatedAt
    }
}

public struct TransferSubscription: Equatable, Sendable {
    public let name: String
    public let source: String
    public let payload: String

    public init(name: String, source: String, payload: String) {
        self.name = name
        self.source = source
        self.payload = payload
    }
}

public enum RoutingMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case rule
    case global
    case direct

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .rule: "规则"
        case .global: "全局"
        case .direct: "直连"
        }
    }
}

public enum AutomaticStrategy: String, Codable, CaseIterable, Identifiable, Sendable {
    case lowestLatency
    case fallback
    case loadBalance

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .lowestLatency: "最低延迟"
        case .fallback: "故障切换"
        case .loadBalance: "负载均衡"
        }
    }

    public var mihomoType: String {
        switch self {
        case .lowestLatency: "url-test"
        case .fallback: "fallback"
        case .loadBalance: "load-balance"
        }
    }
}

public enum DNSProfile: String, Codable, CaseIterable, Identifiable, Sendable {
    case privacy
    case adBlock
    case family
    case custom

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .privacy: "隐私"
        case .adBlock: "广告过滤"
        case .family: "家庭过滤"
        case .custom: "自定义"
        }
    }
}

public enum IPv6Mode: String, Codable, CaseIterable, Identifiable, Sendable {
    case dualStack
    case ipv4Only

    public var id: String { rawValue }
    public var label: String { self == .dualStack ? "IPv4 + IPv6" : "仅 IPv4" }
}

public enum WeavePalette: String, Codable, CaseIterable, Identifiable, Sendable {
    case impressionSunrise
    case waterLilies
    case poppyField
    case twilightGarden

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .impressionSunrise: "日出·印象"
        case .waterLilies: "睡莲"
        case .poppyField: "罂粟田"
        case .twilightGarden: "暮色花园"
        }
    }
}

public enum RouteTarget: Codable, Hashable, Sendable {
    case automatic(subscriptionID: UUID)
    case fixed(subscriptionID: UUID, nodeName: String)
    case direct
    case block

    public var subscriptionID: UUID? {
        switch self {
        case let .automatic(subscriptionID), let .fixed(subscriptionID, _): subscriptionID
        case .direct, .block: nil
        }
    }
}

public struct DomainRouteRule: Codable, Identifiable, Hashable, Sendable {
    public var id: UUID
    public var domainSuffix: String
    public var target: RouteTarget

    public init(id: UUID = UUID(), domainSuffix: String, target: RouteTarget) {
        self.id = id
        self.domainSuffix = domainSuffix
        self.target = target
    }
}

public struct RuntimePreferences: Codable, Equatable, Sendable {
    public var routingMode: RoutingMode
    public var automaticStrategy: AutomaticStrategy
    public var dnsProfile: DNSProfile
    public var customDNSEndpoint: String
    public var ipv6Mode: IPv6Mode
    public var blockSTUN: Bool
    public var directMainlandChina: Bool

    public init(
        routingMode: RoutingMode = .rule,
        automaticStrategy: AutomaticStrategy = .lowestLatency,
        dnsProfile: DNSProfile = .privacy,
        customDNSEndpoint: String = "",
        ipv6Mode: IPv6Mode = .dualStack,
        blockSTUN: Bool = true,
        directMainlandChina: Bool = false
    ) {
        self.routingMode = routingMode
        self.automaticStrategy = automaticStrategy
        self.dnsProfile = dnsProfile
        self.customDNSEndpoint = customDNSEndpoint
        self.ipv6Mode = ipv6Mode
        self.blockSTUN = blockSTUN
        self.directMainlandChina = directMainlandChina
    }
}

public struct RuntimeSelection: Codable, Equatable, Sendable {
    public var subscriptionID: UUID
    public var nodeName: String?

    public init(subscriptionID: UUID, nodeName: String? = nil) {
        self.subscriptionID = subscriptionID
        self.nodeName = nodeName
    }
}

public struct ImportedSubscriptionPayload: Sendable {
    public let suggestedName: String
    public let source: String
    public let payload: String

    public init(suggestedName: String, source: String, payload: String) {
        self.suggestedName = suggestedName
        self.source = source
        self.payload = payload
    }
}
