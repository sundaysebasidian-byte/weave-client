# Weave for Windows（私用预览）

这里是 Windows 10/11 的独立桌面版工程，目标是先提供 x64 私用版本，再补 ARM64。它不复用
Android 的 `VpnService`，而是让 Mihomo 使用 Windows TUN/Wintun 接管流量；订阅、节点、DNS
和进程分流模型保持与 Android 的产品语义一致。

## 当前状态

当前为桌面预览工程，界面已按 Android 的设计语言调整，但尚未在 Windows 真机验收：

- 浅色默认、白绿与深色，沿用 Android 的配色，26 px 圆角、渐变玻璃卡片和细边缘；
- 连接、订阅、分流、设置分开显示，不做页面过渡动画或持续背景模糊；
- 订阅节点查看、恢复自动节点、DNS 过滤与 IPv6 开关接入实际配置；
- 操作互斥、连接生命周期串行化、核心退出状态通知及启动失败释放；

- `Weave.Windows.Core`：Clash/Mihomo YAML 与 Base64 导入、节点提取、5 MiB 限制、HTTPS/私网地址检查；
- DPAPI 加密订阅保险库接口；
- 订阅 provider 文件、自动测速组、固定节点组和 `PROCESS-NAME` 分流规则编译；
- 应用分流编辑器：按 `.exe` 进程选择自动订阅、固定节点、直连或阻止，并用 DPAPI 保存；
- Windows Mihomo 配置校验、启动、就绪探测、日志截断和崩溃清理；
- WinUI 3 桌面壳：订阅导入、订阅删除、节点查看、先订阅后节点、连接/断开状态。

当前仍有两个发布前工作：将经过锁定哈希校验的 `mihomo.exe` 放入发行包，以及在 Windows 10/11
真机上验证管理员权限、Wintun 安装、DNS 劫持和断开回滚。macOS 主机无法代替这一步，所以这里
不会把未在 Windows 真机验证的 TUN 连接称为已完成。

## Windows 构建

目标验收平台是 Windows 10 22H2 与 Windows 11（x64 优先）；工程最低 API 基线为 Windows 10 2004 / build 19041。ARM64 构建入口保留，尚未验收。

需要 Visual Studio 2022（Desktop development with .NET、Windows App SDK）和 .NET 8 SDK：

```powershell
.\build.ps1 -Platform x64 -Configuration Release
```

也提供 `.github/workflows/windows-preview.yml`，仅手动触发，不自动发布。推送后可在 Actions 中选择
Windows preview → Run workflow，下载 `Weave-Windows-x64-preview-without-core`。
该产物没有捆绑核心时不能连接；需要按下文补齐审核过的对应架构核心，不应当作完整发行包。

本轮在 macOS 上只完成了 XML 格式与差异检查，未运行 WinUI 编译、核心单元测试或 Windows 真机 TUN 测试。
发布前需要在 Windows 执行上述脚本，并验证：普通权限提示、管理员启动、固定节点与自动选择、进程分流、
IPv4/IPv6 出口、DNS、睡眠唤醒、网络切换和退出恢复。界面相似不意味着 Android 功能已全部迁移。

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
