# Weave Android / iOS / macOS 产品规格（alpha）

## 一句话

让普通用户一键得到可靠连接，让高级用户能明确控制“哪个应用，通过哪份订阅的哪个节点，
访问哪类目标”，并且能解释最终结果。

## 设计原则

1. **简单是默认路径，不是功能删减。** 首页只有连接、模式和出口；高级能力按需展开。
2. **路由结果可解释。** 任意连接都能追溯到应用规则、域名规则、规则集和默认规则中的一条。
3. **配置与内核解耦。** 产品数据不直接保存成某个内核的 YAML/JSON。
4. **安全失败。** 配置验证失败、规则引用失效、内核崩溃时，不建立或立即关闭 TUN。
5. **不做隐性联网。** 无广告、无推荐订阅、无默认遥测、无远程 WebView 控制面。
6. **本地发行边界。** 公开版不运营账号、云端控制、代理中继、节点市场或应用远程更新；用户主动选择的第三方端点必须在隐私声明和端点清单中可见。

## 用户模型

- **基础用户**：导入一个订阅，选择自动节点，一键连接。
- **分流用户**：为视频、工作、社交等应用指定订阅或节点。
- **高级用户**：维护自定义规则集、DNS、链式代理和网络条件。
- **维护者**：需要可复现构建、内核版本追踪、脱敏诊断和回归测试。

## 信息架构

### 连接

- 连接状态、内核状态和保护范围
- 当前出口、协议、订阅来源、延迟
- 规则 / 全局 / 直连模式
- 实时上下行与会话时长
- 显式错误和一键诊断

### 分流

- 应用规则列表，直接显示最终目标
- 目标类型：自动策略、固定节点、直连、阻止
- 目标可跨订阅引用
- 冲突模拟器：输入应用和域名，展示命中链
- 默认优先级：

  ```text
  临时会话覆盖
    → 应用 + 目标组合规则
    → 应用规则
    → 用户域名/IP规则
    → 远程规则集
    → 默认策略
  ```

### 订阅

- URL、剪贴板、二维码、本地文件导入
- Clash/Mihomo、sing-box、V2Ray URI 和常见 base64 订阅解析
- 各订阅独立更新、限额、User-Agent 和代理更新策略
- 节点去重但不丢失来源
- 失效节点不会破坏引用：规则显示“目标失效”并回落到显式备用策略

### 设置

- 自动连接、可信网络、按需启动
- DNS、防泄漏、IPv6、MTU、局域网共享
- 规则集、嗅探、日志级别和诊断包
- 备份采用端到端加密；第一阶段只做本地加密导入/导出

## Android 平台边界

Android 的 per-app VPN allow/disallow list 只决定应用是否进入 TUN，并不能为每个应用选择
不同远端出口。Weave 默认让受保护流量进入同一个 `VpnService`，再由内核使用 UID/包名匹配到不同
策略组。修改 Android allow/disallow list 需要重建 VPN，因此它只用于“绕过 VPN”高级选项。

Android 11+ 对已安装应用可见性有限。首发不申请高风险的 `QUERY_ALL_PACKAGES`：

- 默认列出带 launcher activity 的用户应用；
- 支持用户明确搜索/选择已知包名；
  - 如未来确有核心功能需要，再按 Play 政策论证并单独评审该权限。

## macOS 平台边界

- 首个 Apple Silicon 版本提供原生 SwiftUI、加密订阅库、订阅/节点条件选择、Mihomo 本地
  代理和局域网互传。
- 普通 macOS App 不能自行宣称全设备 VPN；完整 TUN 与按应用策略必须使用获得 Apple
  entitlement 的 Packet Tunnel Provider extension。
- 未签名开发预览只做 ad-hoc 签名，不具备可分发版本的 Developer ID notarization。

## iOS 平台边界

- iOS 17+ 使用独立 `NEPacketTunnelProvider`，运行配置只通过 App Group 中的版本化清单交接。
- 普通个人 VPN 无法把每个数据包归属到源 App；Per-App VPN 是受管设备的 MDM 能力，因此
  公开版只提供可真实编译的域名后缀分流，不显示假的应用选择器。
- Android `.so` 与 macOS 可执行文件不能在 iOS 加载；连接发行前必须从锁定源码生成、审计并
  嵌入面向 `NEPacketTunnelFlow` 的移动 framework，同时完成 Apple entitlement 和真机签名。

## 0.1 验收

- [x] 四个一级页面和一致的浅/深色设计系统
- [x] 应用到跨订阅目标的领域模型
- [x] 可替换内核接口和 fail-closed Mihomo 实现
- [x] 配置验证通过后才建立 TUN
- [x] 确定性应用规则编译器及单元测试
- [x] Android Studio debug 构建
- [x] HTTPS-only 订阅获取、大小/重定向限制与常见格式识别
- [x] Android Keystore AES-256-GCM 订阅地址存储
- [x] 无 `QUERY_ALL_PACKAGES` 的真实启动器应用选择
- [x] 跨订阅自动策略、固定节点、直连和阻止出口选择
- [x] iOS SwiftUI 控制面、加密订阅库、导入/互传与 Packet Tunnel fail-closed 边界
- [ ] iOS 移动内核 XCFramework、签名真机 TCP/UDP/DNS 数据面闭环
- [ ] 真机视觉与无障碍检查
- [x] 四 ABI Mihomo 源码构建、JNI、TUN 与 socket protect 接入
- [x] Clash YAML 多 provider、包名规则与固定节点配置生成
- [x] Android 16 ARM64 模拟器 TCP/UDP/DNS 与连接/断开闭环
- [ ] ARM64 真机 TCP/UDP/DNS 最小流量闭环

## 路线图

### 0.3 当前：原生数据面

- [x] 可复现地构建并固定 Mihomo Android 原生库
- [x] TUN、socket protect、UID 查询与前台通知
- [x] HTTPS 订阅导入、加密本地保存和固定节点元数据
- [x] Android 16 ARM64 模拟器双栈 TUN、DNS/TCP/UDP 与运行时文件清理
- [ ] 连接、DNS 泄漏、IPv4/IPv6 和休眠唤醒真机测试

### 0.4 强分流与格式转换

- 应用选择器与包名规则
- 跨订阅策略组、固定节点引用和自动回落
- 规则命中解释器与冲突检查
- 远程规则集签名/哈希固定与原子更新
- URI/Base64、sing-box JSON 与基础 V2Ray JSON 的安全转换器（复杂字段缺失时 fail closed）

### 0.5 可靠发布

- SBOM、可复现构建、签名与更新元数据
- 设备矩阵：Android 8–16、主流 ARM64 厂商系统
- 电量、内存、Doze、网络切换和内核压力测试
- F-Droid / GitHub Releases；Google Play 需完成 VpnService 声明和政策审核
