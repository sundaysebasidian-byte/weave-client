# 架构

## 总览

```text
Compose UI / SwiftUI
   │ intent / immutable state
   ▼
Application services
   ├── SubscriptionService
   ├── RoutePolicyService
   └── ConnectionCoordinator
           │ domain model
           ▼
      Config compiler
       ┌──────────┴──────────┐
       ▼                     ▼
MihomoEngineAdapter   SingBoxEngineAdapter
       │                     │
       └──────────┬──────────┘
                  ▼
 Android VpnService / iOS Packet Tunnel / macOS local proxy
                  │ TUN fd + protect(socket)
                  ▼
             Device network
```

Android 使用 `VpnService` 接管 TUN；iOS 使用独立 `NEPacketTunnelProvider` 与
`NEPacketTunnelFlow`，且只有嵌入经审计的移动 framework 后才启动数据面；macOS alpha
首版仅启动绑定 `127.0.0.1` 的 Mihomo
本地代理。macOS 全设备 TUN 必须落在独立签名的 `NEPacketTunnelProvider` extension 中，
不能由普通 App 进程或假状态替代。

三个平台共用 Weave LAN Transfer v1 的线协议：发送端临时监听局域网 TCP，只提供一个随机
token 对应的 AES-256-GCM 密文，接收端从二维码/链接 fragment 取得密钥。成功读取一次或
5 分钟后监听立即关闭；订阅落盘分别使用 Android Keystore，以及 iOS/macOS Keychain 主密钥。

公开发行配置不包含 Weave 后端、远程更新器、遥测或集中式控制 API。订阅刷新、加密 DNS、IP
质量检测和局域网互传属于用户主动选择的第三方/同局域网请求，固定端点和触发条件维护在
[`NETWORK_ENDPOINT_INVENTORY.md`](NETWORK_ENDPOINT_INVENTORY.md)。

## 为什么不直接 fork CMFA 或 Karing

直接 fork 可以更快得到第一条连接，但会把页面、数据结构、配置文件和内核生命周期继续绑在一起。
Weave 借鉴上游成熟做法，但用自己的领域模型隔离三种变化：

- UI 可以重做而不迁移用户配置；
- Mihomo API 变化只影响适配器和编译器；
- 未来试验 sing-box 时不需要复制订阅、路由和安全代码。

项目不复制 CMFA 的 UI/服务源码，但携带由其锁定 Mihomo submodule 构建的 `libclash.so`。
Weave 自行维护窄 JNI bridge；来源、构建参数和哈希均记录在 `core-lock.properties`。

## 核心数据模型

- `Subscription`：来源和更新策略，不等于运行时 provider。
- `ProxyNode`：归一化节点，保留 source subscription ID。
- `RouteTarget`：AUTO / FIXED / DIRECT / BLOCK。
- `AppRoute`：包名到 `RouteTarget` 的稳定引用。
- `ResolvedPolicy`：编译阶段生成，包含备用目标和最终内核 tag。

固定节点用 `(subscriptionId, nodeStableId)` 引用。0.3 的 `nodeStableId` 是订阅内序号与
显示名的摘要，凭据不进入摘要；节点重排或改名后会显式标记引用失效，不静默切换。后续协议
完整转换器会升级为规范化参数摘要并提供迁移。

## 多订阅到多出口

Mihomo 方案：

1. 每份订阅生成唯一 provider tag。
2. 为订阅生成自动策略组，如 `sub.daily.auto`。
3. 固定节点生成不可冲突的内部代理名。
4. Android 应用包名规则指向策略组或内部代理名。
5. 默认规则始终最后生成。

sing-box 方案：

1. 节点转成 outbound。
2. 自动选择转成 selector/urltest outbound。
3. `route.rules[].package_name` 指向对应 outbound。
4. Android 平台层提供 package/UID 映射能力。

UI 永远使用领域 ID；用户不接触这些内部 tag。

## 进程与生命周期

首发采用单应用、多进程可选结构：

- UI 进程负责显示和编辑，不持有 TUN。
- `:core` 进程负责 `VpnService` 和原生内核，降低 UI 崩溃对连接的影响。
- 进程通信只传配置版本、状态和统计，不传明文订阅 URL。

当前实现暂为同进程，完成真机连接闭环后再启用 `android:process=":core"`，以免在接口未稳定前
引入 Binder 复杂度。

## 配置更新事务

```text
拉取到临时文件
 → 限制大小/协议并解析
 → 归一化和引用检查
 → 编译候选配置
 → native validate
 → 原子替换 active config
 → hot reload
 → 健康检查
 → 成功提交 / 失败回滚
```

任一步失败都保留上一份可用配置。订阅更新不会直接覆盖正在运行的配置。

## 性能目标

- 冷启动到可操作首页：中端机 < 700 ms（不启动内核）
- 用户点击到 TUN 建立：< 1.5 s（已有配置）
- 10,000 条规则编译：< 300 ms
- 稳态 UI 进程 PSS：< 80 MB
- 无流量时额外耗电：8 小时 < 2%
