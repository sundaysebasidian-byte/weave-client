# Weave（暂定名）

一款面向 Android 的开源规则代理客户端：以 CMFA 的兼容性和低学习成本为基线，吸收 Karing 的多订阅、
复杂规则与按应用分流能力，同时把可审计、安全默认值和内核可替换性放在架构中心。仓库中的其他平台
目录保留为历史实验代码，不属于当前 Android 发行版。

> 当前里程碑是 **Android 0.3.0-alpha51**（Android-only 预览版）。Android 四个 ABI 的
> CMFA/Mihomo
> 核心已从锁定源码本地复现并接入；Clash YAML、URI/Base64、sing-box JSON 与基础 V2Ray JSON
> 可从 HTTPS、粘贴文本、系统文件选择器、相机二维码或二维码图片安全导入，并编译为隔离 provider，
> Android `VpnService` 会把 TUN fd 交给核心，并通过 `protect(fd)` 与 UID/包名回调完成出站
> 和按应用路由。Android 16 ARM64 模拟器与 Pixel 8 / Android 17 真机均已用真实 OpenVPN
> 订阅完成双栈 TUN、固定节点、Chrome 应用归属、系统网络验证、Wi‑Fi/蜂窝切换和断开清理
> 闭环。当前发行只覆盖 Android；其他平台目录不随 Android 预览版构建，也不应作为生产 VPN 使用。

Weave 本身不提供、销售或推荐代理节点。导入的订阅、节点和第三方 DNS 由用户自行选择，
其运营者可能看到连接所必需的元数据。公开预览版的实际数据处理见
[`PRIVACY.md`](PRIVACY.md)，安全问题请使用 [`SECURITY.md`](SECURITY.md) 中的私密渠道。

## 本地开源发行边界

本仓库按 `local-open-source` 配置维护：Weave 不运营账号、云端控制面、代理中继、节点市场、
订阅服务、广告/统计/崩溃上报或应用远程更新，也不在发行包中放入节点凭据。GitHub 只承载公开源码、
构建说明和人工发布的静态产物；用户主动选择的订阅、代理、DoH/DoT、IP 质量检测和局域网互传仍会
按请求连接相应第三方。完整端点边界见 [`docs/NETWORK_ENDPOINT_INVENTORY.md`](docs/NETWORK_ENDPOINT_INVENTORY.md)，
发行前请运行 `bash tools/audit-local-release.sh` 并完成 [`docs/LOCAL_ONLY_RELEASE_PROFILE.md`](docs/LOCAL_ONLY_RELEASE_PROFILE.md)。

这不是“免监管”“保证匿名”或“规避网络管理”的承诺。Weave 是客户端软件，不代用户提供线路或通信服务；
节点、订阅和目标服务的使用责任仍由使用者承担。开源、免费、非营利和没有云服务器本身都不能替代针对
实际分发和运营行为的法律评估。

## 核心体验

- 首页只回答三个问题：是否受保护、当前出口是什么、现在是否健康。
- 外观分为两类：极简风提供浅色模式与深色模式，艺术风保留四套低饱和莫奈主题；新安装默认使用极简浅色，已有选择会原样保留。
- 设置支持简体中文、繁體中文、English、日本語、Français、Deutsch 六种应用内语言；切换立即生效并本机持久化，节点、订阅和应用名称等用户内容保持原文。
- 首页提供按需 IP 质量检测：通过多个固定 HTTPS 端点读取当前 IPv4/IPv6 出口、地区、ASN、ISP、边缘节点、代理/VPN/Tor/托管标签和真实 HTTPS RTT；结果只保存在内存，不把 DNS 泄漏或 WebRTC 伪装成已完成测试。
- 首页可手动选择默认订阅策略、固定节点或直连；选择订阅后可对该订阅全部节点进行三轮质量
  测速，并直接固定到选中的节点；连接中修改会安全热重载。
