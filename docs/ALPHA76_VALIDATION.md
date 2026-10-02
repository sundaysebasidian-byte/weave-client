# alpha76：Clash 订阅导入兼容修复

## 已修正的代码差异

- 请求标识改为 `ClashMetaForAndroid/2.11.32.Meta`，版本与 `core-lock.properties` 一致。此前遗漏 `.Meta`。主文件、集合请求和重定向均使用此标识；测试验证实际设置的 HTTP 请求头，而非只检查常量。
- CMFA 的 [构建文件](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/build.gradle.kts)为 Meta 版本添加 `.Meta`；[Walless 公告](https://t.me/s/WallessPKUChannel?before=114)明确旧客户端不会获得新协议服务器。二者说明这是需要修正的能力协商差异，但尚无私人服务端响应对比，不能证明它是用户 65→23 的唯一原因。
- 订阅下载跟随当前系统/VPN 路由，删除强制选择物理网络的路径。不静默绕开正在使用的 VPN；隧道本身不可用时仍可能需要用户先修复网络。
- 保留原始订阅源用于以后刷新；只用重定向最终 URL 解析相对 provider 地址，避免把临时下载地址持久化为订阅源。
- Clash 检测从行首限定改为安全读取根映射，兼容 JSON/flow YAML 与块式 YAML。嵌套的同名键不是根配置，不能冒充 `proxies`/`proxy-providers`。
- 主配置和 provider 文件均支持 Base64 包装；HTTP 响应支持 identity/gzip/deflate，解压后仍执行大小上限与严格 UTF-8 校验。
- URL/文件/二维码/LAN 共用准备流程，规范化前后检查数量；Clash 还核对节点名称与协议列表。失败不写入。原有节点骤减阻止覆盖仍有效。
- 订阅详情增加安全计数：主文件节点、集合节点（及集合数）、最终导入节点。仅新导入/更新时记录，无凭据/节点名/服务器地址。集合节点按声明计数，完全重复对象去重后最终数量可能较少；不是“连接成功数量”。删除订阅同时删除计数。

## 范围限制

- 未放宽 HTTPS、地址校验、循环引用、响应大小、YAML 安全限制，也没有复制原订阅控制器/脚本/系统设置。
- 自定义 provider 请求头、过滤器、文件依赖和复杂 override 等原有未支持功能仍会明确拒绝，不宣称所有 Clash 扩展均已兼容。
- 没有连接用户设备、读取私人订阅或调用 Walless 私人地址。真实 65 个节点恢复与连通性尚需用户在手机更新后确认；不会把合成测试当作真实修复证据。

## 验证

- 合成主文件 23 个＋Base64 provider 42 个，经过实际 Repository 使用的准备流程保留 65 个；涵盖 compact JSON、Base64 主文件、无顶层 proxies 的纯 provider 主文件、同端点不同名、嵌套伪键。
- 实际抓取器的离线连接替身验证 Meta 请求头、主/子请求、重定向及明文/本地地址重定向拒绝。
- 解压上限、无效 UTF-8、原有订阅回归与语言覆盖测试。
- 本地锁定的 macOS Mihomo 对 `app/src/test/resources/clash-import-65-smoke.json` 执行配置校验成功，65 个 HTTP/Hysteria2 inline 节点可被接受。仅校验配置，不证明 Android 实机连接或真实服务器可用。

## 最终结果

- 72 项定向 JVM 测试通过，0 失败；本地发布审计、vital lint、优化构建成功。
- 成品：`dist/Weave-0.3.0-alpha76-arm64.apk`，versionCode 81，19,179,091 字节。
- SHA-256：`7aa3b2e32342601ce903c84e14c6e4191b94b9c15c90576314783b883a645869`。
- APK 签名校验成功，与原本地安装证书一致（`54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`）。仍为现有本地 debug 证书签名的非 debuggable 优化包，不是新的公开发布签名。
- 未安装到手机、未提交或推送。安装后需手动更新一次远程订阅；不会自动恢复之前丢失的旧缓存。
