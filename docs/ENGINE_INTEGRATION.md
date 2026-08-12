# Mihomo Android 内核接入

0.3 已接入最小可工作的 CMFA/Mihomo 数据面。`MihomoEngineAdapter` 仍保持 fail-closed：
任一原生库加载、配置校验、TUN 建立或 socket protect 失败都会停止服务。

首个候选构建已经固定在 [`core-lock.properties`](../core-lock.properties)，来源说明见
[`CORE_PROVENANCE.md`](CORE_PROVENANCE.md)。应用不得在运行时自动下载或替换 native core。

## 上游与许可证

CMFA 说明其内核来自 Mihomo Android 分支，并通过 Go/Android 工具链构建。集成前：

1. 选择明确存在且维护中的上游仓库与 Android 分支；
2. 固定 commit，而不是跟踪 `Alpha` 或 `latest`；
3. 复核该 commit 的许可证、NOTICE、依赖和构建产物；
4. 使用 `tools/build-core.sh` 复现四个 ABI；
5. 发布源码对应关系和可复现步骤。

注意：2026 年公开 GitHub 路径存在迁移/同名仓库风险，不能仅凭仓库名称自动拉取内核。CI 应
额外校验 owner、commit 签名或固定对象哈希。

## Android 绑定最小接口

原生层只暴露窄接口：

```text
validate(configPath) -> structured errors
start(configPath, tunFd, protectCallback) -> sessionId
reload(sessionId, configPath) -> result
queryState(sessionId) -> health + counters
stop(sessionId) -> result
version() -> upstream commit + build metadata
```

禁止直接把完整 controller API 暴露在局域网 TCP 端口。进程内控制优先使用 JNI/gomobile；
必须用 socket 时仅绑定 Unix domain socket、校验 peer UID 并采用随机会话 token。

## TUN 建立顺序

1. `VpnService.prepare()` 获得用户授权。
2. 编译配置到 app-private 临时目录。
3. 调用 native `validate`。
4. 启动前台服务并创建通知渠道。
5. 配置地址、DNS、IPv4/IPv6 路由和必要的 Android 应用 allow/disallow list。
6. `Builder.establish()` 取得 TUN fd。
7. 原生层成功接管 fd，并确认 reader loop 已启动。
8. 保护所有外连 socket，防止流量重新进入 TUN。
9. 通过最小健康检查后，UI 才显示“已连接”。

步骤 6–8 任一失败必须立刻关闭 fd、停止内核并撤销通知。

## 应用分流

Android VpnService 应用列表只做是否进入 VPN 的粗粒度选择。进入 TUN 后：

- 从内核支持的 Android UID/包名映射能力获得源应用；
- 编译 `packageName → internal policy tag`；
- policy tag 再解析到某订阅的自动组或固定节点；
- 配置验证阶段拒绝悬空引用；
- 运行时节点消失则使用用户可见的 fallback，不静默换到任意订阅。

`RouteConfigCompiler` 生成 Mihomo `PROCESS-NAME` 规则；0.3 通过 CMFA 的 UID/包名回调把
Android 连接映射到包名。该语义仍必须在真机矩阵验证，不能只用桌面版配置验证替代。

## 当前格式边界

- Clash YAML 会作为 app-private `file` proxy-provider 加载。
- 每个 provider 使用独立名称前缀，避免不同订阅出现同名节点。
- URI/Base64、sing-box JSON 和基础 V2Ray JSON 在导入边界转换成临时 Clash provider；复杂或
  缺字段的协议会 fail closed。旧版本留下的原始 payload 在运行时也会经过同一转换器，不会
  因历史记录绕过校验。

## 测试清单

- [x] Android 16 ARM64 模拟器 JNI、双栈 TUN、DNS、TCP、UDP 与断开清理闭环
- ARM64 真机最小 TCP、UDP、QUIC、DNS 闭环
- 每种协议至少一个成功和失败样例
- 包名规则在共享 UID、工作资料、克隆应用下的行为
- 100 次连接/断开无 fd、线程、端口泄漏
- Wi‑Fi/蜂窝来回切换 50 次
- 内核 kill -9 / native panic 后恢复
- 10k / 100k 规则的编译、加载与内存基线
- Android 8、10、12、14、16 的前台服务和 VPN 行为

模拟器验证的系统镜像、证据和边界见 [`RUNTIME_VALIDATION.md`](RUNTIME_VALIDATION.md)。
