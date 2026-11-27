# 网络端点清单

这是公开预览版的可审计端点边界。Weave 不把这些请求汇总到自己的云服务；“用户主动”表示请求由用户导入、选择或点击触发，而不是后台遥测。

| 类别 | 端点/来源 | 触发条件 | 发送或返回的信息 |
| --- | --- | --- | --- |
| Android 本地内核观测 | 应用私有 cache 下 `core-observation/api.sock`（Unix socket，非 TCP） | 用户点击“读取实际命中”，或选择“链路终端”外观并停留在已连接的首页 | 父目录显式 0700；手动视图最多展示 40 条、两分钟清除；极客首页可见时每 3 秒读取并仅展示前 8 条，离开页面或退到后台即停。不保留响应中的源/目标地址、域名和规则匹配值，不向第三方发送快照 |
| 短时下载测速 | `https://speed.cloudflare.com/__down?bytes=1048576` | 用户点击首页或检测中心的测速按钮且 VPN 已连接 | 请求与 DNS 查询绑定到启动时的 Android VPN 网络。最多读取 1 MiB，拒绝重定向，正文不会落盘。界面分别显示 HTTPS 响应耗时与正文读取吞吐 |
| Android Weave Next 版本检查 | `https://api.github.com/repos/sundaysebasidian-byte/weave-client/git/matching-refs/tags/weave-next-v`、同仓库 `/releases/tags/{已验证标签}` | 官方签名 ARM64 版打开应用时默认检查，每 24 小时最多一次；可在「关于 → 应用更新」关闭，仍可手动检查 | 匿名 HTTPS 请求；GitHub 可见请求出口 IP 及常规 HTTP 信息。无订阅、分流规则、设备标识或诊断上报；开关和检查时间仅保存在本机 |
| Android Weave Next 安装包下载 | 同仓库 `https://github.com/sundaysebasidian-byte/weave-client/releases/download/{已验证标签}/{对应 ARM64 APK 文件名}`，仅允许跳转到匹配该仓库附件路径的 `https://release-assets.githubusercontent.com` | 用户主动点击「下载并验证」 | 附件主机可见请求出口 IP 及常规 HTTP 信息；安装包在本机验证，只有用户通过系统选择器保存后才能在「文件」中发起安装；Weave 不自行安装 |
| 订阅 | 用户粘贴或扫描的 `https://` URL | 用户导入、手动更新；启用自动更新后按所选周期在后台更新（默认关闭） | 由订阅 URL 决定；请求使用 HTTPS，响应只在本机解析、审计和加密保存 |
| 订阅节点集合 | 导入配置中明确列出的 HTTPS `proxy-providers` 地址 | 导入或更新该订阅时，一次最多 16 个集合；重复 URL 只取一次 | 同样执行 HTTPS、重定向、私网地址和大小检查；只展开节点，不下载 rule-providers。任何子集合失败都不覆盖旧订阅；启动 VPN 不重新下载这些文件 |
| 代理/目标 | 用户订阅中的服务器和用户访问的目标服务 | VPN 连接和应用流量 | 由第三方协议和目标服务决定；Weave 不承诺第三方不记录 |
| 加密 DNS | 用户选择的 DoH/DoT 端点（内置预设或自定义） | VPN 运行期间的 DNS 查询 | 加密 DNS 查询；自定义端点不会写入日志或诊断包。内置 DoH：`dns.alidns.com/dns-query`、`doh.pub/dns-query`、`cloudflare-dns.com/dns-query`、`dns.google/dns-query`、`dns.quad9.net/dns-query`、`dns.mullvad.net/dns-query`、`dns.adguard-dns.com/dns-query`、`family.adguard-dns.com/dns-query`；DoT 使用相同主机名（`doh.pub` 对应 `dot.pub`） |
| 引导 DNS | 默认 `223.5.5.5`、`119.29.29.29`；可选 `1.1.1.1`、`9.9.9.9` | 内核需要解析加密 DNS 服务器主机名时 | 明文 DNS 查询；不把它当作普通网站的加密解析器，也不宣称零泄漏 |
| 自动节点健康探测 | `http://www.gstatic.com/generate_204` | VPN 运行期间按自动策略的间隔探测 | 仅发送内核健康检查请求，读取 HTTP 状态和 RTT；不经过 Weave 云端 |
| 可达性 | `https://www.gstatic.com/generate_204` | 用户主动执行内核可用性测试；IP 质量检测也会测量该端点 | HTTPS 请求和响应状态/RTT |
| IPv4 出口 | `https://api4.ipify.org` | 用户点击 IP 质量检测 | 当前请求视角的 IPv4 |
| IPv6 出口 | `https://api6.ipify.org` | 用户点击 IP 质量检测 | 当前请求视角的 IPv6（未取得时显示未知） |
| IP 元数据 | `https://ipwho.is` | 用户点击 IP 质量检测 | IP、地区、ASN、组织和第三方标签（取决于服务响应） |
| 边缘视角 | `https://www.cloudflare.com/cdn-cgi/trace`、`https://cp.cloudflare.com/generate_204` | 用户点击 IP 质量检测 | 边缘机房、HTTP 可达性和 RTT |
| 常用站点可达性与解锁入口证据 | `https://x.com/`、`https://www.tiktok.com/`、`https://www.youtube.com/`、`https://www.google.com/generate_204`、`https://chatgpt.com/`、`https://claude.ai/`、`https://www.netflix.com/title/80057281`、`https://www.facebook.com/`、`https://www.disneyplus.com/` | 用户在“网络与隐私检测”点击运行完整检测且 VPN 已连接 | 仅发送 HTTPS `GET` 的 `Range: bytes=0-0` 请求，记录 HTTP 状态和 RTT；不读取或保存网页正文。Netflix/Facebook/Disney+ 仅作为入口响应证据，不能证明账号、内容或播放级解锁；401/403/429/重定向只表示服务或入口受限 |
| WebRTC 本机检测 | WebView 本地页面 | 用户主动运行网络与隐私检测或浏览器检测 | WebView 阻止网络资源加载，ICE 未配置外部 STUN 或 TURN。候选和指纹仅在本机内存展示。外部 DNS 与浏览器泄漏需另行核验 |
| 应用内第三方复核 | `https://www.dnsleaktest.com/`、`https://browserleaks.com/webrtc`、`https://browserleaks.com/javascript`、`https://browserleaks.com/ip` | 用户在隐私检测页明确点击对应测试 | 在受限 WebView 中显示；站点可看到当前出口 IP，并接收其测试所需的 DNS/浏览器请求。Weave 不抓取或保存页面结果；这些测试只代表该 WebView，不证明 Chrome 或其他应用无泄漏 |
| 局域网互传 | 当前局域网中用户明确选择的私有 IPv4 | 用户点击生成或扫描一次性二维码/链接 | AES-256-GCM 密文；密钥只放在 `weave://` 链接 fragment，不放进 HTTP 请求 |

以下不是默认网络端点：Weave 没有账号、广告/统计、崩溃上报、远程配置、静默安装、内置节点或集中式控制 API。固定端点、协议和用户可配置端点发生变化时，必须同步修改本清单、[`PRIVACY.md`](../PRIVACY.md) 和发布审计。
