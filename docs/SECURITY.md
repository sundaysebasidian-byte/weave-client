# 安全设计与发布门槛

代理客户端处于设备网络的高信任位置。Weave 的安全目标不是“用了 TLS 就安全”，而是保证
订阅供应方、规则集、内核、更新通道或诊断流程中的一个环节出问题时，影响仍然可控。

## 威胁模型

公开版按 `local-open-source` 配置发布：Weave 不运营账号、云端控制面、代理中继、节点市场、
遥测、崩溃上报或应用远程更新。用户主动选择的订阅、代理、DNS、IP 质量和局域网端点不属于
Weave 的信任边界，固定端点与触发条件见 [`NETWORK_ENDPOINT_INVENTORY.md`](NETWORK_ENDPOINT_INVENTORY.md)。
这是工程边界，不是法律豁免、匿名或“零日志”证明。

需要防范：

- 恶意或被劫持的订阅返回超大、畸形或危险配置；
- 订阅 URL token、节点凭据、DNS 查询和访问域名被日志或备份泄露；
- 远程规则集更新改变流量出口；
- WebView、深链或剪贴板内容触发未经确认的配置；
- 原生内核漏洞、ABI 供应链替换和不可信自动更新；
- VPN 表面显示连接但 TUN 无消费者、DNS/IPv6 泄漏或网络切换后绕过；
- 本机其他应用调用导入、启动或停止组件。

不承诺防范已经取得 root、能读取应用进程内存并控制系统网络栈的攻击者。

## 强制安全默认值

- Manifest 禁止明文流量：`usesCleartextTraffic=false`，并通过 `network_security_config.xml` 明确只信任
  Android 系统 CA（不把用户安装 CA 静默带入订阅和诊断请求）。这会牺牲部分企业代理兼容性，换取
  更小的中间人信任面；若必须使用企业 CA，应明确评估后再做专门构建。
- 不允许任意 CA 绕过、跳过证书校验或“兼容模式”静默降级。
- 订阅默认只接受 HTTPS 和用户主动选择的本地文件；每次请求与重定向前检查 DNS 解析结果，
  拒绝回环、私网、链路本地、CGNAT 和组播目标，降低 SSRF 与常见 DNS rebinding 风险。
- 自定义 DNS 只接受 HTTPS DoH 或 TLS DoT；拒绝明文 `udp://`/`tcp://`、凭据、查询参数和片段，
  自定义端点不会写入日志或诊断包。
- Mihomo 运行配置默认使用 `log-level: error`。警告级别可能把失败连接的域名或 SNI 写入 logcat，
  因此 Weave 只保留核心错误；应用侧恢复记录也只保存 allowlist 错误类别。
- TUN 规则对所有配置拒绝应用自发的 TCP/UDP 53、TCP/UDP 853、已知公共 DoH 域名和公共 DNS
  IPv4/IPv6。Mihomo 自己的 DoH/DoT socket 在离开进程前由 `VpnService.protect()` 保护，不经过这些
  应用规则；用户若在浏览器中配置了未枚举的自定义 DoH，仍须关闭浏览器 Secure DNS 才能做到单一解析器。
- 所有 Activity/Service 默认不导出；`VpnService` 受 `BIND_VPN_SERVICE` 保护。
- 配置先解析、验证、原子提交，再建立或 reload；失败时保留旧配置。
- 核心未就绪时不建立 TUN，避免全设备断网。
- 局域网互传默认关闭，只在用户点击生成后监听，成功读取一次或 5 分钟即失效；监听只绑定当前
  选中的私有 IPv4，不绑定 wildcard；接收端严格校验请求行、Content-Type、Content-Length 与 AEAD；
  不开放远程控制或内核调试 API；包含一次性密钥的剪贴板内容标记为敏感并在 60 秒后自动清除。
- 订阅导入、订阅编辑和局域网互传界面动态启用 Android `FLAG_SECURE`，避免 URL、节点凭据
  或一次性密钥进入截图和最近任务预览；普通主页面仍允许截图。
- 代理模式不把 `DIRECT` 追加为故障兜底；默认订阅失效或删除时不会静默暴露物理 IP。
- Weave 不把 Android Always-on 或“阻止无 VPN 连接”伪装成应用内状态；设置页只打开系统页面并明确
  提示用户手动开启。只有系统开关开启后，应用进程崩溃或被强制停止时才有系统级 kill switch 保障。
- 连接中删除最后一个代理时立即关闭 VPN/内核，不让事务回滚继续使用已经删除的内存态凭据。
- Mihomo 出站 socket 在 `VpnService.protect()` 后绑定到当前已验证的非 VPN 网络；全部底层网络
  丢失时把 VPN 上游显式设为空，恢复后通过事务重载重建连接。
- 无第三方广告、统计或崩溃 SDK。遥测如未来加入必须显式 opt-in。
- IP 质量检测仅在用户主动点击时运行，固定使用 HTTPS、4 秒超时和 128 KiB 响应上限；结果只在内存中展示。
  它不会把 DNS 泄漏、WebRTC 候选地址或网站信誉伪装成已测试结论。

## 密钥与存储

