import Darwin
import Foundation
import Yams

/// Closed capabilities for Mihomo e26714a. The caller must check the resulting inline
/// configuration with the actual core before starting TUN or listeners.
enum ProviderSecurity {
    static func prepare(_ payload: String) throws -> PreparedProvider {
        let document = try ClashProviderDocument(payload, providerOnly: true)
        _ = try document.sanitized() // Bound the full alias expansion before replacing material.
        var materials = Materials()
        var proxies: [Node] = []
        for original in document.proxies.sequence! {
            guard let type = original["type"]?.string, let rule = protocols[type] else { throw unsupported }
            var proxy = try rule.rewrite(original, materials: &materials)
            // Earlier Weave URI imports emitted this redundant flag. Trojan always uses TLS;
            // accepting false would silently promise a transport the pinned core ignores.
            if type == "trojan", let flag = proxy["tls"], flag.bool != true { throw unsupported }
            if type == "ss" {
                let plugin = proxy["plugin"]?.string ?? ""
                if plugin.isEmpty {
                    guard proxy["plugin-opts"] == nil else { throw unsupported }
                } else {
                    guard let rule = plugins[plugin] else { throw unsupported }
                    if let options = proxy["plugin-opts"] { proxy["plugin-opts"] = try rule.rewrite(options, materials: &materials) }
                }
            }
            if type == "ssh", let key = proxy["private-key"]?.string, !key.isEmpty {
                // This exact marker keeps the pinned SSH loader on its inline-only branch.
                try validatePEM(key, labels: ["PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY", "DSA PRIVATE KEY", "OPENSSH PRIVATE KEY"], headers: true, multiple: false)
            }
            if type == "wireguard" {
                guard let key = proxy["private-key"]?.string,
                      Data(base64Encoded: key.replacingOccurrences(of: "\r", with: "").replacingOccurrences(of: "\n", with: ""))?.count == 32 else { throw invalidMaterial }
            }
            proxies.append(proxy)
        }
        let yaml: String
        do { yaml = try Yams.serialize(node: Node([(Node("proxies", Tag(.str)), Node(proxies, Tag(.seq), .block))]), indent: 2, width: -1, allowUnicode: true) }
        catch { throw unsupported }
        guard yaml.utf8.count <= ClashProviderDocument.maxPayloadBytes else { throw unsupported }
        return PreparedProvider(names: document.names, yaml: yaml, materials: materials.files)
    }

    private static var unsupported: WeaveMacError { .message("节点包含尚未核验的协议、选项或结构，已取消连接；请检查配置") }
    private static var invalidMaterial: WeaveMacError { .message("节点证书和密钥必须使用内联内容，不能引用本机文件；TLS 客户端证书须提供配套私钥") }

    private indirect enum Rule: Sendable {
        case scalar, list(Rule), headers(lists: Bool), pluginOptions
        case options([String: Rule], tls: Bool)

        func rewrite(_ node: Node, materials: inout Materials) throws -> Node {
            switch self {
            case .scalar:
                guard case .scalar(let scalar) = node, node.tag != Tag(.null), scalar.string.utf8.count <= 65_536,
                      !scalar.string.utf8.contains(0) else { throw unsupported }
                return node
            case let .list(item):
                guard let sequence = node.sequence, sequence.count <= 1024 else { throw unsupported }
                return Node(try sequence.map { try item.rewrite($0, materials: &materials) }, Tag(.seq), .block)
            case let .headers(lists):
                guard let mapping = node.mapping, mapping.count <= 128 else { throw unsupported }
                let token = Set("!#$%&'*+-.^_`|~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".utf8)
                for pair in mapping {
                    guard let key = pair.key.string, !key.isEmpty, key.utf8.count <= 256, key.utf8.allSatisfy(token.contains) else { throw unsupported }
                    let values: [Node]
                    if lists, let sequence = pair.value.sequence {
                        guard sequence.count <= 1024 else { throw unsupported }
                        values = Array(sequence)
                    } else { values = [pair.value] }
                    for value in values {
                        _ = try Rule.scalar.rewrite(value, materials: &materials)
                        guard let text = value.string, !text.utf8.contains(13), !text.utf8.contains(10) else { throw unsupported }
                    }
                }
                return node
            case .pluginOptions:
                guard let mapping = node.mapping, mapping.count <= 64 else { throw unsupported }
                return node // The sibling plugin name selects a closed schema in prepare.
            case let .options(fields, tls):
                guard let mapping = node.mapping else { throw unsupported }
                var pairs: [(Node, Node)] = []
                for pair in mapping {
                    guard let key = pair.key.string, let rule = fields[key] else { throw unsupported }
                    pairs.append((pair.key, try rule.rewrite(pair.value, materials: &materials)))
                }
                var result = Node(pairs, Tag(.map), .block)
                if tls { try materials.rewriteTLS(&result) }
                return result
            }
        }
    }

