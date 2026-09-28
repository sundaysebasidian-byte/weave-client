# 客户端能力差距与路线图

本表以 Weave 当前 Android 实现为基线，对照公开的 Mihomo API/配置能力、v2rayNG
公开说明和 Karing 官方功能列表。它只记录已经核对过的能力，不把尚未接通的入口标为已实现。

| 能力 | Weave 当前状态 | 对照后发现的差距 | 优先级 |
| --- | --- | --- | --- |
| 手动节点选择 | 已实现 | 选择订阅后可对该订阅节点做多轮测速，按综合质量排序并固定到单节点；未加载订阅会临时加入运行配置 | P0 |
| 自动节点策略 | 已实现 | 每订阅与跨订阅自动组；自定义策略组与两跳链式代理已实现（0.4.0-alpha1）；更多跳链与正则筛选组仍缺 | P2 |
| 订阅格式 | Clash YAML、URI/Base64、sing-box JSON、基础 V2Ray JSON 均可转换为运行 provider；二维码/粘贴文本/文件入口统一走同一校验链 | V2Ray 专有 JSON 字段与厂商自定义扩展仍需逐源金样；不支持的字段会 fail-closed | P0 |
| 协议覆盖 | URI 已覆盖 VLESS、VMess、Trojan、SS/SSR、Hysteria、Hysteria2、TUIC、SOCKS5、HTTP、AnyTLS 的基础字段；Clash YAML 仍由锁定 Mihomo 原生承载 | Reality、WireGuard、OpenVPN 等复杂字段依赖 Clash/sing-box 原生配置或后续金样转换；当前不会猜测缺失私钥/地址 | P0 |
| 分流规则 | UID/包名应用分流、GeoIP/Geosite、内置 DNS 过滤、本地域名/IP、远程规则集（SHA-256）、命中解释 | 复杂冲突预览、二进制 .mrs 规则集仍缺 | P2 |
| DNS | DoH/DoT、广告/家庭过滤、fake-IP、IPv6 防旁路、端点 RTT/可用性检测、内置国内/海外 nameserver-policy | DoQ、规则化 fake-IP、自定义策略编辑仍未开放 | P1 |
| 观测与诊断 | 节点延迟/抖动/丢包、质量矩阵、实时速率与曲线、实时连接列表（可断开）、内存内内核日志 | 尚缺内存占用、按策略流量统计、延迟测试地址/超时设置 | P2 |
| 订阅管理 | HTTPS/文件/二维码/深链接/分享导入、编辑、删除、局域网同步、可选定时更新与通知、流量与到期、加密备份 | 尚缺跨订阅去重、WebDAV 同步 | P2 |
| 离线策略 | 本地 `weave-policy/v1`、SHA-256、可选 Ed25519、Keystore 存储和热重载 | 尚缺签名密钥管理界面、规则集版本自动更新和跨平台策略包编辑器 | P1 |
| Android 可靠性 | 独立 `:vpn` 进程、前台 VPN、热重载回滚、非阻塞停止、系统 Always-on/无 VPN 阻断入口 | 仍需完成 Doze、锁屏、开机、网络切换、内核崩溃和 100 次重连压力矩阵（真机） | P0 |

## 实施顺序

1. P0：继续补齐各协议金样回归（Reality、WireGuard、OpenVPN、AnyTLS 扩展字段），保持
   fail-closed 错误提示；基础 URI/Base64/sing-box/V2Ray 转换已接入导入与运行时。
2. P1：增加自定义规则集、规则命中解释和跨订阅自定义策略组；同时接入 Mihomo 观测 API，形成
   “当前连接—连接列表—策略—节点质量”的闭环。
3. P1/P2：加入可取消的后台订阅刷新、差异通知与加密 ZIP/WebDAV/LAN 同步；当前已先支持手动 HTTPS 刷新。
4. 发布前：完成真机矩阵、第三方依赖许可证/源码、SBOM、可复现构建和签名验证。

## 公开对照来源

- [Mihomo API](https://wiki.metacubex.one/en/api/)
- [Mihomo proxy-groups](https://wiki.metacubex.one/en/config/proxy-groups/)
- [Mihomo DNS](https://wiki.metacubex.one/en/config/dns/)
- [v2rayNG README](https://github.com/2dust/v2rayNG)
- [Karing README](https://github.com/KaringX/karing)
- [Karing Quick Start](https://karing.app/en/quickstart)
