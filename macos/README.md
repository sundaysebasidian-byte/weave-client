# Weave for macOS

原生 SwiftUI / Apple Silicon (`arm64`) 客户端。当前 `0.1.0-alpha06` 包含：

- 与 Android 共用 `Ink / Acid / Canvas / Paper / Good` 配色、圆角卡片、状态标签和页面层级，
  同时保留适合键鼠操作的 macOS 侧栏；
- 与 Android 共用“编织结”应用图标：深靛蓝三环交叠，叠加雾青、淡紫和珊瑚节点的莫奈式柔和色彩；
- 节点 YAML 只在订阅变化时于后台解析缓存；核心配置、互传编解码和加密不阻塞 UI 主线程；
- 固定桌面双栏取代 `NavigationSplitView`，四个主页面常驻且切换无重建、无隐式转场；
  节点选择使用可搜索、懒加载的弹层，不同步构造完整原生菜单；
- Keychain 主密钥 + AES-256-GCM 的本机订阅库；
- Weave LAN Transfer v1 一次性加密导入/导出；
- 导出二维码与链接；订阅页可直接从公网 HTTPS / Weave 链接、二维码图片或本地
  Clash/Mihomo YAML 文件导入，并在写入前校验有效节点；
- 独立订阅列表和永久删除；
- 订阅编辑、HTTPS 原位刷新和导入前的 Clash 控制面清理，只把节点 provider 交给 Mihomo；
- 连接页先选择订阅，再选择自动策略或该订阅的原始节点；
- 内置由固定源码构建并校验 SHA-256 的 `mihomo` arm64，开放真实本地代理模式；
- 订阅远程请求在初始地址、重定向和最终响应前解析并拒绝本机/私网/CGNAT/链路本地地址；
- 连接成功后接管当前主网络服务的 HTTP/HTTPS/SOCKS 代理并临时关闭 PAC，停止、崩溃或下次启动会恢复原设置；
- 局域网互传只绑定当前私有 IPv4，并严格校验一次性令牌、响应类型、长度和 AES-GCM 密文；
- 未签名 Network Extension 时明确禁用完整 VPN，不显示假连接成功。

## 构建

完整发布构建推荐 Xcode 16+。当前命令行工具可执行：

```bash
cd macos
./build-app.sh
```

输出为 `macos/build/Weave.app`。脚本执行 ad-hoc 签名，适合 Apple Silicon 本机和朋友私下分发；
朋友首次打开可能需要在“系统设置 → 隐私与安全性”中允许打开。未做 Apple notarization，
不应当作公开商店发行包。
打包前会核对内核哈希，不匹配 `core-lock.properties` 时直接失败。

## 完整 VPN

macOS 全设备 TUN 必须使用 `NEPacketTunnelProvider`。将
`WeaveMac.NetworkExtension.entitlements.example` 的 entitlement 配置到 Apple Developer
App ID 和 Packet Tunnel Provider extension target 后才能启用。没有该 entitlement 时，
Weave 只运行绑定到 `127.0.0.1` 的本地代理；在未启用 Network Extension 的私有构建中，连接时
会临时修改当前网络服务的 HTTP/HTTPS/SOCKS 代理并关闭 PAC，退出、停止或异常恢复时写回原设置。它不是完整的
全设备 VPN，不能替代受签名的 `NEPacketTunnelProvider`。

## 内核

`Resources/mihomo` 必须是从 `core-lock.properties` 固定的 Mihomo commit 为
`darwin/arm64` 构建的可执行文件。构建脚本只有在该文件存在且可执行时才会打入 App。
