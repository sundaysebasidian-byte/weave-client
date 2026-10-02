# Android 客户端能力核对（2026-09）

本表区分「内核能解析」「Weave 已接线」「已在真机验证」。不能因为 Mihomo 支持某个字段，就宣称 Weave 对所有来源、协议和配置都完整兼容。Windows 预览版不继承这里的 Android 结论。

| 用户关心的优点 | Weave Android 当前状态 | 尚不能宣称的部分 |
| --- | --- | --- |
| 多协议节点 | Clash YAML 节点由随包 Mihomo 原生解析；常见 URI/Base64、sing-box/V2Ray JSON 的基础字段可转换；sing-box SOCKS5 认证、AnyTLS、Hysteria 1 的安全子集已补齐 | 不是「全协议、所有扩展字段」；不兼容的传输及 AnyTLS+Reality 明确拒绝，仍需逐协议金样和真机联网回归 |
| 易用选节点与分流 | 订阅→自动/固定节点、按应用直连/阻止/代理、域名/IP/国内直连、本地规则优先级排序、Route Lens 已接入运行配置 | 尚无任意自定义策略组、远程 rule-provider 或与完整 Clash 规则文件等价的图形化编辑 |
| Meta 配置可视化 | 节点与 provider 集合会导入；订阅详情可展开查看原代理组的名称、类型、显式成员及 provider 引用，并显示未执行的规则数量；预览加密保存在本机 | 原 `proxy-groups`、`rules`、`rule-providers` 不会参与实际路由；远程 icon 不自动下载；不能宣传为「完整 Meta 配置适配」 |
| DNS 与隐私 | fake-IP、加密 DoH/DoT、国内/海外解析策略、过滤选项、IPv6 防旁路与按需检测 | 未提供 sing-box 的 DNS 规则编辑器、DoQ 或 Tailscale endpoint；隐私检测不能证明绝对无泄漏 |
| 链式代理 | 当前 Android UI/运行配置未接线 | Mihomo 已弃用 relay 组，未来应使用 `dialer-proxy`；需要处理跨订阅引用、DNS、UDP、环路与故障回滚 |
| 省电与稳定 | 懒测速、前台 VPN、网络切换恢复、事务回滚及首页长期零流量时的分级降频已有实现；不兼容出站转换会在导入阶段拒绝 | 没有同机同节点的长期对照数据，不宣称比 CMFA/sing-box 更省电或更稳定 |
| 同步与导入 | HTTPS/文件/二维码/粘贴、手动更新、选定订阅的加密局域网分享 | 无云同步、自动后台更新或任意客户端数据无损迁移 |

旧版本已经规范化保存的订阅只保留节点，不可能从本地还原被删去的原代理组；需重新导入或更新源文件才有代理组预览。局域网分享目前传输的是规范化节点内容，不传输这份仅供查看的原配置预览。

## 下一步按风险实施

1. **配置兼容**：现已保留原代理组的安全预览（不抓取 icon）；后续逐协议测试扩展字段，并展示规则引用。任何执行原规则/远程 provider 的选项都必须明确授权、限制下载和做好回滚。
2. **链式代理**：用 `dialer-proxy` 做两个固定节点的最小闭环；先做配置生成与内核校验，再在 Android 真机验证 TCP/UDP、DNS 与切换后的出口，最后开放 UI。
3. **高级分流**：本地规则集导入与命中解释先行；远程规则集需签名/哈希、大小/域名限制、明确刷新策略和失败保留旧版本。
4. **性能结论**：固定设备、订阅、节点与流量样本，与 CMFA/sing-box 对照 24 小时前后台耗电、PSS 内存、切网和重连，不用主观体感代替测试。

参考：[Mihomo 代理节点及 dialer-proxy](https://wiki.metacubex.one/en/config/proxies/)、[Mihomo rule-provider](https://wiki.metacubex.one/en/config/rule-providers/)、[sing-box DNS](https://sing-box.sagernet.org/configuration/dns/)、[sing-box Tailscale endpoint](https://sing-box.sagernet.org/configuration/endpoint/tailscale/)。