- 用 Android Keystore 生成不可导出的 AES-256-GCM 主密钥。
- 订阅 URL、认证信息、备份密钥、节点元数据和自定义 DNS 端点单独加密；节点展示模型不含凭据。
- 订阅正文以 Keystore AES-GCM 加密保存在 `noBackupFilesDir`；仅在服务运行时解密到
  Mihomo home 的 app-private provider 文件，断开时删除运行时副本。
- 生成的 `config.yaml` 也只写入 app-private cache，连接失败或断开时连同目录删除。
- 用户确认永久删除订阅时，加密 payload 先原子移动为不可达 tombstone，再提交元数据删除；
  提交失败会恢复，成功后清理 tombstone、加密 URL 和节点索引。
- 数据库字段按敏感度拆分，避免一次普通查询返回全部密钥材料。
- 备份默认排除敏感文件；加密导出使用用户口令派生的独立密钥。
- 屏幕锁被移除或 Keystore key 永久失效时，要求重新导入，不实现不安全恢复后门。
- iOS 与 macOS 订阅库使用 Keychain 中的 256-bit 主密钥和 AES-GCM 加密文件；iOS 运行时
  provider/config 采用完整文件保护，仅在 App Group 短暂存在并在断开或启动失败时清理；局域网互传的随机
  密钥不写入订阅库。

## 局域网互传

- 链接只接受 RFC1918、IPv4 link-local 或 loopback 地址，并避开 TUN/utun 点对点接口。
- HTTP body 是带固定 AAD 的 AES-256-GCM 密文；订阅 URL、节点与凭据不以明文上网。
- 32-byte 密钥只在 `weave://` fragment 中，HTTP 请求不会携带 fragment。
- 128-bit token、密钥和 nonce 均来自系统安全随机源；接收端执行大小、数量、UTF-8、
  协议 magic 和 GCM 认证检查后才写入。
- 当前协议面向同一可信局域网内的用户主动迁移，不提供互联网中继、设备发现或后台监听。

## 订阅和规则集输入

- 限制响应大小、节点数、嵌套深度、正则长度和解压倍率。
- 禁止订阅配置声明任意本地文件、任意脚本、外部 UI 或管理端口；Clash 导入只接受节点 provider
  段，控制器、监听端口、TUN、脚本、规则集、DNS 和 proxy-groups 等控制面字段会在进入 Mihomo
  前丢弃，YAML merge anchor 也只保留被节点实际引用的定义。
- URL scheme 和重定向逐跳校验；凭据不跨 host 转发。
- 远程规则集记录最终 URL、ETag、哈希与更新时间，更新异常可回滚。
- 高风险能力（脚本、外部控制器、局域网监听）不从订阅继承，只能由用户本地开启。

## 日志与诊断

日志事件使用结构化 allowlist，不在写入后再靠正则“尽量脱敏”；恢复中心只保存错误类别，DNS
探测也只显示有限的错误类型，不显示主机、SNI、URL 或异常原文。

禁止进入日志：

- 完整订阅 URL、Authorization/Cookie；
- 节点服务器地址、UUID、密码、证书私钥；
- 完整 DNS 名称、SNI、访问 URL；
- 已安装应用完整清单。

诊断包只包含版本、ABI、Android 版本、内核状态码、规则数量、匿名化性能指标和用户明确选择的
最近错误。导出前显示清单并二次确认。

原生内核日志仍可能由系统组件写入 logcat；发布构建应把 logcat 视为敏感诊断面，不在 issue、截图或
公开日志中粘贴原始输出。Weave 默认将 Mihomo 日志降到 error，但无法阻止 root/调试权限读取系统日志。

## 内核供应链

发布所用原生库必须：

1. 固定上游仓库、commit、Go toolchain 与依赖校验和；
2. 由仓库 CI 脚本从源码构建，不接受聊天群或网盘二进制；
3. 生成 SHA-256、SBOM、依赖许可证和构建日志；
4. 至少由两名维护者复核版本升级差异；
5. 对已知漏洞和协议回归运行自动测试；
6. 同发行版发布对应源码、补丁与构建说明。

0.3 已完成固定 commit、Go/NDK/CMake、四 ABI SHA-256 和离线构建脚本；完整依赖 SBOM、
双人复核与生产签名仍是发布阻断项。

## 连接正确性门槛

发布前必须在 IPv4、IPv6、双栈、Wi‑Fi/蜂窝切换、Doze、锁屏和系统 always-on 下验证：

- TUN 有活跃消费者后 UI 才显示“已连接”；
- DNS 查询不绕过所选策略；
- 被保护应用的 IPv6 不因缺少路由而直连；
- 内核崩溃时 kill switch 行为与用户设置一致；
- 应用规则确实命中预期订阅/节点；
- 断开后文件描述符、前台通知、端口和 goroutine 全部释放。

0.3 已在 Android 16 ARM64 模拟器确认断开后前台服务、VPN network、TUN 接口、生成配置和
provider 明文文件均消失；实机、压力和网络切换矩阵仍是发布阻断项。

## 漏洞报告

正式公开仓库时添加私密安全报告渠道和 `SECURITY.md` 联系地址。在地址确定前，请不要公开提交
包含真实订阅、节点或可利用细节的 issue。
