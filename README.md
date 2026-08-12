# Weave（暂定名）

一款面向 Android 与 macOS 的开源规则代理客户端：以 CMFA 的兼容性和低学习成本为基线，
吸收 Karing 的多订阅、复杂规则与按应用分流能力，同时把可审计、安全默认值和内核可替换性
放在架构中心。

> 当前里程碑是 **Android 0.3.0-alpha25 / macOS 0.1.0-alpha05**。Android 四个 ABI 的
> CMFA/Mihomo
> 核心已从锁定源码本地复现并接入；Clash YAML 可从 HTTPS、系统文件选择器、相机二维码或
> 二维码图片安全导入并编译为隔离 provider，
> Android `VpnService` 会把 TUN fd 交给核心，并通过 `protect(fd)` 与 UID/包名回调完成出站
> 和按应用路由。Android 16 ARM64 模拟器与 Pixel 8 / Android 17 真机均已用真实 OpenVPN
> 订阅完成双栈 TUN、固定节点、Chrome 应用归属、系统网络验证、Wi‑Fi/蜂窝切换和断开清理
> 闭环。macOS 版是原生 SwiftUI Apple Silicon 客户端，已集成相同锁定 commit 的 arm64
> Mihomo、本地代理、订阅加密库和跨端局域网互传；完整系统 VPN 仍受 Apple Network
> Extension 签名权限阻断。两端都仍是开发预览版，不应作为生产 VPN 使用。

Weave 本身不提供、销售或推荐代理节点。导入的订阅、节点和第三方 DNS 由用户自行选择，
其运营者可能看到连接所必需的元数据。公开预览版的实际数据处理见
[`PRIVACY.md`](PRIVACY.md)，安全问题请使用 [`SECURITY.md`](SECURITY.md) 中的私密渠道。

## 核心体验

- 首页只回答三个问题：是否受保护、当前出口是什么、现在是否健康。
- 首页可手动选择默认订阅策略、固定节点或直连；连接中修改会安全热重载。
- 出口选择采用“先选订阅、再选节点”的两步条件流程；节点只显示核心名称，不拼接订阅名、
  协议或空延迟说明，并清理开头的旗帜 Emoji 与 `\u...` 转义装饰。
- 已导入订阅可查看和搜索全部节点、修改名称或 HTTPS 地址，也可用本地文件原位替换；
  被替换掉的固定节点引用会安全降级到该订阅的自动策略。
- 订阅原位更新采用版本化加密 payload：新内容和元数据提交成功后才删除旧版本，并显示
  新增、移除、保留节点差异；同名同协议但参数未知的节点只提示，不做可能误删的去重。
- 已导入订阅可永久删除；确认页会列出受影响的应用规则和默认出口回退行为，并同步清理
  Keystore 加密的地址、节点元数据与加密 payload。
- 应用分流是一等能力：`应用 → 自动策略 / 固定节点 / 直连 / 阻止`。
- Android 应用规则同时编译 UID 与包名匹配；所选节点不能承载 UDP 时会拒绝该应用的
  UDP/QUIC，并促使 Chrome 等客户端回退到同一出口的 TCP，禁止静默泄漏到默认节点。
- Android UID 归属会在内存中按本地 socket 短期缓存 10 秒且最多保留 2048 项，覆盖
  QUIC 被拒绝后系统瞬间返回 `-1` 的重试窗口；服务停止或规则重载时立即清空。
- 应用规则可在出口编辑弹窗中删除；删除前二次确认，删除后回落到默认出口。
- 每个应用可以引用不同订阅中的不同节点，而不要求切换整套配置。
- 二维码相机扫描在有 Google Play 服务的设备上无需申请相机权限；相册二维码由随包 ML Kit
  在本机识别，并限制为 20 MiB。
- Clash YAML 多订阅会生成彼此隔离的 Mihomo file provider；URI/sing-box 转换仍 fail closed。
- 运行时只加载默认出口和应用规则实际引用的 provider；健康检查按需懒执行，避免大订阅在
  VPN 启动时并发探测全部节点、挤占真实连接。
- 自动订阅策略可在最低延迟（`url-test`）、按顺序故障切换（`fallback`）与一致性哈希
  负载均衡之间切换；修改后持久化并安全热重载。
- DNS 可选择 DoH 或 DoT，并提供普通隐私、广告过滤、家庭过滤和自定义加密端点；均保留 fake-IP 全隧道接管；IPv4-only 模式会停用 IPv6 DNS，
  同时在已经接管 `::/0` 的隧道内拒绝 IPv6，避免旁路物理网络。
- 可选“国内智能直连”把随 APK 固定并校验哈希的 lite GeoSite/GeoIP CN 规则放在应用规则
  之后、默认出口之前；应用级指定出口始终优先，运行时不静默下载规则数据。
- 可选 UDP STUN 阻断覆盖常见的 3478–3479 与 19302–19309 端口范围，用于降低 WebRTC
  地址暴露风险；界面明确提示它可能影响音视频通话。
- 运行配置采用候选、活动、回滚三个短生命周期快照：候选先由原生内核只解析不应用，
  校验失败时旧 TUN 不停；启动失败时自动恢复上一份配置与 provider。
- 订阅详情可读取 CMFA 真实运行组的节点延迟并手动触发 provider 健康检查；未加载的订阅
  会明确提示先设为出口，不生成假延迟。
- 代理服务器域名强制交给所选 DoH/DoT 上游解析；明文 `default-nameserver` 只承担加密
  DNS 上游域名的引导解析，不再用于每个代理服务器域名。
