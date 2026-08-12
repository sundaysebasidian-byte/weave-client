# Android 能力矩阵

这份矩阵用于约束 Android 客户端不出现“写了入口但运行时没接通”的能力。`已实现`表示 UI、
持久化、配置编译和现有 CMFA/Mihomo 数据面已经形成闭环；`后续`不会在当前界面伪装成可用。

| 能力 | alpha22 状态 | 实现边界 |
|---|---|---|
| Clash YAML 协议节点 | 已实现 | Mihomo provider 可承载 VLESS/Reality、VMess、Trojan、Hysteria/Hysteria2、TUIC、WireGuard、Shadowsocks/SSR、SOCKS5、HTTP、AnyTLS 与 OpenVPN；实际字段兼容性由锁定的内核版本决定 |
| URI / Base64 / sing-box JSON | 仅安全解析元数据 | 可以识别并展示；尚未通过协议完整转换测试，因此不会拿去启动内核 |
| 多订阅 | 已实现 | 不同订阅隔离为不同 provider，可让不同应用引用不同订阅的自动组或固定节点 |
| 订阅差异与安全更新 | 已实现 | 版本化加密 payload 原子切换，提交后显示新增/移除/保留；保留节点 ID，避免重排破坏固定引用 |
| 订阅合并与跨订阅节点去重 | 后续 | 同名同协议只标记可能重复；解析器取得完整参数指纹前不会误删参数不同的节点 |
| 自动测速 | 已实现 | `url-test`，300 秒间隔、80 ms 容差、按需懒探测 |
| 最低延迟 / 故障切换 / 负载均衡 | 已实现 | 分别编译为 `url-test`、`fallback`、一致性哈希 `load-balance` |
| 节点健康面板 | 已实现 | 连接页/订阅详情可手动触发三轮 provider 健康检查，显示中位延迟、P95、抖动与丢包；未加载订阅会临时加入候选运行配置 |
| 应用分流 | 已实现 | UID 优先、包名兼容回退，可选订阅自动组、固定节点、直连或阻止 |
| 国内智能直连 / GeoIP / Geosite | 已实现 | 可选随包 lite CN 数据；哈希和来源锁定，应用规则优先，运行时不静默更新 |
| 海外服务分类与自定义 Geo 规则 | 后续 | 需要规则命中解释、冲突预览和可回滚的数据更新机制后再开放入口 |
| 自定义域名、远程规则集 | 后续 | 尚无入口；需要哈希固定、原子更新和命中解释器 |
| DNS | 已实现 | fake-IP 全隧道接管，可选 DoH 或 DoT；代理服务器域名使用相同加密上游，明文 DNS 仅用于加密上游域名引导 |
| 自定义 DNS / DoQ / 分流 DNS | 部分实现 | 设置页支持广告过滤（AdGuard DoH/DoT + 内置域名规则）、家庭过滤和自定义 DoH/DoT；DoQ 与分流 DNS 尚未开放 |
| IPv6 | 已实现 | 双栈或仅 IPv4；仅 IPv4 会关闭内核/DNS IPv6，并在 TUN 中拒绝 IPv6 |
| 速度优化 | 部分实现 | `tcp-concurrent`、`unified-delay` 与懒测速已启用；未宣称未经设备矩阵验证的 TCP Fast Open |
| 后台稳定 | 已实现基础 | Android 前台 `VpnService`、`START_STICKY`、非 VPN 底层网络监听和 1.5 秒去抖事务恢复；仍需真机长时测试 |
| 配置热重载 | 已实现事务回退 | 候选配置只解析不应用；校验失败保留旧 TUN，启动失败恢复旧配置/provider；进程崩溃测试仍待设备矩阵 |
| Kill Switch | 已实现系统入口 | 打开 Android Always-on / Block connections without VPN 设置，不复制不可靠的应用内假开关 |
| DNS 防泄漏 | 已实现 | IPv4/IPv6 DNS 地址均进入 TUN，由 Mihomo DNS 模块接管 |
| WebRTC 防护 | 可选实现 | 拒绝常见 UDP STUN 端口；不是浏览器级 WebRTC 总开关，可能影响通话 |
| AI 自动优化 | 后续研究 | 未建立本地、可解释、可回滚的决策与隐私模型前不提供入口 |

当前 Android 功能不等于 Windows、Linux 或 iOS 已实现。macOS 的独立能力与 Apple Network
Extension 权限边界见 `UI_ENTRYPOINT_AUDIT.md`。