- Android 长连接遇到 Wi‑Fi/蜂窝切换或底层 Network 短暂重建时，会在保持前台服务和 fail-closed
  的前提下恢复上一次健康运行时；Android 10+ 的核心出站 socket 只调用 `protect()`，由系统跟随
  当前物理网络，避免把连接钉在已经失效的 Network 对象上。
- 出口选择采用“先选订阅、再选节点”的两步条件流程；节点只显示核心名称，不拼接订阅名、
  协议或空延迟说明，并清理开头的旗帜 Emoji 与 `\u...` 转义装饰。
- 已导入订阅可查看和搜索全部节点、修改名称或 HTTPS 地址，也可用本地文件原位替换；
  被替换掉的固定节点引用会安全降级到该订阅的自动策略。
- 订阅原位更新采用版本化加密 payload：新内容和元数据提交成功后才删除旧版本，并显示
  新增、移除、保留节点差异；同名同协议但参数未知的节点只提示，不做可能误删的去重。
- 订阅更新在提交前运行本地 Subscription Guard：空订阅、节点数量灾变式下降/增长会阻断并保留旧版本；
  来源主机变化、格式变化和重复节点会明确标记为需要复核，不静默替换。
- 已导入订阅可永久删除；确认页会列出受影响的应用规则和默认出口回退行为，并同步清理
  Keystore 加密的地址、节点元数据与加密 payload。
- 应用分流是一等能力：`应用 → 自动策略 / 固定节点 / 直连 / 阻止`。
- Android 应用规则同时编译 UID 与包名匹配；自动策略或固定代理节点不能承载 UDP 时会
  拒绝该应用的 UDP/QUIC，禁止静默泄漏到默认节点。明确选择直连的应用不添加这条守卫，
  让 WebView/HTTP3 按直连出口正常使用 UDP；阻止规则仍由 `REJECT` 本身负责。
- Android UID 归属会在内存中按本地 socket 短期缓存 10 秒且最多保留 2048 项，覆盖
  QUIC 被拒绝后系统瞬间返回 `-1` 的重试窗口；服务停止或规则重载时立即清空。
- 应用规则可在出口编辑弹窗中删除；删除前二次确认，删除后回落到默认出口。
- 每个应用可以引用不同订阅中的不同节点，而不要求切换整套配置。
- 二维码扫描使用系统相机预览和随包 ZXing 在本机识别；首次使用相机时才申请
  `CAMERA` 权限，相册二维码同样在本机识别，并限制为 20 MiB，不依赖 Google Play
  services 或额外的 ML Kit 图片模型。
- 多订阅会生成彼此隔离的 Mihomo file provider；URI/Base64、sing-box 与基础 V2Ray JSON 在导入边界转换，复杂或缺字段协议继续 fail closed。
- 运行时只加载默认出口和应用规则实际引用的 provider；健康检查按需懒执行，避免大订阅在
  VPN 启动时并发探测全部节点、挤占真实连接。
- 自动订阅策略可在最低延迟（`url-test`）、按顺序故障切换（`fallback`）与一致性哈希
  负载均衡之间切换；修改后持久化并安全热重载。
- DNS 可选择 DoH 或 DoT，并提供国内隐私、阿里、腾讯、Cloudflare、Google、Quad9、Mullvad、广告过滤、家庭过滤和自定义加密端点；海外 DNS 在当前网络不可达时回退到阿里/腾讯加密解析，所有配置都会阻断应用自发的明文 TCP/UDP 53、DoT/DoQ 853、已知公共 DoH 域名和公共 DNS IPv4/IPv6，过滤模式再叠加广告/家庭域名规则；自定义浏览器 DoH 无法从网络层枚举，需在浏览器内关闭；同时保留 fake-IP 全隧道接管；IPv4-only 模式会停用 IPv6 DNS，
  同时在已经接管 `::/0` 的隧道内拒绝 IPv6，避免旁路物理网络。
