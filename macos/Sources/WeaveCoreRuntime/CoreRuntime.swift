import Darwin
import Foundation

package enum CoreRuntime {
    package static func prepare(
        provider: String,
        selectedNodeName: String?,
        enableTun: Bool,
        root: URL = RuntimeDirectory.defaultRoot,
    ) throws -> MacRuntime {
        let document = try ClashProviderDocument(provider)
        if let selectedNodeName, !document.names.contains(selectedNodeName) {
            throw WeaveMacError.message("所选节点已不存在，请重新选择")
        }
        let sanitized = try document.sanitized()
        let providerID = UUID().uuidString
        _ = try RuntimeDirectory.reclaimOrphans(root: root)
        let directory = try RuntimeDirectory(root: root)
        var prepared = false
        defer { if !prepared { _ = try? directory.removeAfterCoreExit() } }
        let base = directory.url
        let mixedPort = try unusedPort()
        var controllerPort = try unusedPort()
        while controllerPort == mixedPort { controllerPort = try unusedPort() }
        var readinessListenPort = try unusedPort()
        while [mixedPort, controllerPort].contains(readinessListenPort) { readinessListenPort = try unusedPort() }
        var readinessTargetPort = try unusedPort()
        while [mixedPort, controllerPort, readinessListenPort].contains(readinessTargetPort) { readinessTargetPort = try unusedPort() }
        let runtime = MacRuntime(directory: directory, mixedPort: mixedPort, controllerPort: controllerPort,
                                 secret: UUID().uuidString + UUID().uuidString,
                                 tunDevice: "utun\(Int.random(in: 1_000...60_000))",
                                 readinessListenPort: readinessListenPort, readinessTargetPort: readinessTargetPort)
        let providers = base.appendingPathComponent("providers", isDirectory: true)
        try FileManager.default.createDirectory(at: providers, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let provider = providers.appendingPathComponent("\(providerID).yaml")
        try Data(sanitized.utf8).write(to: provider, options: .atomic)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: provider.path)
        var yaml = """
        mixed-port: \(mixedPort)
        external-controller: '127.0.0.1:\(controllerPort)'
        secret: '\(runtime.secret)'
        allow-lan: false
        mode: rule
        log-level: warning
        ipv6: true
        find-process-mode: strict
        unified-delay: true
        tcp-concurrent: true
        tunnels:
          - network: [tcp]
            address: 127.0.0.1:\(readinessListenPort)
            target: 127.0.0.1:\(readinessTargetPort)
            proxy: DIRECT

        """
        if enableTun {
            yaml += """
            tun:
              enable: true
              stack: mixed
              device: \(runtime.tunDevice)
              dns-hijack:
                - 'any:53'
              auto-route: true
              auto-detect-interface: true
              strict-route: true
              mtu: 1500
              route-address:
                - 0.0.0.0/1
                - 128.0.0.0/1
                - ::/1
                - 8000::/1
              route-exclude-address:
                - 10.0.0.0/8
                - 100.64.0.0/10
                - 127.0.0.0/8
                - 169.254.0.0/16
                - 172.16.0.0/12
                - 192.168.0.0/16
                - 224.0.0.0/4
                - ::1/128
                - fe80::/10
                - fc00::/7
                - ff00::/8

            """
        }
        yaml += """
        dns:
          enable: true
          ipv6: true
          enhanced-mode: fake-ip
          fake-ip-range: 198.18.0.1/16
          fake-ip-filter:
            - '*.lan'
            - '*.local'
            - localhost
          default-nameserver:
            - 223.5.5.5
            - 119.29.29.29
          nameserver:
            - 'https://doh.pub/dns-query'
            - 'https://dns.alidns.com/dns-query'
            - 'https://1.1.1.1/dns-query'
          proxy-server-nameserver:
            - 'https://doh.pub/dns-query'
            - 'https://dns.alidns.com/dns-query'
        sniffer:
          enable: true
          force-dns-mapping: true
          parse-pure-ip: true
          override-destination: false
          sniff:
            TLS:
              ports: [443, 8443]
            HTTP:
              ports: [80, 8080-8880]
            QUIC:
              ports: [443, 8443]
        proxy-providers:

        """
        yaml += """
          '\(providerID)':
            type: file
            path: './providers/\(providerID).yaml'
        proxy-groups:

        """
        if selectedNodeName == nil {
            yaml += """
              - name: '\(providerID).auto'
                type: url-test
                use:
                  - '\(providerID)'
                url: 'https://www.gstatic.com/generate_204'
                interval: 300
                lazy: true

            """
        }
        let selectedTarget: String
        if let selectedNodeName {
            selectedTarget = "\(providerID).fixed"
            yaml += """
              - name: '\(selectedTarget)'
                type: select
                use:
                  - '\(providerID)'
                filter: '\(yamlSingleQuoted(exactRegex(selectedNodeName)))'

            """
        } else {
            selectedTarget = "\(providerID).auto"
        }
        yaml += """
        rules:
          # Block standard STUN ports in strict privacy mode. Chrome can fall back to TCP
          # through the proxy instead of exposing a direct public address.
          - AND,((NETWORK,UDP),(DST-PORT,3478)),REJECT
          - AND,((NETWORK,UDP),(DST-PORT,5349)),REJECT
          - AND,((NETWORK,UDP),(DST-PORT,19302-19309)),REJECT
          - DOMAIN-SUFFIX,stun.l.google.com,REJECT
          - DOMAIN-SUFFIX,stun1.l.google.com,REJECT
          - MATCH,\(selectedTarget)
        """
        try yaml.write(
            to: base.appendingPathComponent("config.yaml"),
            atomically: true,
            encoding: .utf8,
        )
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: base.appendingPathComponent("config.yaml").path)
        prepared = true
        return runtime
    }

    package static func waitUntilReady(runtime: MacRuntime, pid: Int32, requireTun: Bool) async throws {
        let probe = try CoreReadinessProbe(port: runtime.readinessTargetPort)
        do {
            try await probe.start()
            try await waitForForwarding(runtime: runtime, pid: pid, requireTun: requireTun, probe: probe)
            await probe.stop()
        } catch {
            await probe.stop()
            try Task.checkCancellation()
            throw error
        }
    }

    private static func waitForForwarding(runtime: MacRuntime, pid: Int32, requireTun: Bool, probe: CoreReadinessProbe) async throws {
        let config = URLSessionConfiguration.ephemeral
        config.connectionProxyDictionary = ["HTTPEnable": 0, "HTTPSEnable": 0, "SOCKSEnable": 0]
        config.timeoutIntervalForRequest = 1
        config.timeoutIntervalForResource = 1
        let session = URLSession(configuration: config)
        defer { session.invalidateAndCancel() }
        for _ in 0..<30 {
            try Task.checkCancellation()
            var request = URLRequest(url: URL(string: "http://127.0.0.1:\(runtime.controllerPort)/configs")!)
            request.setValue("Bearer \(runtime.secret)", forHTTPHeaderField: "Authorization")
            if let (data, response) = try? await session.data(for: request),
               (response as? HTTPURLResponse)?.statusCode == 200,
               let values = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               values["mixed-port"] as? Int == Int(runtime.mixedPort),
               listenerBelongsToProcess(port: runtime.mixedPort, pid: pid),
               listenerBelongsToProcess(port: runtime.readinessListenPort, pid: pid),
               (!requireTun || (isTunInterfacePresent(runtime.tunDevice) && hasDualStackRoutes(runtime.tunDevice))) {
                if try await probe.isForwarding(port: runtime.readinessListenPort) { return }
            }
            try await Task.sleep(for: .milliseconds(200))
        }
        throw WeaveMacError.message(requireTun
            ? "本会话内核、代理端口或双栈 TUN 路由未就绪，已拒绝连接"
            : "本会话内核未能就绪，请检查端口与配置")
    }

    package static func listenerBelongsToProcess(port: UInt16, pid: Int32) -> Bool {
        guard let output = commandOutput(executable: "/usr/sbin/lsof", arguments: ["-nP", "-a", "-p", String(pid), "-iTCP:\(port)", "-sTCP:LISTEN", "-Fp"]) else { return false }
        return output.split(separator: "\n").contains("p\(pid)")
    }

    private static func isTunInterfacePresent(_ device: String) -> Bool {
        guard let output = commandOutput(
            executable: "/sbin/ifconfig",
            arguments: [device],
        ) else { return false }
        return output.contains("\(device):")
    }

    /// Both halves of each family must use this exact interface; a private /16 is not a /1.
    private static func hasDualStackRoutes(_ device: String) -> Bool {
        guard
            let ipv4 = commandOutput(
                executable: "/usr/sbin/netstat",
                arguments: ["-rn", "-f", "inet"],
            ),
            let ipv6 = commandOutput(
                executable: "/usr/sbin/netstat",
                arguments: ["-rn", "-f", "inet6"],
            )
        else { return false }

        return TunRouteCoverage.isComplete(ipv4: ipv4, ipv6: ipv6, device: device)
    }

    private static func commandOutput(
        executable: String,
        arguments: [String],
    ) -> String? {
        try? BoundedCommand.run(executable: executable, arguments: arguments, timeout: 2)
    }

    private static func unusedPort() throws -> UInt16 {
        let descriptor = socket(AF_INET, SOCK_STREAM, 0)
        guard descriptor >= 0 else { throw WeaveMacError.message("无法分配本地代理端口") }
        defer { close(descriptor) }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_addr.s_addr = inet_addr("127.0.0.1")
        return try withUnsafeMutablePointer(to: &address) { pointer in
            try pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sock in
                guard bind(descriptor, sock, socklen_t(MemoryLayout<sockaddr_in>.size)) == 0 else {
                    throw WeaveMacError.message("无法绑定本地代理端口")
                }
                var size = socklen_t(MemoryLayout<sockaddr_in>.size)
                guard getsockname(descriptor, sock, &size) == 0 else { throw WeaveMacError.message("无法读取本地端口") }
                return UInt16(bigEndian: pointer.pointee.sin_port)
            }
        }
    }

    private static func exactRegex(_ value: String) -> String {
        let meta = CharacterSet(charactersIn: "\\.^$|?*+()[]{}")
        return "^" + value.unicodeScalars.map {
            meta.contains($0) ? "\\\($0)" : String($0)
        }.joined() + "$"
    }

    private static func yamlSingleQuoted(_ value: String) -> String {
        value.replacingOccurrences(of: "'", with: "''")
    }
}
