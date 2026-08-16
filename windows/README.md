# Weave for Windows（私用预览）

这里是 Windows 10/11 的独立桌面版工程，目标是先提供 x64 私用版本，再补 ARM64。它不复用
Android 的 `VpnService`，而是让 Mihomo 使用 Windows TUN/Wintun 接管流量；订阅、节点、DNS
和进程分流模型保持与 Android 的产品语义一致。

## 当前状态

已完成第一版工程骨架：

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

需要 Visual Studio 2022（Desktop development with .NET、Windows App SDK）和 .NET 8 SDK：

```powershell
.\build.ps1 -Platform x64 -Configuration Release
```

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
