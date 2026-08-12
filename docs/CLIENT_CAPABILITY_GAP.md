# 客户端能力差距与路线图

本表以 Weave 当前 Android 实现为基线，对照公开的 Mihomo API/配置能力、v2rayNG
公开说明和 Karing 官方功能列表。它只记录已经核对过的能力，不把尚未接通的入口标为已实现。

| 能力 | Weave 当前状态 | 对照后发现的差距 | 优先级 |
| --- | --- | --- | --- |
| 手动节点选择 | 已实现 | 选择订阅后可对该订阅节点做多轮测速，按综合质量排序并固定到单节点；未加载订阅会临时加入运行配置 | P0 |
| 自动节点策略 | 已实现 | 每订阅 `url-test`、`fallback`、`load-balance`；尚缺跨订阅自定义策略组、relay/链式代理和用户自定义筛选组 | P1 |
| 订阅格式 | 当前以 Clash YAML 为可运行输入 | URI/Base64、sing-box JSON、V2Ray 批量链接仍只做安全识别，尚未转换为运行配置；这是当前兼容性最大缺口 | P0 |
| 协议覆盖 | Clash provider 可承载锁定 Mihomo 支持的节点类型 | 还需要逐协议字段回归样本，尤其 Reality、Hysteria2、TUIC、WireGuard、AnyTLS 和 OpenVPN 的订阅转换/异常提示 | P0 |
| 分流规则 | UID/包名应用分流、GeoIP/Geosite、内置 DNS 过滤 | 尚缺自定义域名、远程 rule-provider、规则集更新哈希、命中解释和冲突预览 | P1 |
| DNS | DoH/DoT、广告/家庭过滤、fake-IP、IPv6 防旁路 | Mihomo 的 nameserver-policy、分流 DNS、DoQ、规则化 fake-IP 仍未开放 | P1 |
| 观测与诊断 | 节点延迟/抖动/丢包、实时上下行速率、应用归属计数 | 尚缺连接列表、内核日志、内存、按策略流量统计、延迟测试地址/超时设置；Mihomo API 已提供这些控制面能力 | P1 |
| 订阅管理 | HTTPS/文件/二维码、编辑、删除、一次性局域网互传 | 尚缺后台定时更新、失败重试/通知、跨订阅去重、流量信息刷新、ZIP/WebDAV/多设备同步 | P1 |
| Android 可靠性 | 前台 VPN、热重载回滚、系统 Always-on/无 VPN 阻断入口 | 仍需完成 Doze、锁屏、开机、网络切换、内核崩溃和 100 次重连压力矩阵 | P0 |

## 实施顺序

1. P0：完成 URI/Base64/sing-box/V2Ray 批量链接的协议完整转换，并为每种协议加入金样配置与
   fail-closed 错误提示。
2. P1：增加自定义规则集、规则命中解释和跨订阅策略组；同时接入 Mihomo 观测 API，形成
   “当前连接—连接列表—策略—节点质量”的闭环。
3. P1/P2：加入可取消的后台订阅刷新、差异通知与加密 ZIP/WebDAV/LAN 同步。
4. 发布前：完成真机矩阵、第三方依赖许可证/源码、SBOM、可复现构建和签名验证。

## 公开对照来源

- [Mihomo API](https://wiki.metacubex.one/en/api/)
- [Mihomo proxy-groups](https://wiki.metacubex.one/en/config/proxy-groups/)
- [Mihomo DNS](https://wiki.metacubex.one/en/config/dns/)
- [v2rayNG README](https://github.com/2dust/v2rayNG)
- [Karing README](https://github.com/KaringX/karing)
- [Karing Quick Start](https://karing.app/en/quickstart)
