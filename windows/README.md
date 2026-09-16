# Weave Windows 0.1 Preview

独立 Windows 10/11 x64 测试版。沿用 Android 的八套外观、订阅→节点选择和图形化应用分流，
使用适合电脑的侧栏与双列卡片。不是将 Android APK 放进模拟器。

## 下载安装

在独立分支 `codex/windows-v1-preview` 的 **Windows preview** Actions 中下载
`Weave-Windows-v1-Setup-x64`，解压后运行其中的 Setup.exe。
也提供 `Weave-Windows-x64-test` 完整便携目录，必须全部解压，不能只复制主程序。
未签名测试包可能触发信誉提示；请核对来源和 SHA-256，**不要关闭系统安全保护**。

首次连接会申请管理员权限并重开应用；使用同一 Windows 账户，重新点击连接即可。
配置保存在当前账户的 LocalAppData/Weave，不随卸载删除。订阅、应用规则及网络偏好由 DPAPI 加密保存。
运行期间 Mihomo 需要本机配置文件；正常断开会清理会话目录，异常终止可能留下文件。
这不是针对本机管理员或已入侵设备的防护。

目标系统：Windows 10 22H2 / Windows 11（Intel/AMD x64）；最低 API 为 build 19041。
ARM64 尚未交付。本分支不会自动发布 Release 或修改 Android main。

## 当前功能

| 内容 | Windows 0.1 |
| --- | --- |
| 八种主题 | 浅色、白绿、深色、素纸、印象日出、睡莲、罂粟花田、暮色花园 |
| 订阅 | HTTPS、YAML、JSON、Base64；编辑、更新、删除、完整节点列表 |
| 外部节点提供器 | HTTPS / inline；有循环、超限或不支持的覆盖项时拒绝整份导入，不静默丢节点 |
| 选择节点 | 先订阅后节点；自动低延迟或手动；主动批量测速、排序和取消 |
| 应用分流 | Windows .exe 名称 → 指定订阅/节点、直连或阻止；重新连接生效 |
| 网络 | Mihomo TUN、IPv4/IPv6 开关；规则/全局/直连；自订加密 DNS、广告/家庭过滤 |
| 检测入口 | 经本会话代理检测出口 IP、Google、YouTube、GPT、Claude、X、TikTok、Netflix、Facebook、Disney+ |
| 迁移与分享 | 手动选择其他客户端导出的 Clash 文件；选择性导出 YAML ZIP |
| 运行稳定性 | 串行连接、先验证配置、鉴权控制接口、核对全部 provider 节点数、TUN 就绪检查、进程退出状态、Job Object 生命周期管理 |

默认不自动测试网站、不轮询流量、不持续后台测速或实时背景模糊。
自动组的必要健康检查由 Mihomo 管理；关闭重复 provider 周期检查，保留惰性自动组检测。
页面切换无过渡动画，节点列表虚拟化，大配置解析和写盘不阻塞界面。

## 尚未与 Android 完全对齐

- 实时摄像头扫码、二维码图片识别、自动发现其他客户端、Android 兼容的局域网二维码互传；
- 多语言完整覆盖、国内域名/IP 地理直连规则库、完整浏览器 WebRTC/DNS 泄漏/指纹实验；
- 实时流量图、完整连接日志、系统托盘与开机启动、自动睡眠恢复、真正的系统级 Kill Switch；
- Windows 10/11 真机的 IPv6、DNS、睡眠/网络切换、长时间连接及异常退出回归。

规则模式当前是**应用进程分流**，未指定进程的流量走所选出口，不宣称已有完整中国大陆自动直连策略。
网站返回 403/429 表示服务器有响应但限制访问，不算“解锁成功”。
出口检测只测代理请求的 IP，不证明浏览器、所有应用或 IPv6 无泄漏。
DNS 的首次引导解析使用明文 DNS；常见 STUN 端口拦截不等于完整 WebRTC 防泄漏。
TUN 未就绪时不显示“已连接”，但这不是系统断网保护：代理退出后系统可能恢复直连。

## 内核与构建

绑定官方 **Mihomo v1.19.30 Windows amd64-v1**，不是 Android 的定制 JNI 核心。
上游源码：https://github.com/MetaCubeX/mihomo/tree/v1.19.30
下载包 SHA-256：`8b81fe2c5cd04ca6deb61eec6075150b44cc5ad13ab867750642e2422f7c1278`。
CI 固定版本并校验哈希；应用不自动下载或更新内核。

Windows 需要 Visual Studio 2022、.NET 8 SDK 与 Windows App SDK 构建支持：

```powershell
.\windows\build.ps1 -Platform x64 -Configuration Release
```

CI 执行核心测试、原生内核配置与会话测试（不启用 TUN）、WinUI 启动检查和多主题截图，
再用 Inno Setup 生成安装包。这些检查不能替代 Windows 真机代理联通性验收。
手动构建需先将同版本官方内核放入 `windows/src/Weave.Windows/runtime/mihomo.exe`。
