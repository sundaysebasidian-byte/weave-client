# macOS 核心服务接入

当前完成共享通信模块 `WeaveCoreBroker`，并从 GUI 提取 `WeaveCoreRuntime` 的配置、解析、就绪、DNS 和进程生命周期实现。GUI 已复用运行模块，但尚未通过 Broker 启动 TUN，也没有安装或注册 LaunchDaemon。现有 GUI 仍自行启动核心，不能把本阶段称为特权服务交付。

## 已实现

- 仅提供启动、停止、状态与 DNS 查询。启动只携带选中 provider 和节点名，不接收执行命令、核心/配置路径、订阅来源或控制密钥；返回状态、代理端口、IP 地址或固定错误码。
- v1 二进制帧使用明确字段，provider 上限 5 MiB、节点名 320 UTF-8 字节；DNS 查询/返回有长度和地址语法限制。拒绝未知版本/类型、截断、超长长度、非法 UTF-8、空值、控制字符及尾随数据。它只验证传输结构，**provider 内容仍是不可信 YAML**，必须由服务后端再次验证能力和配置语义，才能运行核心。
- 生产身份要求 Apple Developer ID Application、相同 Team、精确签名 identifier，以及不存在已启用的 `get-task-allow`。Team 从当前进程的有效签名取得，ad-hoc 预览不改用通配要求。要求字符串先由 Security.framework 编译，避免非法要求触发 Objective-C 异常。
- XPC 对收到的消息执行签名要求，因此客户端先发送不含机密的状态握手。只有收到被 OS 接受的回复，才按原顺序发送 provider 或 DNS 目标。连接中断后关闭旧通道，不自动复用新进程。监听端使用 OS 的 UID、审计会话和 PID；拒绝 root/未知 UID 与非法 PID。
- 每连接最多三个普通在途请求，另留一个停止名额；监听端最多八个连接。取消查询只取消调用方等待，其名额保持至回复被排空，不因此断开 VPN。取消启动则关闭会话并触发服务清理。超时关闭连接，不把未结束的远端工作当作空闲名额。
- 停止会取消先前的启动工作，等待其返回/清理后再发送停止回复；断开会取消全部在途任务，并等待会话清理。客户端断开通知只发一次。实际核心的退出和资源保留仍由后续 `BrokerSession` 后端承担，当前测试后端没有启动核心。

采用 macOS 13 起的公开 API，覆盖现有 macOS 14 最低系统版本：[NSXPCConnection 签名要求](https://developer.apple.com/documentation/foundation/nsxpcconnection/setcodesigningrequirement(_:))、[NSXPCListener 签名要求](https://developer.apple.com/documentation/foundation/nsxpclistener/setconnectioncodesigningrequirement(_:))、[代码签名要求语法](https://developer.apple.com/library/archive/documentation/Security/Conceptual/CodeSigningGuide/RequirementLang/RequirementLang.html)。服务注册后续使用 [SMAppService](https://developer.apple.com/documentation/servicemanagement/smappservice)，不使用提升整个 GUI 的方案。

## 已验证与证据范围

15 项测试声明通过，包含原生匿名 XPC、双向错误签名、无机密握手、普通 UID/PID、请求限流/停止名额、取消查询排空、取消启动、超时、监听端退出、单次断开通知，以及帧解析边界。启动/停止竞争重复 20 次通过。原应用 Release 构建通过。

开发中的通道初版已实测复现并修正两项问题：错误服务仍会收到第一条敏感请求；停止先返回成功，较早启动随后仍返回运行状态。这些是新模块的接入前回归，不是已经部署的 GUI 服务故障。

当前 XPC 测试使用同一普通用户进程中的两个真实 OS 端点和合成后端，没有启动 VPN、修改系统代理、注册服务或读取真实订阅。它们不能替代独立 GUI/服务进程、不同用户、Developer ID 分发包、真实 TUN 或服务崩溃后的核心清理验收。

## 共享运行实现

`WeaveCoreRuntime` 不依赖 GUI 或用户订阅元数据，配置生成只取 provider 正文、节点选择和本地调用方提供的运行目录。provider/代理组使用新会话 UUID，写盘前再次确认节点确实存在。YAML 资源限制、锚点展开、私有目录租约、真实转发就绪和核心 DNS 都由原实现迁移，GUI 与后续服务无需各维护一份。

`CoreProcess` 固定核心命令参数并继承目录租约。并发停止共享一个不随等待方取消的任务；先 SIGTERM，再有界升级 SIGKILL，只有确认进程退出才允许调用方释放目录。停止失败仍保留对象和资源，后续可重试。系统代理设置和恢复仍属于原用户 GUI。

41 项定向声明通过，含原有核心/导入/转发回归，以及实际核心被 SIGSTOP 后，已取消的首个等待方与另两个停止等待方共同完成强制终止、租约保留和退出确认。测试只使用随机回环端口与合成订阅；没有启用 TUN 或改写用户代理。

**共享运行模块不是服务安全策略。** 现有 `prepare` 仍为普通 GUI 清洗顶层配置，没有封闭节点能力校验；目录工具也未成为 root 安装/运行路径边界。锁定版本 `e26714a` 的 [TLS 加载器](https://github.com/MetaCubeX/mihomo/blob/e26714a181ac0e2fa803453c0a8e9a9ce94e31cb/component/ca/keypair.go) 在内联解析失败后尝试文件读取，[SSH 加载器](https://github.com/MetaCubeX/mihomo/blob/e26714a181ac0e2fa803453c0a8e9a9ce94e31cb/adapter/outbound/ssh.go) 同样接受私钥路径。核心另有 [路径安全检查](https://github.com/MetaCubeX/mihomo/blob/e26714a181ac0e2fa803453c0a8e9a9ce94e31cb/constant/path.go)；这里没有证明任意系统文件读取或提权。服务入口仍须独立验证 provider、环境、固定核心及完整路径权限，并管理配置检查子进程的存活/清理，不能直接把共享调用暴露给客户端。

## 完成接入仍需满足

1. 基于共享运行实现创建实际 `BrokerSession` 后端；根服务重新验证 provider 的封闭能力集合、体积和协议语义，固定核心哈希与配置生成入口，不接受客户端路径或环境变量覆盖。
2. 校验应用/核心安装位置、所有者、写权限、链接和签名；私有服务运行目录使用独占租约，真实核心退出未确认时保留文件和所有权。
3. 全机 TUN 独占租约与用户/连接归属；处理启动取消、GUI/服务退出、系统停止与核心崩溃，真实转发就绪后才报告连接成功。
4. Bundled LaunchDaemon、SMAppService 注册/授权/撤销状态、普通用户 GUI 接入、签名打包和更新/卸载恢复。测试包不得弱化生产签名要求。
5. 在明确可丢弃环境中完成两个实际进程的认证和服务生命周期、不同用户隔离、真实 TUN 与升级/撤销；随后做真机网络切换和耐久。
