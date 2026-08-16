import CryptoKit
import Foundation

func require(_ condition: @autoclosure () -> Bool, _ message: String) {
    if !condition() {
        FileHandle.standardError.write(Data("FAIL: \(message)\n".utf8))
        exit(1)
    }
}

do {
    let items = [
        TransferSubscription(
            name: "工作订阅",
            source: "https://example.invalid/sub",
            payload: "proxies:\n  - name: test\n    type: vless\n"
        ),
    ]
    let key = Data((0..<32).map(UInt8.init))
    let plaintext = try TransferCodec.encode(items)
    let vectorHash = SHA256.hash(data: plaintext).map { String(format: "%02x", $0) }.joined()
    require(
        vectorHash == "3762f88e5dbbb4598b84219faf58fcbf7620607c08c51df1a218e9fb039040c1",
        "cross-platform plaintext vector"
    )
    let packet = try TransferCodec.seal(plaintext, key: key)
    let roundTrip = try TransferCodec.decode(TransferCodec.open(packet, key: key))
    require(roundTrip == items, "encrypted round trip")

    let link = TransferLink(
        host: "192.168.1.20",
        port: 38422,
        token: "0123456789abcdef0123456789abcdef",
        key: key
    )
    let parsedLink = try TransferLink.parse(link.string)
    require(parsedLink == link, "link round trip")

    let nodeNames = ClashNodeNames.parse(
        """
        proxies:
          - name: "🇩🇪 de-n1 (0.3x)"
            type: vless
          - {name: 'us-n2', type: trojan, server: example.invalid}
        proxy-groups:
          - name: not-a-node
            type: select
        """
    )
    require(nodeNames == ["🇩🇪 de-n1 (0.3x)", "us-n2"], "Clash node parsing")
    require(ClashNodeNames.display(nodeNames[0]) == "de-n1 (0.3x)", "concise node display")
    require(
        ClashNodeNames.display("\\u0001f1e0\\u0001f1ea de-n1") == "de-n1",
        "escaped decoration cleanup",
    )

    let sanitized = try ClashProviderSanitizer.sanitize(
        """
        mixed-port: 7890
        common: &common
          udp: true
        proxies:
          - name: test
            <<: *common
            type: vless
        proxy-groups:
          - name: control-plane
            type: select
        external-controller: 0.0.0.0:9090
        """
    )
    require(sanitized.contains("proxies:"), "provider sanitizer keeps nodes")
    require(sanitized.contains("common: &common"), "provider sanitizer keeps referenced anchor")
    require(!sanitized.contains("external-controller"), "provider sanitizer strips control plane")

    var tampered = packet
    tampered[tampered.count - 1] ^= 1
    do {
        _ = try TransferCodec.open(tampered, key: key)
        require(false, "tampering must fail")
    } catch {}

    do {
        _ = try TransferLink.parse(
            "weave://lan/v1/0123456789abcdef0123456789abcdef?host=8.8.8.8&port=80#invalid"
        )
        require(false, "public endpoint must fail")
    } catch {}

    let remoteSubscription = try DirectSubscriptionImporter.validateRemoteURL(
        "https://example.com/subscription.yaml?token=test"
    )
    require(remoteSubscription.scheme == "https", "public HTTPS subscription")

    for blockedURL in [
        "http://example.com/subscription.yaml",
        "https://localhost/subscription.yaml",
        "https://127.0.0.1/subscription.yaml",
        "https://192.168.1.8/subscription.yaml",
        "https://user:password@example.com/subscription.yaml",
    ] {
        do {
            _ = try DirectSubscriptionImporter.validateRemoteURL(blockedURL)
            require(false, "unsafe subscription URL must fail: \(blockedURL)")
        } catch {}
    }

    print("PASS: Weave transfer and direct subscription import self-test")
} catch {
    FileHandle.standardError.write(Data("FAIL: \(error)\n".utf8))
    exit(1)
}
