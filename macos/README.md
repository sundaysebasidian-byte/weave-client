# Weave for macOS

原生 SwiftUI / Apple Silicon (`arm64`) 客户端。当前 `0.1.0-alpha09` 包含：

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
- 内置由固定源码构建并校验 SHA-256 的 `mihomo` arm64，默认优先启用 Mihomo 原生 macOS TUN；
- TUN 模式接管 IPv4、IPv6、UDP 和 DNS，不依赖系统代理的应用，并阻断标准 STUN 端口；TUN 或 DNS 未就绪时拒绝连接，避免静默直连；
- 订阅远程请求在初始地址、重定向和最终响应前解析并拒绝本机/私网/CGNAT/链路本地地址；
- 连接成功后接管当前主网络服务的 HTTP/HTTPS/SOCKS 代理并临时关闭 PAC，停止、崩溃或下次启动会恢复原设置；
- 局域网互传只绑定当前私有 IPv4，并严格校验一次性令牌、响应类型、长度和 AES-GCM 密文；
- 未签名 Apple Network Extension 时不伪装成系统签名 VPN；当前 TUN 由内置 Mihomo 原生 `utun` 实现。关闭 TUN 开关后才会主动使用本地代理，但该模式不保证所有 IPv4、IPv6、UDP 和 DNS 都经过 Weave。

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

## TUN 与完整 VPN

alpha09 默认先尝试 Mihomo 自己创建 `utun_weave`，打开 IPv4/IPv6 分半路由、DNS 劫持和
严格路由。它不需要 Apple Network Extension entitlement，适合在自己的 Apple Silicon Mac
上快速验证双栈 TUN、DNS 和 UDP 分流。如果 macOS 拒绝创建 utun、路由或 DNS 未能就绪，
Weave 会拒绝连接并明确显示错误，不会把“本地代理已启动”误报成全设备 VPN。只有用户主动
关闭设置中的“双栈 TUN”开关，才会使用绑定 `127.0.0.1:7890` 的本地 HTTP/HTTPS/SOCKS 代理。

这里的“双栈”表示本机 IPv4 和 IPv6 都进入 TUN；远端节点是否能提供独立的公网 IPv6 出口，仍取决于
节点协议、服务端网络和订阅本身。若节点只有 IPv4，IPv6 目标可能经由 IPv4 节点转发，或在节点不支持时失败，
不会因此回到物理网卡直连。

如果要做面向朋友分发的系统级签名 VPN，仍建议增加 `NEPacketTunnelProvider`/system extension、
Apple entitlement 和 Developer ID 签名。当前构建不会伪造或隐藏这一差异。

## 内核

`Resources/mihomo` 必须是从 `core-lock.properties` 固定的 Mihomo commit 为
`darwin/arm64` 构建的可执行文件。构建脚本只有在该文件存在且可执行时才会打入 App。
