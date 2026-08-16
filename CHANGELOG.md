# Changelog

This project follows semantic versioning while in alpha. Breaking storage or
configuration changes may still occur before 1.0.

## Unreleased

- Android alpha51：针对“同一节点在 CMFA 稳定、Weave 偶发无网”收敛 CMFA 数据面路径。Mihomo
  使用 Android `system` TUN 栈并接管任意 IPv4/IPv6 DNS 53 端口；Android 10+ 核心出站只做
  `VpnService.protect()`，不再绑定易失效的 `Network` 或在换网时重建健康 TUN；自动节点连续
  3 次健康探测失败才切换，并使用轻量 connectivity probe，减少移动网络瞬时抖动导致的断网。
  新版本号为 `0.3.0-alpha51`（versionCode 53）。

- 发布边界：新增 `local-open-source` 本地发行配置、网络端点清单和 `audit-local-release.sh`；明确公开版不运营账号、云端控制、节点中继、遥测、崩溃上报或应用远程更新，并在首次 VPN 数据路径说明中展示该边界。发布文案、隐私说明、第三方清单和 PR 模板同步加入事实核对与敏感信息脱敏门槛。

- Android：稳定长连接的出站保护恢复链路。区分底层 Network 暂时不可用与原生内核出站 socket
  保护失败；发生晚到的 `protect`/`bindSocket` 失败时保留前台服务，按 0.5/1.5/3/6/12 秒
  退避重建上一份健康配置，网络恢复后继续自动重连。恢复期间始终 fail-closed，不把未保护的
  socket 放出；重试耗尽后明确显示错误并等待下一次网络变化，而不是静默直连。

- Android：新增应用内语言选择，支持简体中文、繁體中文、English、日本語、Français、Deutsch；选择
  保存在本机设置中，重启后保持。导航、设置、连接模式、订阅导入和主要对话框会立即切换语言；
  节点名、订阅名、应用名和用户输入内容保持原文。

- Android：补齐语言切换的运行时覆盖。订阅解析、二维码/文件导入、局域网互传、DNS 探测、Mihomo
  启停、VPN 恢复和 Subscription Guard 的错误与状态现在统一经过本地翻译层；带节点名、数量、HTTP
  状态码和重试次数的动态文案也会保留变量并翻译上下文，避免页面标题已切换但 Snackbar 或错误弹窗仍为中文。

- Android：修复暗色极简模式下分流应用徽标使用浅色底、浅色字导致的低可读性。暗色徽标现在
  使用与石墨面板协调的低饱和色，并使用高对比文字；浅色和四套艺术风的徽标行为保持不变。

- Android：极简浅色/深色主题收敛为低噪声配色。浅色使用单一中性画布与统一面板，深色使用两级
  石墨灰和低饱和蓝色强调；极简卡片取消彩色渐变并降低阴影、边框对比度。四套艺术风主题的
  配色、渐变与绘制保持不变。

- Android：极简风新增“深海蓝 / 石墨灰 / 夜松青”三套夜间配色，并重新整理原深色模式的
  画布、面板和强调色。浅色模式与四套艺术风保持原样；旧的“深色模式”设置仍可正常读取。

- Android：隐私与网络防旁路加固：Mihomo 默认日志级别收紧为 `error`，避免失败连接把域名/SNI
  写入 logcat；所有配置在 TUN 规则前置拒绝应用自发的明文 TCP/UDP 53、DoT/DoQ 853、已知公共
  DoH 域名和公共 DNS IPv4/IPv6，广告/家庭模式继续叠加本地过滤。Privacy Observatory 新增
  DNS 旁路证据和系统 kill switch 提醒；Always-on 与“阻止无 VPN 连接”仍由 Android 系统设置负责。

- macOS：新增 Apple Silicon 私有版 `0.1.0-alpha06`。连接成功后通过事务化快照接管当前网络服务的
  HTTP/HTTPS/SOCKS 代理并关闭 PAC，停止、Mihomo 异常退出、应用退出或下次启动会恢复原设置；完整 TUN/VPN 仍需
  经过 Apple 签名的 Network Extension。