    private struct Materials {
        var files: [String: Data] = [:]
        private var names: [Data: String] = [:]
        private var bytes = 0

        mutating func rewriteTLS(_ node: inout Node) throws {
            let certificate = node["certificate"]?.string ?? ""
            let key = node["private-key"]?.string ?? ""
            if certificate.isEmpty, key.isEmpty { return }
            guard !certificate.isEmpty, !key.isEmpty else { throw invalidMaterial }
            try validatePEM(certificate, labels: ["CERTIFICATE"], multiple: true)
            try validatePEM(key, labels: ["PRIVATE KEY", "RSA PRIVATE KEY", "EC PRIVATE KEY"], multiple: false)
            // A failed inline TLS parse falls back to files in Mihomo. Never pass the caller's
            // string as that path: the core may read only these server-generated material files.
            node["certificate"] = Node(try store(certificate), Tag(.str))
            node["private-key"] = Node(try store(key), Tag(.str))
        }

        private mutating func store(_ text: String) throws -> String {
            let data = Data(text.utf8)
            if let name = names[data] { return "./tls-materials/\(name)" }
            guard files.count < 256, bytes + data.count <= ClashProviderDocument.maxPayloadBytes else { throw unsupported }
            let name = UUID().uuidString + ".pem"
            files[name] = data
            names[data] = name
            bytes += data.count
            return "./tls-materials/\(name)"
        }
    }

    private static func validatePEM(_ text: String, labels: Set<String>, headers: Bool = false, multiple: Bool) throws {
        let lines = text.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")
        var index = 0, count = 0
        while index < lines.count {
            if lines[index].trimmingCharacters(in: .whitespaces).isEmpty { index += 1; continue }
            guard let label = labels.first(where: { lines[index] == "-----BEGIN \($0)-----" }) else { throw invalidMaterial }
            count += 1
            guard count <= (multiple ? 32 : 1) else { throw invalidMaterial }
            index += 1
            var body = ""
            if headers {
                // Go's encrypted legacy SSH PEM parser accepts these headers. They remain
                // inline data and do not select files or invoke a passphrase dialog.
                while index < lines.count, lines[index].hasPrefix("Proc-Type:") || lines[index].hasPrefix("DEK-Info:") { index += 1 }
            }
            while index < lines.count, lines[index] != "-----END \(label)-----" {
                body += lines[index].trimmingCharacters(in: .whitespaces)
                index += 1
            }
            guard index < lines.count, let decoded = Data(base64Encoded: body), !decoded.isEmpty else { throw invalidMaterial }
            index += 1
        }
        guard count > 0 else { throw invalidMaterial }
    }