- 兼容使用 YAML merge anchor 的 OpenVPN Clash 配置，文件导入限制为 5 MiB 且要求严格 UTF-8。
- 兼容 Clash 节点的多行 YAML、单行 `{...}` flow map 与整行 `proxies: [...]` 表达；
  远程请求使用 CMFA/Clash 兼容标识，避免自适应面板误返回通用格式。
- 用户看到的是“最终命中出口”，不是一堆互相覆盖、难以调试的规则开关。
- 首页以不记录域名、IP 或连接明细的计数器展示应用 UID 识别是否正常。
- 订阅凭据、节点地址和访问域名默认不进入日志或诊断包。
- 只枚举带启动器入口的应用，不申请 `QUERY_ALL_PACKAGES`。
- 用户新增的应用规则与跨订阅出口会在本机持久化。
- 首次建立 VPN 前显示独立的数据路径说明并要求主动确认；设置页可随时重新查看，不用营销
  文案代替系统权限与第三方节点的真实责任边界。
- Android 与 macOS 可一键生成 5 分钟、仅可使用一次的局域网二维码/链接；HTTP 只承载
  AES-256-GCM 密文，密钥只在 `weave://` 链接 fragment 中。
- Android 监听已验证的非 VPN 底层网络；Wi‑Fi/蜂窝切换稳定 1.5 秒后自动事务重启核心，
  无网络时保持明确状态并等待恢复。首页运行指标只在首页可见时轮询，减少后台页面重组。

## macOS Apple Silicon

macOS 14+ 可运行 [Weave.app](macos/build/Weave.app)，或在 `macos/` 执行
`./build-app.sh` 重新构建。连接页先选订阅，再选自动策略或该订阅的具体节点；当前启动绑定
`127.0.0.1:7890` 的本地代理，不自动修改系统代理。完整设备 VPN 和按应用分流需要 Apple
Developer 为 Packet Tunnel extension 授予 Network Extension entitlement，未获权限时
界面不会显示虚假的 VPN 成功状态。

## 运行

项目使用 Android Studio、JDK 17、AGP 9.2、Gradle 9.4.1、`compileSdk 36`、
NDK `29.0.14206865` 和 CMake `3.31.6`。
AndroidX Core 1.18 / Lifecycle 2.10 暂时固定在仍兼容 API 36 的稳定版本；升级到它们的下一
稳定版需要先把编译 SDK 提升到 API 37。

1. 用 Android Studio 打开仓库。
2. 安装 Android SDK 36、NDK 29.0.14206865 和 CMake 3.31.6。
3. 使用仓库内经过 SHA-256 固定的 wrapper 构建：

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

4. 运行 `app` 的 debug variant。

请让 Android Studio 或 `JAVA_HOME` 使用 JDK 17。Wrapper JAR 和发行包校验和已随仓库提供。

公开贡献前请阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。发行者还必须逐项完成
[`docs/OPEN_SOURCE_RELEASE_CHECKLIST.md`](docs/OPEN_SOURCE_RELEASE_CHECKLIST.md)，Debug APK
不属于受支持的生产发行物。

## 目录

```text
app/
  src/main/java/io/weave/client/
    core/bridge/       最小 JNI 接口与回调
    core/engine/       Mihomo 生命周期、配置组装与规则编译
    core/vpn/          Android VpnService 生命周期
    data/              路由与运行模式持久化
    domain/            与内核无关的订阅、节点、应用路由模型
    security/          Android Keystore AES-GCM 密钥信封
    subscription/      HTTPS 获取、输入限制、格式识别与加密存储
    ui/                Compose 页面与设计系统
docs/
  PRODUCT_SPEC.md      产品边界与里程碑
  ARCHITECTURE.md      模块、数据流和双内核策略
  SECURITY.md          威胁模型和安全发布门槛
  ANDROID_FEATURE_MATRIX.md 截图需求逐项对应的真实实现状态与后续边界
  UI_ENTRYPOINT_AUDIT.md 已实现入口、移除的假控件与兼容性边界
  ENGINE_INTEGRATION.md Mihomo 接入清单
  CORE_PROVENANCE.md   固定的 CMFA/Mihomo 构建来源
  RUNTIME_VALIDATION.md Android 16 模拟器与 Android 17 真机运行验证记录
macos/                  SwiftUI 客户端、arm64 Mihomo、构建与互传实现
```

## 技术判断与上游

- [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid)：
  Android 兼容经验、Mihomo Android 分支与服务组织方式。上游为 GPL-3.0。
- [Karing](https://github.com/KaringX/karing)：
  多订阅、规则组、同步和新手模式的产品能力。上游采用 GPL-3.0，并限制衍生作品使用其名称
  或暗示关联。
- [sing-box](https://github.com/SagerNet/sing-box)：
  作为第二内核候选；其路由规则原生支持 Android `package_name`。
- [Android VPN 指南](https://developer.android.com/develop/connectivity/vpn)：
  `VpnService`、always-on 和 per-app VPN 的平台行为。

Weave 不使用 CMFA 或 Karing 的名称、图标、品牌素材，也不暗示与它们存在官方关联。

## 许可证

项目以 GPL-3.0-or-later 发布，完整正文见 [LICENSE](LICENSE)。原生输入、工具链和哈希见
[`core-lock.properties`](core-lock.properties)。生产发布前仍须生成完整 Go 依赖 SBOM、
NOTICE/版权清单，并随发行版提供对应源码。
