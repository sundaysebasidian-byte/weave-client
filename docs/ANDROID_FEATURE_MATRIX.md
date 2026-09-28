# Android 能力矩阵

这份矩阵用于约束 Android 客户端不出现“写了入口但运行时没接通”的能力。`已实现`表示 UI、
持久化、配置编译和现有 CMFA/Mihomo 数据面已经形成闭环；`后续`不会在当前界面伪装成可用。

| 能力 | 状态（0.4.0-alpha1） | 实现边界 |
|---|---|---|
| Clash YAML 协议节点 | 已实现 | Mihomo provider 可承载 VLESS/Reality、VMess、Trojan、Hysteria/Hysteria2、TUIC、WireGuard、Shadowsocks/SSR、SOCKS5、HTTP、AnyTLS 与 OpenVPN；实际字段兼容性由锁定的内核版本决定 |
| URI / Base64 / sing-box / V2Ray JSON | 已实现基础运行转换 | 导入、二维码、粘贴文本和文件统一转换为本地 Clash provider；不支持的复杂字段 fail-closed，原始 Clash YAML 不改写 |
| 多订阅 | 已实现 | 不同订阅隔离为不同 provider，可让不同应用引用不同订阅的自动组或固定节点 |
| 订阅差异与安全更新 | 已实现 | 版本化加密 payload 原子切换，提交后显示新增/移除/保留；保留节点 ID，避免重排破坏固定引用 |
| Subscription Guard 订阅审计 | 已实现 | 提交前检查空内容、节点数量灾变式变化、格式/来源主机变化和重复节点；阻断项保留旧版本，提示项需要复核 |
| 订阅合并与跨订阅节点去重 | 后续 | 同名同协议只标记可能重复；解析器取得完整参数指纹前不会误删参数不同的节点 |
| 自动测速 | 已实现 | `url-test`，300 秒间隔、80 ms 容差、按需懒探测；可切换为按订阅独立或跨订阅自动组 |
| 最低延迟 / 故障切换 / 负载均衡 | 已实现 | 分别编译为 `url-test`、`fallback`、一致性哈希 `load-balance` |
| 节点健康面板 | 已实现 | 连接页/订阅详情可手动触发三轮 provider 健康检查，显示中位延迟、P95、抖动与丢包；未加载订阅会临时加入候选运行配置；1–10000 ms 之外的核心哨兵计作失败样本 |
| 质量矩阵 | 已实现 | 订阅详情按透明稳定度排序，展示中位延迟、P95、抖动、丢包和成功样本；DNS/TLS/UDP/带宽未探测时保持未知，不估算 |
| 应用分流 | 已实现 | UID 优先、包名兼容回退，可选订阅自动组、固定节点、直连或阻止 |
| 应用内语言 | 已实现 | 设置中支持简体中文、繁體中文、English、日本語、Français、Deutsch；选择持久化到本机，用户输入的节点/订阅/应用名称保持原文 |
| 国内智能直连 / GeoIP / Geosite | 已实现 | 默认启用随包 lite CN 数据；哈希和来源锁定，CN 域名返回真实地址以便 GEOIP 分类，应用规则优先，CN direct 使用独立解析策略，运行时不静默更新 |
| 海外服务分类与自定义 Geo 规则 | 后续 | 需要规则命中解释、冲突预览和可回滚的数据更新机制后再开放入口 |
| 本地域名、关键词、IPv4/IPv6 CIDR | 已实现 | 设置中加密保存；应用规则优先；编译前严格校验 |
| 远程规则集 | 已实现 | 用户添加的 HTTPS 域名/IP/经典规则列表；逐条校验、SHA-256 记录、本机加密；以内联 rule-provider 编入，位于本地规则之后、国内直连之前；只在添加或手动刷新时联网 |
| 自定义策略组与链式代理 | 已实现 | 跨订阅挑选节点组成最低延迟/故障切换/负载均衡组；可设入口节点，通过 `dialer-proxy` 形成两跳链；可作为默认出口或应用出口，删除订阅时失效组 fail-closed |
| DNS | 已实现 | fake-IP 全隧道接管，可选 DoH 或 DoT；内置国内、阿里、腾讯、Cloudflare、Google、Quad9、Mullvad、AdGuard 过滤上游；设置页可按端点实测 HTTPS/TLS 可用性与 RTT |
| 自定义 DNS / 分流 DNS | 已实现基础 | 设置页支持广告过滤、家庭过滤、自定义加密 DoH/DoT，以及基于 `geosite:cn` 与 `geosite:geolocation-!cn` 的国内/海外 nameserver-policy；DoQ 与自定义规则化 DNS 仍后续 |
| IPv6 | 已实现 | 双栈或仅 IPv4；仅 IPv4 会关闭内核/DNS IPv6，并在 TUN 中拒绝 IPv6 |
| 速度优化 | 部分实现 | `tcp-concurrent`、`unified-delay` 与懒测速已启用；未宣称未经设备矩阵验证的 TCP Fast Open |
| 后台稳定 | 已实现基础 | VPN 与内核运行于独立 `:vpn` 进程，UI 崩溃或回收不影响隧道；Android 前台 `VpnService`、`START_STICKY`、非 VPN 底层网络监听；Android 10+ 只用 `protect()` 让核心 socket 跟随系统换网，Android 8/9 保留 underlying-network 元数据和 2.5 秒去抖恢复；晚到的出站保护失败按退避重建上一份健康配置，无上游时保持 fail-closed，仍需真机长时测试 |
| 配置热重载 | 已实现事务回退 | 候选配置只解析不应用；校验失败保留旧 TUN，启动失败恢复旧配置/provider；进程崩溃测试仍待设备矩阵 |
| Recovery Vault 恢复中心 | 已实现 | 只持久化失败与快照元数据；候选和回滚均失败时进入安全模式，用户主动解除后才能重新连接 |
| Kill Switch | 已实现系统入口 | 打开 Android Always-on / Block connections without VPN 设置，不复制不可靠的应用内假开关 |
| DNS 防泄漏 | 已实现 | IPv4/IPv6 DNS 地址均进入 TUN，由 Mihomo DNS 模块接管；所有配置阻断应用自发的明文 TCP/UDP 53、DoT/DoQ 853、已知公共 DoH 域名和公共 DNS IPv4/IPv6，广告/家庭模式再叠加本地过滤规则；自定义浏览器 DoH 需在浏览器内关闭 |
| WebRTC 防护 | 可选实现 | 拒绝常见 UDP STUN 端口；不是浏览器级 WebRTC 总开关，可能影响通话 |
| Route Lens 路由解释 | 已实现 | 本地模拟应用规则、本地域名/IP规则、默认出口、DNS、UDP/QUIC 与 IPv6 状态；不执行网络请求，未知项明确标注 |
| IP 质量检测 | 已实现基础 | 用户主动触发固定 HTTPS 出口探测，展示 IPv4/IPv6、ASN/地区/ISP、边缘节点、第三方代理标签和 RTT；DNS 泄漏、WebRTC、网站信誉仍需外部浏览器测试 |
| 远程订阅刷新 | 已实现 | 手动刷新之外可选定时更新（默认关闭；6/12/24 小时，默认仅不计流量网络）；同样执行安全审计，影响固定出口的变化保留旧版本并通知预览；显示 `subscription-userinfo` 流量与到期 |
| 快捷入口 | 已实现 | 快捷设置磁贴、应用快捷方式、`clash://`/`clashmeta://`/`flclash://`/`sing-box://`/`hiddify://` 链接与分享导入（均需用户确认来源）；通知栏断开按钮与亮屏时实时网速 |
| 实时连接与内核日志 | 已实现 | 通过私有 Unix socket 读取；可逐条或全部断开连接；日志仅内存保留 500 行，复制默认脱敏；防截屏 |
| 系统代理与局域网共享 | 可选实现 | 默认关闭；系统 HTTP 代理使用 127.0.0.1:7890；局域网共享需随机用户名密码，本机回环免认证 |
| 直连应用绕过 VPN | 可选实现 | 规则模式下选为直连的应用通过 `addDisallowedApplication` 不进入隧道 |
| 加密备份 | 已实现 | PBKDF2-HMAC-SHA256 + AES-256-GCM 的 `.weavebackup`；订阅经 LAN Sync 同一审计路径恢复并保留 ID |
| Privacy Observatory 隐私观测 | 已实现 | 根据本地 VPN、DNS 旁路拒绝、过滤、IPv6、STUN 与直连配置生成证据报告；按用户操作并行检查 HTTPS IPv4/IPv6 出口，并用本机 WebView 采集 ICE 候选和浏览器身份表面做出口交叉验证；DNS 泄漏及 Always-on/断网保护仍需外部或系统设置复核，未知/未测试不转换为安全百分比 |
| AI 自动优化 | 后续研究 | 未建立本地、可解释、可回滚的决策与隐私模型前不提供入口 |
| 离线策略包 | 已实现 | `weave-policy/v1` 本地 JSON；SHA-256 必须匹配，可选 Ed25519 签名，Keystore 加密保存；规则值和数量有上限，未签名包需人工复核 |
| LAN Sync 2.0 | 已实现基础 | 可按订阅选择导出、二维码后核对发送端显示的 6 位短码；WVLAN001 保持 macOS/iOS 兼容，同名同源订阅在 Android 侧经审计原位更新 |

当前 Android 功能不等于 Windows、Linux 或 iOS 已实现。macOS 的独立能力与 Apple Network
Extension 权限边界见 `UI_ENTRYPOINT_AUDIT.md`。