    private static func options(_ scalars: String = "", lists: String = "", tls: Bool = true, children: [String: Rule] = [:]) -> Rule {
        var fields = Dictionary(uniqueKeysWithValues: scalars.split(separator: " ").map { (String($0), Rule.scalar) })
        for name in lists.split(separator: " ") { fields[String(name)] = .list(.scalar) }
        for (name, rule) in children { fields[name] = rule }
        return .options(fields, tls: tls)
    }
    private static func node(_ scalars: String, lists: String = "", tls: Bool = true, children: [String: Rule] = [:]) -> Rule {
        options("name type server port tfo mptcp ip-version " + scalars, lists: lists, tls: tls,
                children: children.merging(["smux": smux]) { first, _ in first })
    }
    private static let tls = "skip-cert-verify name-cert-verify fingerprint certificate private-key"
    private static let ech = options("enable config query-server-name")
    private static let reality = options("public-key short-id support-x25519mlkem768")
    private static let shadowTLS = options("password version")
    private static let restls = options("password version-hint restls-script")
    private static let jls = options("username password")
    private static let ws = options("path max-early-data early-data-header-name v2ray-http-upgrade v2ray-http-upgrade-fast-open", children: ["headers": .headers(lists: false)])
    private static let http = options("method", lists: "path", children: ["headers": .headers(lists: true)])
    private static let h2 = options("path", lists: "host")
    private static let grpc = options("grpc-service-name grpc-user-agent ping-interval max-connections min-streams max-streams")
    private static let reuse = options("max-concurrency max-connections c-max-reuse-times h-max-request-times h-max-reusable-secs h-keep-alive-period")
    private static let download = options("path host server port tls servername client-fingerprint " + tls, lists: "alpn", children: ["headers": .headers(lists: false), "reuse-settings": reuse, "ech-opts": ech, "reality-opts": reality, "shadow-tls-opts": shadowTLS, "restls-opts": restls, "jls-opts": jls])
    private static let xhttp = options("path host mode no-grpc-header x-padding-bytes x-padding-obfs-mode x-padding-key x-padding-header x-padding-placement x-padding-method uplink-http-method session-placement session-key session-table session-length seq-placement seq-key uplink-data-placement uplink-data-key uplink-chunk-size sc-max-each-post-bytes sc-min-posts-interval-ms", children: ["headers": .headers(lists: false), "reuse-settings": reuse, "download-settings": download])
    private static let smux = options("enabled protocol max-connections min-streams max-streams padding statistic only-tcp", children: ["brutal-opts": options("enabled up down")])
    private static let mkcp = options("mtu tti uplink-capacity downlink-capacity congestion write-buffer read-buffer seed header")
    private static let mekya = options("url h2-pool-size max-write-delay max-request-size polling-interval-initial max-write-size max-write-duration-ms max-simultaneous-write-connection packet-writing-buffer", children: ["kcp": mkcp])
    private static let peer = options("server port public-key pre-shared-key", lists: "reserved allowed-ips")
    private static let amnezia = options("jc jmin jmax s1 s2 s3 s4 h1 h2 h3 h4 i1 i2 i3 i4 i5 j1 j2 j3 itime")