- “国内智能直连”默认开启，把随 APK 固定并校验哈希的 lite GeoSite/GeoIP CN 规则放在应用规则
  之后、默认出口之前；DNS 同时让 CN 域名返回真实地址，避免 fake-IP 让 GEOIP 只能看到
  `198.18.x.x`。应用级指定出口始终优先，运行时不静默下载规则数据。需要全量代理时可在设置中关闭。
- Mihomo 在本地启用 DNS 映射和 TLS/HTTP/QUIC 主机嗅探，用于恢复未携带域名的连接的路由上下文；
  嗅探结果只用于本机规则匹配，不替换实际目的地址，也不上传域名。
- 可选 UDP STUN 阻断覆盖常见的 3478–3479 与 19302–19309 端口范围，用于降低 WebRTC
  地址暴露风险；界面明确提示它可能影响音视频通话。
- 运行配置采用候选、活动、回滚三个短生命周期快照：候选先由原生内核只解析不应用，
  校验失败时旧 TUN 不停；启动失败时自动恢复上一份配置与 provider。
- 恢复中心只保存失败次数、最近可用快照和安全模式元数据，不保存 URL、凭据或明文配置；
  候选与回滚均失败时自动进入安全模式，必须由用户主动解除后才允许重新连接。
- 分流页提供 Route Lens 路由解释器，可输入应用包名、域名、端口和协议，展示命中规则、DNS、
  出口、UDP/QUIC 与 IPv6 风险；它只模拟本地配置，不伪造网络测试结果。
- 设置页提供 Privacy Observatory 隐私观测，逐项区分已确认、未知和未测试，覆盖 VPN、加密 DNS、
  DNS 旁路拒绝、过滤、IPv6、WebRTC/STUN、显式直连和断开清理，不输出误导性的安全百分比；
  Android 的 Always-on / “阻止无 VPN 连接”仍需用户在系统 VPN 设置中打开，应用不会伪造已开启状态。
- 连接页和订阅详情都可读取 CMFA 真实运行组的节点质量，并手动触发三轮 provider 健康检查；
  未加载的订阅会临时加入候选运行配置，不改变默认出口；核心未初始化/失败延迟哨兵会在数据边界丢弃，不参与排序或展示。
- 订阅详情提供质量矩阵：按中位延迟、P95、抖动、丢包和成功样本数计算透明稳定度排序；DNS、TLS、UDP
  和带宽等未实际探测的字段保持未知，不生成伪造数值。
- 设置页支持离线策略包（`weave-policy/v1`）：本机校验 SHA-256，可选 Ed25519 签名，Keystore
  加密保存，启用/停用后安全热重载；规则只接受受限域名、CIDR 和进程匹配，未签名包明确标记为需复核。
- 代理模式没有隐式直连兜底；默认订阅失效或底层网络丢失时保持失败关闭。核心出站 socket 通过
  `VpnService.protect()` 绕过 TUN，Android 10+ 由系统选择当前非 VPN Wi‑Fi、蜂窝或以太网；Android 8/9
  额外更新 VPN 的 underlying-network 元数据。
- 代理服务器域名强制交给所选 DoH/DoT 上游解析；明文 `default-nameserver` 只承担加密
  DNS 上游域名的引导解析，不再用于每个代理服务器域名。
- 订阅正文、URL、节点元数据和自定义 DNS 端点均使用 Keystore AES-GCM 保护；Clash 导入只保留
  节点 provider 段，局域网互传只绑定私有 IPv4，并对 HTTP 响应和恢复错误做最小化处理。
- 兼容使用 YAML merge anchor 的 OpenVPN Clash 配置，文件导入限制为 5 MiB 且要求严格 UTF-8。
- 兼容 Clash 节点的多行 YAML、单行 `{...}` flow map 与整行 `proxies: [...]` 表达；
  远程请求使用 CMFA/Clash 兼容标识，避免自适应面板误返回通用格式。
