# IP 质量检测

Android 端的 IP 质量检测位于连接首页，只有用户主动点击时运行。它不上传到 Weave 服务，也不把结果写入订阅、日志或诊断包。

## 检测内容

- `api4.ipify.org` / `api6.ipify.org`：分别确认当前可见 IPv4、IPv6 出口。
- `ipwho.is`：读取 IP、国家/地区/城市、ASN、组织/ISP 和第三方代理、VPN、Tor、托管标签。
- Cloudflare trace：读取边缘机房和另一个出口视角。
- Cloudflare 与 Google 的 `generate_204`：测量真实 HTTPS 可达性与 RTT。

所有请求都通过当前 Android VPN 数据路径发出，并限制 HTTPS、超时和响应大小。公网地址会拒绝回环、私网、链路本地、组播、CGNAT 和文档保留网段。

## 结果边界

“已确认”只表示对应端点返回了可解析证据，不代表匿名、无恶意或网站一定信任该 IP。地区、ASN 和代理标签由第三方数据库提供，可能过期或误判。DNS 泄漏、WebRTC 候选地址、浏览器 Secure DNS 和网站信誉需要在 Chrome 等真实浏览器中使用外部测试页复核；应用不会把 HTTP 结果冒充这些测试的结论。
