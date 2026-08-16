# iOS 移动内核适配契约

## 为什么需要单独 framework

iOS App 和 Packet Tunnel extension 不能像 macOS 一样启动任意子进程，也不能加载 Android
ELF `.so`。内核必须作为签名 app bundle 内的 framework 运行，并通过
`NEPacketTunnelFlow` 收发 IP 数据包。

公开仓库当前保留一个窄适配点：`PacketTunnel/MobileCoreBridge.swift`。没有符合契约的
framework 时，`core.isAvailable` 为 `false`，扩展在应用网络设置前失败。

## 模块契约

framework 模块名为 `WeaveMihomoMobile`，暴露：

```swift
public final class PacketEngine {
    public init(
        homeDirectory: String,
        configurationPath: String,
        packetFlow: NEPacketTunnelFlow
    ) throws

    public func start() async throws
    public func stop() async
    public func statusData() -> Data
}
```

实现必须：

- 把 `NEPacketTunnelFlow.readPackets` 输入内核用户态网络栈，并把返回包写回 `packetFlow`；
- 在所有内核出站 socket 上避开隧道递归；
- `start()` 只有在配置解析、DNS 和数据面全部就绪后才成功；
- `stop()` 可重复调用并等待读写循环、DNS、健康检查和出站连接全部退出；
- `statusData()` 不包含域名、目标 IP、节点凭据或订阅 URL；
- 不启动外部 controller 监听，不把控制 API 暴露给局域网；
- 支持 Extension 的内存上限并在系统 memory pressure 下释放非活动 provider。

## 版本与来源

首个可连接版本应从 `core-lock.properties` 记录的 Mihomo commit 构建，而不是下载 release
目录中“最新”二进制。若该 commit 缺少可审计的 `NEPacketTunnelFlow` 适配，应在独立分支做
最小补丁并记录：源码 commit、补丁 SHA-256、Go/Xcode 版本、gomobile 版本、构建 tag、
最终 XCFramework 每个 slice 的 SHA-256 和完整 Go SBOM。

在满足上面条件之前，不应把 fallback bridge 改成返回成功，也不应发布声称可用的 IPA。

## Apple 权限

即使 framework 已完成，真机仍需要：

- App 与 Extension 同一 Team 签名；
- `packet-tunnel-provider` Network Extension entitlement；
- `group.io.weave.client`（或发行者自有值）App Group；
- 与 bundle identifiers 完全匹配的 provisioning profiles。

公开 App Store 版本还需准备 VPN 数据使用说明、隐私标签、出口节点责任边界和当地分发合规审查。
