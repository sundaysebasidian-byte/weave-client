# Weave for iOS

原生 SwiftUI iPhone 客户端，最低系统为 iOS 17。界面沿用 Android 已定稿的
Liquid Glass × 莫奈设计系统，但交互使用 iOS 原生 Material、导航、菜单、照片选择器、
相机扫描和系统 Packet Tunnel。

## 当前完成度（0.1.0-alpha01）

- 连接、分流、订阅、设置四个一级页面；浅色/深色和四套艺术主题持久化。
- 连接页严格采用“先选订阅，再选自动策略或固定节点”。节点名称清理旗帜等开头装饰，
  不修改写给内核的原始名称。
- Keychain 256-bit 主密钥 + AES-256-GCM 加密订阅库。
- 公网 HTTPS、粘贴 Clash YAML、常见 URI/Base64、系统文件、相机二维码、照片二维码和
  Weave LAN Transfer v1 导入。
- HTTPS 拉取限制大小、超时和重定向，拒绝内嵌凭据、localhost、私网 IPv4 和明文 HTTP。
- 订阅查看、搜索节点、更新、改名和永久删除；删除时同步清理相关规则和默认出口引用。
- 域名后缀可分别选择不同订阅的自动策略、固定节点、直连或阻止，并支持删除。
- 隐私/广告/家庭/自定义加密 DNS、IPv4-only、STUN 阻断、国内智能直连和三种自动节点策略。
- Android/macOS/iOS 共用的一次性局域网加密传输协议；密钥只位于 `weave://` fragment。
- 独立 `NEPacketTunnelProvider` target、App Group 运行配置事务、配置 SHA-256 校验和
  fail-closed 移动内核适配边界。

## 必须诚实说明的两个平台边界

1. 普通个人 iOS VPN 看不到每个数据包来自哪个 App。按应用 VPN 需要 MDM 管理的
   Per-App VPN 配置，因此公开版分流按域名/规则组工作，不显示假的 App 选择器。
2. 仓库现有 Android `libclash.so` 和 macOS `mihomo` 可执行文件都不能在 iOS 使用。
   Packet Tunnel 必须嵌入面向 `NEPacketTunnelFlow` 的 `WeaveMihomoMobile.xcframework`。
   未提供该 framework 时扩展会明确报错并拒绝建立隧道，不会显示虚假“已连接”。

移动内核的最小适配契约和固定版本要求见 [MOBILE_CORE.md](MOBILE_CORE.md)。

## 打开与构建

需要完整 Xcode 16 或更高版本；只有 Command Line Tools 不包含 iPhoneOS SDK。

1. 打开 `ios/WeaveIOS.xcodeproj`。
2. 在 `WeaveIOS` 和 `WeavePacketTunnel` 两个 target 中选择同一个 Apple Developer Team。
3. 将 App、Extension 和 App Group bundle identifier 换成你账号拥有的唯一值，并同步修改：
   - `AppModel.appGroupIdentifier`
   - `PacketTunnelProvider` 的 App Group
   - 两份 entitlements
   - `TunnelManager.providerBundleIdentifier`
4. 在两个 target 启用 App Groups，在 App 和 Extension 启用 Network Extensions → Packet Tunnel。
5. 加入经过固定源码、哈希和许可证审计的 `WeaveMihomoMobile.xcframework`。
6. 只有在 framework 已链接且扩展构建成功后，将 App `Info.plist` 中
   `WeaveMobileCoreEmbedded` 改为 `true`；这个显式开关防止误发只有空 TUN 的构建。
7. 选择真机运行。模拟器可验证 UI/导入，但不能作为 Packet Tunnel 真机验收替代品。

无签名编译检查：

```bash
./ios/build.sh
```

共享核心自测：

```bash
swift run --package-path ios/WeaveCore WeaveCoreSelfTest
```

## 安全默认值

- 运行时 provider/config 只在 App Group 中短暂存在，使用完整文件保护；断开或启动失败时清理。
- `NETunnelProviderProtocol.providerConfiguration` 只保存版本化清单和 SHA-256，不保存订阅凭据。
- 配置、节点引用或移动内核任一校验失败，都在设置系统路由前或立即回滚后失败。
- 不包含 WebView 控制面、遥测 SDK、远程开关或默认推荐订阅。

## 已执行验证

- `WeaveCoreSelfTest`：节点解析、URI 转换、SSRF 边界、互传防篡改、加密落盘、规则优先级。
- 主 App 全部 Swift 文件使用 Mac Catalyst iOSSupport SDK 完整类型检查。
- Packet Tunnel target 使用 NetworkExtension SDK 独立完整类型检查。
- Xcode project、Info.plist 和 entitlements 均通过 `plutil`。

当前开发机未安装完整 Xcode，所以这里没有声称已生成、签名或真机安装 IPA。