- macOS：订阅导入与编辑统一清理 Clash 控制面字段，只保留节点 provider；支持 HTTPS 原位刷新、
  编辑替换和严格的节点重新解析。局域网服务只绑定私有 IPv4，并严格校验请求、响应头、长度和
  AES-256-GCM 密文，适合仅限朋友的 ad-hoc 分发。

- Android：隐私与网络安全加固：节点元数据和自定义 DNS 端点改用 Keystore AES-GCM 保存；Clash
  导入在进入 Mihomo 前只保留节点 provider，丢弃控制器、监听端口、TUN、脚本、远程规则、DNS
  和 proxy-groups；局域网互传改为绑定当前私有 IPv4，并严格校验请求行、响应类型、长度和 AEAD。
- Android：恢复中心与 DNS 探测不再持久化或展示异常原文，避免节点主机、SNI、URL 和凭据进入日志、
  错误记录或诊断面；网络安全配置显式只信任系统 CA 并拒绝明文流量。

- Android：新增 DNS 端点可用性与延迟检测。DoH 仅发送 HEAD，DoT 仅完成 TLS 握手，不发送域名查询；结果按真实可达端点展示，失败不会回填无效延迟。
- Android：修复国内直连在 fake-IP 下被误判为默认代理的问题。国内/私有域名现在返回真实地址，增加
  `GEOSITE,private`、`GEOIP,LAN` 直连规则，并启用本地 DNS 映射与 TLS/HTTP/QUIC 主机识别；应用级出口
  仍优先于国内直连，Chrome 等显式分流应用不会被 CN 规则悄悄改写。
- Android：新增 DNS 解析策略“统一解析 / 国内·海外分流”，通过 Mihomo `nameserver-policy` 将 `geosite:cn` 与海外域名交给不同加密上游；自定义、广告和家庭过滤配置会保持原有过滤语义。
- Android：自动节点策略新增“跨订阅自动”范围，把当前运行配置中的可用订阅合并为一个 `url-test`、`fallback` 或 `load-balance` 组；应用分流规则在该范围下统一命中跨订阅组。

- Android：深色极简模式改为深靛蓝/石墨三层色阶，修正浅色主色与浅色文字对撞，并降低玻璃面板亮边；DNS 新增阿里、腾讯、Cloudflare、Google、Quad9 与 Mullvad 加密上游，可分别选择 DoH 或 DoT。

- Android：修复部分 vivo/OriginOS 将未压缩 `.so` 保留在 APK 内、但 `nativeLibraryDir` 没有实体文件时被误报为“无法加载 Mihomo”；安装检测现在校验 base/ABI split APK 的原生库条目，加载时先解析 `libclash.so`，并为厂商 linker namespace 增加绝对路径兜底。

- Android：外观选择改为“极简风 / 艺术风”两级分组；极简风提供固定浅色和深色模式，艺术风保留四套莫奈主题。无历史外观设置的新安装默认使用极简浅色；已有艺术主题不会被覆盖。极简模式关闭装饰性渐变，减少视觉噪声和 GPU 绘制。

- Android：alpha39 修复部分国内 Wi‑Fi/蜂窝网络未及时获得 Android `VALIDATED` 能力时的启动失败；底层网络现在优先已验证网络，并安全回退到非 VPN 的可用物理网络。国内智能直连默认开启，CN 域名使用独立加密解析策略；IP 质量检测增加 DNS、IPv6/WebRTC 和综合 IP 的外部浏览器复核入口。

- Android：加入按需 IP 质量检测：通过固定 HTTPS 端点读取当前 IPv4/IPv6 出口、地区、ASN、ISP、边缘节点、代理/VPN/Tor/托管标签和多端点 RTT；结果只在内存中展示，DNS 泄漏和 WebRTC 明确保持“未测试”。
- Android：第三阶段加入本地域名、域名后缀、关键词和 IPv4/IPv6 CIDR 规则；规则使用 Android Keystore 加密保存，严格校验后编译进 Mihomo，应用规则优先，支持启停和删除。
- Android：Route Lens 现在可以解释本地规则命中（含域名/IP 输入），并正确区分规则模式、全局模式和直连模式；订阅页增加 HTTPS 远程订阅逐项手动刷新、进度和失败状态，不启用常驻后台任务。
- Android：第二阶段质量矩阵已接入订阅详情，使用三轮真实健康探测的中位延迟、P95、抖动、丢包和成功样本数做可解释排序；未探测字段保持未知。
- Android：新增离线策略包 `weave-policy/v1`，支持 SHA-256、可选 Ed25519 签名、受限规则类型、Keystore 加密存储和热重载；无签名包会显式标记为需复核。
- Android：局域网互传增加按订阅选择、6 位带外短码和安全同源合并；macOS/iOS 同步加入 6 位带外短码校验；继续使用 WVLAN001 兼容线协议，旧版跨端客户端仍可接收，Android 同名同源订阅更新前运行 Subscription Guard。

