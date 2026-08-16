# 网络端点清单

这是公开预览版的可审计端点边界。Weave 不把这些请求汇总到自己的云服务；“用户主动”表示请求由用户导入、选择或点击触发，而不是后台遥测。

| 类别 | 端点/来源 | 触发条件 | 发送或返回的信息 |
| --- | --- | --- | --- |
| 订阅 | 用户粘贴或扫描的 `https://` URL | 用户导入或手动更新订阅 | 由订阅 URL 决定；请求使用 HTTPS，响应只在本机解析、审计和加密保存 |
| 代理/目标 | 用户订阅中的服务器和用户访问的目标服务 | VPN 连接和应用流量 | 由第三方协议和目标服务决定；Weave 不承诺第三方不记录 |
| 加密 DNS | 用户选择的 DoH/DoT 端点（内置预设或自定义） | VPN 运行期间的 DNS 查询 | 加密 DNS 查询；自定义端点不会写入日志或诊断包。内置 DoH：`dns.alidns.com/dns-query`、`doh.pub/dns-query`、`cloudflare-dns.com/dns-query`、`dns.google/dns-query`、`dns.quad9.net/dns-query`、`dns.mullvad.net/dns-query`、`dns.adguard-dns.com/dns-query`、`family.adguard-dns.com/dns-query`；DoT 使用相同主机名（`doh.pub` 对应 `dot.pub`） |
| 自动节点健康探测 | `http://www.gstatic.com/generate_204` | VPN 运行期间按自动策略的间隔探测 | 仅发送内核健康检查请求，读取 HTTP 状态和 RTT；不经过 Weave 云端 |
| 可达性 | `https://www.gstatic.com/generate_204` | 用户主动执行内核可用性测试；IP 质量检测也会测量该端点 | HTTPS 请求和响应状态/RTT |
| IPv4 出口 | `https://api4.ipify.org` | 用户点击 IP 质量检测 | 当前请求视角的 IPv4 |
| IPv6 出口 | `https://api6.ipify.org` | 用户点击 IP 质量检测 | 当前请求视角的 IPv6（不可用时显示未测试） |
| IP 元数据 | `https://ipwho.is` | 用户点击 IP 质量检测 | IP、地区、ASN、组织和第三方标签（取决于服务响应） |
| 边缘视角 | `https://www.cloudflare.com/cdn-cgi/trace`、`https://cp.cloudflare.com/generate_204` | 用户点击 IP 质量检测 | 边缘机房、HTTP 可达性和 RTT |
| 局域网互传 | 当前局域网中用户明确选择的私有 IPv4 | 用户点击生成或扫描一次性二维码/链接 | AES-256-GCM 密文；密钥只放在 `weave://` 链接 fragment，不放进 HTTP 请求 |

以下不是默认网络端点：Weave 没有账号、广告/统计、崩溃上报、远程配置、远程更新、内置节点或集中式控制 API。固定端点、协议和用户可配置端点发生变化时，必须同步修改本清单、[`PRIVACY.md`](../PRIVACY.md) 和发布审计。
