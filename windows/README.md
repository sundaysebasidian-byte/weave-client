# Weave for Windows（私用预览）

这里是 Windows 10/11 的独立桌面版工程，目标是先提供 x64 私用版本，再补 ARM64。它不复用
Android 的 `VpnService`，而是让 Mihomo 使用 Windows TUN/Wintun 接管流量；订阅、节点、DNS
和进程分流模型保持与 Android 的产品语义一致。

## 当前状态

已完成第一版工程骨架，并补齐一部分与 Android 对齐的管理能力：

- `Weave.Windows.Core`：Clash/Mihomo YAML、常见节点 URI、sing-box 与基础 V2Ray JSON 及其 Base64 包装导入；节点提取、5 MiB 限制、HTTPS/私网地址检查；连接时重新校验 DNS 结果，避免解析检查与实际连接之间的地址变化；
- DPAPI 加密订阅保险库接口；
- HTTPS 订阅手动刷新，保留原订阅和固定节点引用；节点数量骤降或已配置的固定节点消失时保留旧版本，并显示刷新差异；
- 订阅 provider 文件、自动测速组、固定节点组和 `PROCESS-NAME` 分流规则编译；
- 应用分流编辑器：按 `.exe` 进程选择自动订阅、固定节点、直连或阻止，并用 DPAPI 保存；
- Windows Mihomo 配置校验、启动、带随机密钥的本机控制接口就绪探测、日志截断和运行文件清理；
- WinUI 3 桌面壳：订阅导入、刷新和删除、节点查看、先订阅后节点、连接/断开状态；DNS 预设/自定义 DoH 或 DoT、IPv6 和 STUN 阻断设置使用 DPAPI 保存。
- 连接页通过带密钥的本机控制接口展示实时上下行速率与累计流量；可对当前运行配置已加载的所选节点或整份订阅手动进行三轮健康检查，展示中位延迟、P95、抖动及失败轮次。整份订阅检查限制同时运行的节点数为 8，支持进度与取消，并按与 Android 相同的稳定度公式排序。

当前仍有两个发布前工作：将经过锁定哈希校验的 `mihomo.exe` 放入发行包，以及在 Windows 10/11
真机上验证管理员权限、Wintun 安装、DNS 劫持和断开回滚。控制接口就绪只证明核心已启动，
不证明 TUN 已接管流量，所以界面显示“核心运行中 · TUN 已请求”。

## 与 Android 的差距

| 能力 | Windows 当前状态 |
| --- | --- |
| Clash YAML/Base64、多订阅、固定节点、按进程分流 | 已接入；需真机流量验证 |
| 手动 HTTPS 刷新、差异和固定引用保护 | 已接入；没有 Android 的完整 Subscription Guard 审计项 |
| DNS 预设、自定义 DoH/DoT、IPv6、常见 STUN 端口阻断 | 已接入配置；需真机 DNS/IPv6 泄漏回归 |
| 节点 URI、sing-box/V2Ray JSON | 已接入常见协议的导入链路；无法安全转换的协议、传输或参数会在导入时提示 |
| 二维码、局域网互传 | 尚未接入 Windows 导入链路 |
| GeoIP/Geosite、域名与 IP 规则、离线策略包、路由解释 | 尚未接入 Windows 配置和界面 |
| 节点健康与质量矩阵 | 已接入当前运行配置的单节点及整份订阅三轮健康检查；按实测稳定度排序，未测得延迟时标记未完成；仍需 Windows 真机验证 |
| 实时速率 | 已接入控制界面；需 Windows 真机流量验证 |
| 网络与隐私检测 | 尚未接入 Windows 控制界面 |
| TUN 数据面、管理员权限与断开回滚 | 待 Windows 10/11 真机验证 |

完整 Android 基线见 [`docs/ANDROID_FEATURE_MATRIX.md`](../docs/ANDROID_FEATURE_MATRIX.md)。

粘贴、文件和 HTTPS 订阅共用同一解析链。URI 支持 SS/SSR、VMess、VLESS、Trojan、SOCKS5、HTTP、AnyTLS、Hysteria2 和 TUIC 的基础字段；JSON 支持 sing-box 与 V2Ray 的常见单服务器出站。复杂路由、插件、多用户或不支持的传输无法无损转换时，请改用 Clash/Mihomo YAML。WireGuard URI 仍需完整 YAML 配置。

## Windows 构建

需要 Visual Studio 2022（Desktop development with .NET、Windows App SDK）和 .NET 8 SDK：

```powershell
.\build.ps1 -Platform x64 -Configuration Release
```

核心库测试可运行 `dotnet test windows/src/Weave.Windows.Core/Weave.Windows.Core.Tests/Weave.Windows.Core.Tests.csproj`；
`.github/workflows/windows.yml` 会在 Windows runner 上执行测试和 WinUI x64 构建。

把同一 Mihomo 固定 commit 构建出的 `mihomo.exe` 放到：

```text
windows\src\Weave.Windows\runtime\mihomo.exe
```

也可以用 `WEAVE_MIHOMO_PATH` 指向核心。第一阶段默认不自动下载核心，避免把未审计的二进制
静默带进应用。

## 重要边界

- TUN 连接可能需要 Windows 防火墙/网络适配器权限；应用会在核心未就绪时保持“未连接”，不伪造成功。
- 进程分流使用 Mihomo 的 `PROCESS-NAME`；普通桌面 `.exe` 最可靠，UWP/系统服务的进程归属可能受系统限制。
- 远程订阅只接受 HTTPS，并拒绝解析到本机、私网、链路本地和 CGNAT 地址。
- 这是朋友私用预览，不是当前公开商店发行包；正式分发前还需要代码签名、核心 SBOM、安装器和 Windows 真机回归。