- Android：第一阶段安全能力落地。分流页新增本地 Route Lens 路由解释器；订阅原位更新新增
  Subscription Guard，空内容与节点数量灾变式变化会在加密存储提交前阻断并保留旧版本；
  设置页新增 Privacy Observatory 与 Recovery Vault，分别提供证据标注的隐私检查和候选/回滚失败后的
  安全模式恢复。以上功能不执行隐蔽联网测试、不保存明文凭据，也不生成虚假安全百分比。
- Android：原生桥接改为按需加载，应用启动、设置和订阅页面不再提前映射 Mihomo
  核心；后台/非首页时暂停运行时遥测轮询，可见首页的遥测间隔调整为 3 秒，并缓存
  渐变绘制资源，降低空闲耗电与内存峰值。
- Android：移除 Google Play services code scanner，二维码相机和图片识别统一改为本机
  系统相机预览 + ZXing；生成二维码改用 512px RGB565，图片解码限制为 1536px，减少
  临时 Bitmap 内存和依赖体积。

- Android：修复首次节点测速把 Mihomo 未初始化/失败哨兵显示为 `65535/65553 ms`；延迟值在核心边界统一限制为 1–10000 ms，三轮聚合把异常值计作失败，并对首轮全无效状态做最多 150 ms 的有界等待。
- Android：代理模式移除隐式直连兜底；失效或已删除的默认订阅不再自动授权物理网络直连，连接中删除最后一个代理会立即关闭旧内核。VPN 出站 socket 绑定到已验证的非 VPN 底层网络，Wi-Fi/蜂窝切换同步更新，底层网络全失时显式进入无上游状态。
- Android：广告/家庭 DNS 模式增加 DoT/DoQ 853 端口和常见公共 DNS IPv6 地址防绕过；HTTPS 订阅每次请求及重定向前检查 DNS 结果，拒绝回环、私网、链路本地、CGNAT 与组播目标；订阅编辑/导入/局域网互传界面阻止截图与最近任务预览，一次性链接按敏感剪贴板处理并在 60 秒后清除。
- iOS：新增 iOS 17+ 原生 SwiftUI 客户端初稿，包含 Liquid Glass × 莫奈四主题、连接/域名分流/订阅/设置四页、订阅与节点条件选择、二维码/照片/文件/HTTPS 导入和跨端局域网互传。
- iOS：新增 Keychain 主密钥保护的 AES-256-GCM 订阅库、事务化 App Group 运行配置、SHA-256 清单校验、IPv4-only 系统路由贯通和独立 `NEPacketTunnelProvider` target。
- iOS：移动内核通过窄 `WeaveMihomoMobile.xcframework` 契约隔离；framework 缺失、配置损坏或签名能力不足时 fail closed，不提供虚假连接状态。个人 iOS 版分流按域名工作，按应用 VPN 明确保留为 MDM 能力。
- Android：根据 Pixel 8 / Android 17 真机视觉复核重建为 Liquid Glass × 莫奈视觉系统：恢复清晰的无衬线字阶，以蓝紫、睡莲青和珊瑚柔光构成连续背景，并用冷暖折射渐变、白色高光边缘和悬浮阴影统一首页、分流、订阅、设置与底栏。
- Android：移除会让界面发脏的纸张纹理、伪笔触和半透明白灰叠层；针对 Android 17 的透明 RenderLayer 矩形残影，改用全不透明色彩折射模拟玻璃，保留玻璃感同时避免卡片内容区域出现白块。
- Android：新增 4 套低饱和莫奈风艺术主题（“日出·印象”“睡莲”“罂粟田”“暮色花园”），设置中可切换并持久化；画布、纸张、浮层、描边和状态色会随主题成组变化，避免白色与浅灰模块混用。
- Android / macOS：内部 UI 与新版编织结图标统一为暖象牙、深靛蓝、海玻璃青绿、淡紫和珊瑚配色；Android 面板增加轻微透纸层次与柔和背景渐变，macOS 同步降低分割线和纯白块的突兀感。
- Android / macOS：应用图标改为“编织结”艺术标记，使用深靛蓝、雾青、淡紫和珊瑚节点的柔和印象派配色，去除直白的 W/V、斜线和荧光色；Android 保留单色主题回退轮廓。
- Android：订阅导入统一支持 HTTPS、粘贴 URI/Base64、Clash YAML、sing-box JSON、基础 V2Ray JSON、二维码和文件；URI/JSON 会在本机转换为 Mihomo provider，复杂或缺字段协议 fail-closed。
- Android：手动节点测速继续保持按订阅懒加载，新增 SOCKS5/HTTP/SSR/AnyTLS 基础 URI 字段、V2Ray VMess/VLESS/Trojan/SS 基础 JSON 转换，并为旧版本记录增加运行时兼容转换。
- Docs：同步更新格式边界、能力矩阵和开源路线图，避免把尚未接通的入口标为已实现。

