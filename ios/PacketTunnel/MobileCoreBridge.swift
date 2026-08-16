import Foundation
@preconcurrency import NetworkExtension

#if canImport(WeaveMihomoMobile)
import WeaveMihomoMobile
#endif

enum MobileCoreBridgeError: LocalizedError {
    case frameworkMissing

    var errorDescription: String? {
        switch self {
        case .frameworkMissing:
            "未嵌入 WeaveMihomoMobile.xcframework；Packet Tunnel 已安全拒绝启动"
        }
    }
}

/// Narrow boundary between Apple's Packet Tunnel and the audited mobile core.
///
/// The optional framework must expose `PacketEngine` with the initializer and
/// lifecycle below. Keeping the bridge in one file prevents the SwiftUI/data
/// layers from depending on a particular Go-mobile binding.
final class MobileCoreBridge {
    #if canImport(WeaveMihomoMobile)
    private var engine: WeaveMihomoMobile.PacketEngine?
    #endif

    var isAvailable: Bool {
        #if canImport(WeaveMihomoMobile)
        true
        #else
        false
        #endif
    }

    func start(configurationDirectory: URL, packetFlow: NEPacketTunnelFlow) async throws {
        #if canImport(WeaveMihomoMobile)
        let engine = try WeaveMihomoMobile.PacketEngine(
            homeDirectory: configurationDirectory.path,
            configurationPath: configurationDirectory.appendingPathComponent("config.yaml").path,
            packetFlow: packetFlow
        )
        try await engine.start()
        self.engine = engine
        #else
        throw MobileCoreBridgeError.frameworkMissing
        #endif
    }

    func stop() async {
        #if canImport(WeaveMihomoMobile)
        await engine?.stop()
        engine = nil
        #endif
    }

    func statusData() -> Data {
        #if canImport(WeaveMihomoMobile)
        engine?.statusData() ?? Data("{\"running\":false}".utf8)
        #else
        Data("{\"running\":false,\"coreAvailable\":false}".utf8)
        #endif
    }
}