- 用户看到的是“最终命中出口”，不是一堆互相覆盖、难以调试的规则开关。
- 首页以不记录域名、IP 或连接明细的计数器展示应用 UID 识别是否正常。
- 订阅凭据、节点地址和访问域名默认不进入日志或诊断包。
- 只枚举带启动器入口的应用，不申请 `QUERY_ALL_PACKAGES`。
- 用户新增的应用规则与跨订阅出口会在本机持久化。
- 首次建立 VPN 前显示独立的数据路径说明并要求主动确认；设置页可随时重新查看，不用营销
  文案代替系统权限与第三方节点的真实责任边界。
- Android、iOS 与 macOS 可一键生成 5 分钟、仅可使用一次的局域网二维码/链接；导出时可按订阅选择，
  接收端需核对发送端显示的 6 位短码；同名同源订阅先经 Subscription Guard 审计后原位更新；HTTP 只承载
  AES-256-GCM 密文，密钥只在 `weave://` 链接 fragment 中。
- Android 监听已验证的非 VPN 底层网络；Wi‑Fi/蜂窝切换稳定 1.5 秒后自动事务重启核心，
  无网络时保持明确状态并等待恢复。首页运行指标只在首页可见时轮询，减少后台页面重组。

## Windows 私用预览

Windows 版工程位于 [`windows/`](windows/)，目标为 Windows 10/11 x64，后续补 ARM64。它不复用
Android `VpnService`，而是由 Mihomo 的 Windows TUN/Wintun 接管流量；核心层提供 Clash/Mihomo
YAML/Base64 导入、DPAPI 订阅加密、节点列表、自动测速组、固定节点组和 `PROCESS-NAME` 进程分流规则。
WinUI 入口已经包含订阅导入/删除、先选订阅再选节点和连接状态，但 Windows 真机上的 Wintun、DNS
劫持、管理员权限与断开回滚仍需在 Windows 10/11 上验证；当前不把它标记为生产 VPN。

构建说明与核心放置位置见 [`windows/README.md`](windows/README.md)。第一版默认不自动下载 Mihomo，
需要把经过固定来源与哈希审计的 `mihomo.exe` 放到发行包 `runtime/`，或显式设置
`WEAVE_MIHOMO_PATH`。

## macOS Apple Silicon

macOS 14+ 可运行 [Weave.app](macos/build/Weave.app)，或在 `macos/` 执行
`./build-app.sh` 重新构建。连接页先选订阅，再选自动策略或该订阅的具体节点；当前启动绑定
`127.0.0.1:7890` 的本地代理；连接时会事务化接管当前 macOS 网络服务的 HTTP/HTTPS/SOCKS 代理并关闭 PAC，停止、崩溃
或下次启动时恢复原设置。完整设备 VPN 和按应用分流需要 Apple Developer 为 Packet Tunnel extension
授予 Network Extension entitlement，未获权限时界面不会显示虚假的 VPN 成功状态。macOS/iOS
仅用于私有朋友分发，不是本仓库的公开发布目标。

## iOS

iOS 17+ 源码工程位于 [`ios/`](ios/README.md)，使用 SwiftUI、Keychain + AES-GCM、系统文件/
照片/相机导入和独立 `NEPacketTunnelProvider`。界面沿用 Liquid Glass × 莫奈主题，并保持
“先选订阅，再选节点”的条件选择。普通个人 iOS Packet Tunnel 无法获知流量来自哪个 App，
因此 iOS 公开版提供域名规则，不伪造 Android 式按应用选节点入口。未嵌入
`WeaveMihomoMobile.xcframework` 时扩展会 fail closed，不会显示虚假连接成功。

## 运行

