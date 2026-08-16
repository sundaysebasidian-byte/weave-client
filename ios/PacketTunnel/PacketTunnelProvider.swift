import Foundation
@preconcurrency import NetworkExtension
import WeaveCore

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let core = MobileCoreBridge()
    private var containerURL: URL?

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        let completion = TunnelCompletion(completionHandler)
        do {
            guard core.isAvailable else { throw MobileCoreBridgeError.frameworkMissing }
            guard let container = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: "group.io.weave.client"
            ) else {
                throw WeaveError.message("Packet Tunnel 无法访问共享 App Group")
            }
            containerURL = container
            let descriptor = try decodeDescriptor(options: options)
            Task { [weak self] in
                guard let self else {
                    completion.finish(WeaveError.message("Packet Tunnel 已释放"))
                    return
                }
                do {
                    try await self.start(descriptor: descriptor, container: container)
                    completion.finish(nil)
                } catch {
                    completion.finish(error)
                }
            }
        } catch {
            completion.finish(error)
        }
    }

    private func start(descriptor: RuntimeLaunchDescriptor, container: URL) async throws {
        let runtime = try RuntimeConfigCompiler.validateStagedRuntime(
            descriptor: descriptor,
            in: container
        )

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "198.18.0.1")
        let ipv4 = NEIPv4Settings(
            addresses: ["198.18.0.1"],
            subnetMasks: ["255.255.0.0"]
        )
        ipv4.includedRoutes = [.default()]
        ipv4.excludedRoutes = [
            NEIPv4Route(destinationAddress: "127.0.0.0", subnetMask: "255.0.0.0"),
            NEIPv4Route(destinationAddress: "169.254.0.0", subnetMask: "255.255.0.0"),
        ]
        settings.ipv4Settings = ipv4

        var dnsServers = ["198.18.0.2"]
        if descriptor.ipv6Enabled {
            let ipv6 = NEIPv6Settings(addresses: ["fdfe:dcba:9876::1"], networkPrefixLengths: [64])
            ipv6.includedRoutes = [.default()]
            settings.ipv6Settings = ipv6
            dnsServers.append("fdfe:dcba:9876::2")
        }
        settings.dnsSettings = NEDNSSettings(servers: dnsServers)
        settings.mtu = 8_500

        try await setTunnelNetworkSettings(settings)
        do {
            try await core.start(configurationDirectory: runtime, packetFlow: packetFlow)
        } catch {
            await core.stop()
            RuntimeConfigCompiler.clearRuntime(in: container)
            throw error
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason) async {
        await core.stop()
        if let containerURL { RuntimeConfigCompiler.clearRuntime(in: containerURL) }
    }

    override func handleAppMessage(_ messageData: Data) async -> Data? {
        core.statusData()
    }

    private func decodeDescriptor(options: [String: NSObject]?) throws -> RuntimeLaunchDescriptor {
        let optionData = options?["runtimeDescriptor"] as? Data
        let configuredData = (protocolConfiguration as? NETunnelProviderProtocol)?
            .providerConfiguration?["runtimeDescriptor"] as? Data
        guard let data = optionData ?? configuredData else {
            throw WeaveError.message("Packet Tunnel 缺少运行清单")
        }
        do { return try JSONDecoder().decode(RuntimeLaunchDescriptor.self, from: data) }
        catch { throw WeaveError.message("Packet Tunnel 运行清单无法解析") }
    }
}

private final class TunnelCompletion: @unchecked Sendable {
    private let lock = NSLock()
    private var callback: ((Error?) -> Void)?

    init(_ callback: @escaping (Error?) -> Void) {
        self.callback = callback
    }

    func finish(_ error: Error?) {
        lock.lock()
        guard let callback else {
            lock.unlock()
            return
        }
        self.callback = nil
        lock.unlock()
        callback(error)
    }
}