## 0.3.0-alpha31 — 2026-08-12

- Android / macOS：品牌图标改为非字母的“日蚀轨道”符号，由开放光环、酸橙轨道和单一节点组成，减少视觉噪声并提升小尺寸识别度。

## 0.3.0-alpha30 — 2026-08-12

- Android / macOS：再次重做品牌标记，采用上下交叠的织环弧线，去除直白的 W 轮廓和多余装饰，适配小尺寸启动器图标。

## 0.3.0-alpha29 — 2026-08-12

- Android：重做浅色主题的描边和分隔线，改用暖灰、半像素和内缩留白，减少黑线带来的突兀感。
- Android / macOS：将直白的编织 W 标记改为两条交叠丝带组成的抽象网络结，统一启动图标和侧边栏品牌标记。

## 0.3.0-alpha28 — 2026-08-12

- Android：广告过滤和家庭过滤现在会在 AdGuard DoH/DoT 之外，使用内置广告、跟踪器和成人域名规则；规则位于分流链最前面，能拦截浏览器和应用发起的域名连接。
- Android：fake-IP 对局域网、`.local` 和 `home.arpa` 使用真实地址，减少局域网服务被错误映射造成的卡顿。
- Android：设置页明确显示“DNS + 本地规则”，避免把 DNS 过滤误解成浏览器级元素隐藏。

## 0.3.0-alpha27 — 2026-08-12

### Fixed

- Filtering DNS profiles now block common browser Secure DNS endpoints so Chrome/Firefox cannot
  silently bypass the selected AdGuard resolver over HTTPS.
- DNS and fake-IP state is cleared when a runtime profile is reloaded, so changing from privacy DNS
  to ad/family filtering takes effect immediately instead of retaining old cached mappings.

## 0.3.0-alpha26 — 2026-08-12

### Changed

- Removed the universal Android APK from normal ABI builds; release output is now split per CPU architecture.
- Replaced bundled ML Kit image QR decoding with ZXing to reduce package size and offline model overhead.
- Flattened the Android paper surface, softened borders/accent contrast, and reduced bottom navigation/card elevation.
- Lazy-loaded the installed-app catalog and reduced visible runtime polling from 1 second to 2 seconds, while
  retaining the session timer and avoiding unchanged dashboard emissions.

## 0.3.0-alpha25 — 2026-08-12

### Added

- Versioned, affirmative VPN data-path disclosure before first connection.
- In-app security/privacy, routing, LAN transfer, and open-source component details.
- GitHub contribution, security, privacy, CI, dependency update, and release files.
- Adaptive woven-ribbon application icon shared by Android and macOS artwork.

### Security

- VPN runtime failures no longer expose arbitrary native exception text to logs or UI.

## 0.3.0-alpha24 — 2026-08-12

- Improved node availability testing, latency ordering, and retained prior results.
- Refined the warm mineral visual system.
- Verified Android 17 arm64 installation and startup.
