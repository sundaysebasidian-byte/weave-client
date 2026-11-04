# Weave for macOS

原生 SwiftUI 客户端，当前支持 macOS 14+、Apple Silicon arm64。仍处于产品加固阶段；验收状态见 [产品验收账本](../docs/PRODUCT_READINESS.md)。

## 当前能力

- Keychain + AES-256-GCM 订阅库；密钥不可用或库损坏时进入只读状态，保留原文件。
- HTTPS、本地文件、二维码、URI/Base64 与 Clash YAML/JSON 使用一致的导入流程。Yams 6.2.2 固定版本负责结构化解析；先限制文档数量、嵌套深度、节点数量和锚点展开规模。重复键、重名节点和暂不支持的 URI 参数明确报错。
- 持久化订阅/固定节点选择；节点消失时要求重新选择；刷新先预览增删变化，保存后下次连接生效。
- 只把选中的节点 provider 交给核心；移除订阅中的监听器、控制器、脚本和远程规则配置。会话使用随机回环端口和认证密钥，目录 0700、配置 0600，带进程租约的清理保留未知文件。
- 本地代理模式接管 HTTP/HTTPS/SOCKS 系统代理。恢复时逐项检查归属，保留用户后来做的修改；失败时保留可重试日志。
- Weave LAN Transfer v1 一次性加密互传，发送端确认短码和 AES-GCM 校验。

## 构建与验证

在 Apple Silicon Mac 上使用 Swift 6 / Xcode 16+：

```bash
# Test fixtures need python3 on PATH with OpenSSL >= 1.1.1 (CI uses Python 3.13).
bash macos/test.sh --jobs 4
bash macos/build-app.sh
```

打包脚本核对 `core-lock.properties` 的内核 SHA-256，缺失或不匹配即失败。输出位于新的 `macos/build/package.XXXXXX/` 目录，包含 `Weave.app`、ZIP、ZIP SHA-256 和 `manifest.json`。不会覆盖已有包。App 内含源码 commit、dirty 标记及第三方许可证。

默认使用 ad-hoc 签名，只是本地测试预览，尚未经过 Developer ID 签名及 Apple 公证。正式发行模式须从干净提交构建，显式设置 `WEAVE_MAC_RELEASE=1`、`WEAVE_MAC_SIGN_IDENTITY` 和已有 Keychain 公证配置 `WEAVE_MAC_NOTARY_PROFILE`；公证成功并装订票据后才生成最终 ZIP。脚本不会创建证书、账号或公证凭据。

## TUN 与连接状态的范围

TUN 由内置 Mihomo 创建随机 `utun` 设备，需要系统权限；普通 GUI 当前尚无经过验收的提权辅助服务。默认请求 TUN，启动失败时不会自动降级。本地代理模式由用户关闭 TUN 开关后使用，仅覆盖遵循系统代理的应用。

连接检查确认属于本会话的核心进程、认证控制 API、监听端口；TUN 模式还核对 IPv4/IPv6 路由。它们不等同于外网节点可达、DNS 无泄漏或断线锁网。未连接或核心退出时，其他应用仍可能使用原网络。真实 DNS/出口探测、权限辅助服务、休眠与网络切换验收仍在开发中。

HTTPS 导入使用系统 libcurl，在创建 socket 前校验随后实际连接的地址；拒绝本机、内网和保留地址，每次重定向均重新校验。证书与主机名校验保持开启，响应解压后限 5 MiB，响应头限 64 KiB。已连接时，通过本会话核心的认证 DNS API 获取真实公网地址并固定到该次 HTTPS 请求，避免把 TUN 的 fake-IP 当作公网目的地。隔离的真实 TLS/DNS 测试覆盖这些边界；它们不替代真实 TUN 和外网防泄漏验收。

## 内核与供应链

`Resources/mihomo` 固定到 `core-lock.properties` 指定的源码和工具链，当前仅打包 darwin/arm64。Swift 依赖由 `Package.resolved` 固定到提交，Yams 与其 LibYAML 许可证位于 `Resources/Licenses/`。核心来源及重建说明见 [CORE_PROVENANCE](../docs/CORE_PROVENANCE.md)。