    private static let protocols: [String: Rule] = [
        "http": node("username password tls sni " + tls, children: ["headers": .headers(lists: false)]),
        "socks5": node("username password tls udp " + tls),
        "ss": node("password cipher udp plugin udp-over-tcp udp-over-tcp-version client-fingerprint", children: ["plugin-opts": .pluginOptions]),
        "ssr": node("password cipher obfs obfs-param protocol protocol-param udp"),
        "snell": node("psk udp version reuse client-fingerprint", children: ["obfs-opts": options("mode host")]),
        "vmess": node("uuid alterId cipher udp network tls servername packet-addr xudp packet-encoding global-padding authenticated-length client-fingerprint " + tls, lists: "alpn", children: ["ech-opts": ech, "reality-opts": reality, "shadow-tls-opts": shadowTLS, "restls-opts": restls, "jls-opts": jls, "ws-opts": ws, "http-opts": http, "h2-opts": h2, "grpc-opts": grpc, "mkcp-opts": mkcp, "mekya-opts": mekya]),
        "vless": node("uuid flow tls udp packet-addr xudp packet-encoding encryption network servername client-fingerprint " + tls, lists: "alpn", children: ["ech-opts": ech, "reality-opts": reality, "shadow-tls-opts": shadowTLS, "restls-opts": restls, "jls-opts": jls, "ws-opts": ws, "ws-headers": .headers(lists: false), "http-opts": http, "h2-opts": h2, "grpc-opts": grpc, "xhttp-opts": xhttp]),
        "trojan": node("password sni udp network client-fingerprint tls " + tls, lists: "alpn", children: ["ech-opts": ech, "reality-opts": reality, "shadow-tls-opts": shadowTLS, "restls-opts": restls, "jls-opts": jls, "ws-opts": ws, "grpc-opts": grpc, "ss-opts": options("enabled method password")]),
        "hysteria": node("ports protocol obfs-protocol up up-speed down down-speed auth auth-str obfs sni recv-window-conn recv-window disable-mtu-discovery fast-open hop-interval " + tls, lists: "alpn", children: ["ech-opts": ech]),
        "hysteria2": node("ports hop-interval up down password obfs obfs-password obfs-min-packet-size obfs-max-packet-size sni cwnd bbr-profile udp-mtu initial-stream-receive-window max-stream-receive-window initial-connection-receive-window max-connection-receive-window " + tls, lists: "alpn", children: ["ech-opts": ech, "realm-opts": options("enable server-url token realm-id sni " + tls, lists: "stun-servers alpn")]),
        "tuic": node("token uuid password ip heartbeat-interval reduce-rtt request-timeout udp-relay-mode congestion-controller disable-sni max-udp-relay-packet-size fast-open max-open-streams cwnd bbr-profile recv-window-conn recv-window disable-mtu-discovery max-datagram-frame-size sni udp-over-stream udp-over-stream-version " + tls, lists: "alpn", children: ["ech-opts": ech]),
        "anytls": node("password sni client-fingerprint udp idle-session-check-interval idle-session-timeout min-idle-session disable-reuse " + tls, lists: "alpn", children: ["ech-opts": ech, "shadow-tls-opts": shadowTLS, "restls-opts": restls, "jls-opts": jls]),
        "ssh": node("username password private-key private-key-passphrase", lists: "host-key host-key-algorithms", tls: false),
        "wireguard": node("ip ipv6 private-key workers mtu udp persistent-keepalive remote-dns-resolve refresh-server-ip-interval public-key pre-shared-key", lists: "dns reserved allowed-ips", tls: false, children: ["peers": .list(peer), "amnezia-wg-option": amnezia]),
    ]
    private static let plugins: [String: Rule] = [
        "obfs": options("mode host"),
        "v2ray-plugin": options("mode host path tls mux v2ray-http-upgrade v2ray-http-upgrade-fast-open " + tls, children: ["headers": .headers(lists: false), "ech-opts": ech]),
        "gost-plugin": options("mode host path tls mux " + tls, children: ["headers": .headers(lists: false), "ech-opts": ech]),
        "shadow-tls": options("password host version " + tls, lists: "alpn"),
        "restls": options("password host version-hint restls-script fingerprint skip-cert-verify name-cert-verify force-tls12"),
        "jls": options("host username password", lists: "alpn"),
        "kcptun": options("key crypt mode conn autoexpire scavengettl mtu ratelimit sndwnd rcvwnd datashard parityshard dscp nocomp acknodelay nodelay interval resend nc sockbuf smuxver smuxbuf framesize streambuf keepalive"),
    ]
}

struct PreparedProvider {
    let names: [String]
    let yaml: String
    let materials: [String: Data]

    func writeMaterials(to runtime: RuntimeDirectory) throws {
        guard !materials.isEmpty else { return }
        let folder = runtime.url.appendingPathComponent("tls-materials", isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
        for (name, data) in materials {
            let descriptor = open(folder.appendingPathComponent(name).path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW, 0o600)
            guard descriptor >= 0 else { throw WeaveMacError.message("无法建立私有 TLS 材料文件") }
            let file = FileHandle(fileDescriptor: descriptor, closeOnDealloc: true)
            try file.write(contentsOf: data)
            try file.close()
        }
    }
}