项目使用 Android Studio、JDK 17、AGP 9.2、Gradle 9.4.1、`compileSdk 36`、
NDK `29.0.14206865` 和 CMake `3.31.6`。
AndroidX Core 1.18 / Lifecycle 2.10 暂时固定在仍兼容 API 36 的稳定版本；升级到它们的下一
稳定版需要先把编译 SDK 提升到 API 37。

1. 用 Android Studio 打开仓库。

发布 Android 包时执行 `./gradlew assembleRelease`。为避免把四套原生核心塞进一个约 230 MB
的 universal APK，构建会分别输出 `arm64-v8a`、`armeabi-v7a`、`x86` 和 `x86_64` 包；现代
手机通常选择 `app-arm64-v8a-release-unsigned.apk`，签名后再分发。`assembleDebug` 也遵循
同样的 ABI 拆分规则。
2. 安装 Android SDK 36、NDK 29.0.14206865 和 CMake 3.31.6。
3. 使用仓库内经过 SHA-256 固定的 wrapper 构建：

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

4. 运行 `app` 的 debug variant。

请让 Android Studio 或 `JAVA_HOME` 使用 JDK 17。Wrapper JAR 和发行包校验和已随仓库提供。

公开贡献前请阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。发行者还必须逐项完成
[`docs/OPEN_SOURCE_RELEASE_CHECKLIST.md`](docs/OPEN_SOURCE_RELEASE_CHECKLIST.md)，Debug APK
不属于受支持的生产发行物。

## 目录

```text
app/
  src/main/java/io/weave/client/
    core/bridge/       最小 JNI 接口与回调
    core/engine/       Mihomo 生命周期、配置组装与规则编译
    core/vpn/          Android VpnService 生命周期
    data/              路由与运行模式持久化
    domain/            与内核无关的订阅、节点、应用路由模型
    security/          Android Keystore AES-GCM 密钥信封
    subscription/      HTTPS 获取、输入限制、格式识别与加密存储
    ui/                Compose 页面与设计系统
docs/
  PRODUCT_SPEC.md      产品边界与里程碑
  ARCHITECTURE.md      模块、数据流和双内核策略
  SECURITY.md          威胁模型和安全发布门槛
  ANDROID_FEATURE_MATRIX.md 截图需求逐项对应的真实实现状态与后续边界
  UI_ENTRYPOINT_AUDIT.md 已实现入口、移除的假控件与兼容性边界
  ENGINE_INTEGRATION.md Mihomo 接入清单
  CORE_PROVENANCE.md   固定的 CMFA/Mihomo 构建来源
  RUNTIME_VALIDATION.md Android 16 模拟器与 Android 17 真机运行验证记录
macos/                  SwiftUI 客户端、arm64 Mihomo、构建与互传实现
ios/                    SwiftUI iPhone App、共享核心、Packet Tunnel 与移动内核契约
windows/                WinUI 3 私用预览、Windows Mihomo/TUN 生命周期与订阅核心
```

## 技术判断与上游

- [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid)：
  Android 兼容经验、Mihomo Android 分支与服务组织方式。上游为 GPL-3.0。
- [Karing](https://github.com/KaringX/karing)：
  多订阅、规则组、同步和新手模式的产品能力。上游采用 GPL-3.0，并限制衍生作品使用其名称
  或暗示关联。
- [sing-box](https://github.com/SagerNet/sing-box)：
  作为第二内核候选；其路由规则原生支持 Android `package_name`。
- [Android VPN 指南](https://developer.android.com/develop/connectivity/vpn)：
  `VpnService`、always-on 和 per-app VPN 的平台行为。

Weave 不使用 CMFA 或 Karing 的名称、图标、品牌素材，也不暗示与它们存在官方关联。

## 许可证

项目以 GPL-3.0-or-later 发布，完整正文见 [LICENSE](LICENSE)。原生输入、工具链和哈希见
[`core-lock.properties`](core-lock.properties)。生产发布前仍须生成完整 Go 依赖 SBOM、
NOTICE/版权清单，并随发行版提供对应源码。
