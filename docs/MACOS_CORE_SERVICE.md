# macOS 核心服务接入

当前已有共享通信模块 `WeaveCoreBroker`、GUI 复用的 `WeaveCoreRuntime`，以及实现实际核心生命周期的 `WeaveCoreService` 会话后端。后端已通过普通用户、回环环境的原生 XPC 集成验证；任意路径或注入工厂的 root 构造仍拒绝，新增宿主只接受生产安装能力对象。实际 Developer ID/root 环境尚未验收。GUI 的 TUN 路径现通过 Broker 使用该服务，本地系统代理模式继续使用普通用户核心。SMAppService 注册/撤销和授权状态界面已接入；未在本机注册服务或启动 TUN。

## 已实现

- 仅提供启动、停止、状态与 DNS 查询。启动只携带选中 provider 和节点名，不接收执行命令、核心/配置路径、订阅来源或控制密钥；返回状态、代理端口、IP 地址或固定错误码。
- v1 二进制帧使用明确字段，provider 上限 5 MiB、节点名 320 UTF-8 字节；DNS 查询/返回有长度和地址语法限制。拒绝未知版本/类型、截断、超长长度、非法 UTF-8、空值、控制字符及尾随数据。它只验证传输结构，**provider 内容仍是不可信 YAML**，必须由服务后端再次验证能力和配置语义，才能运行核心。
- 生产身份要求 Apple Developer ID Application、相同 Team、精确签名 identifier，以及不存在已启用的 `get-task-allow`。Team 从当前进程的有效签名取得，ad-hoc 预览不改用通配要求。要求字符串先由 Security.framework 编译，避免非法要求触发 Objective-C 异常。
- XPC 对收到的消息执行签名要求，因此客户端先发送不含机密的状态握手。只有收到被 OS 接受的回复，才按原顺序发送 provider 或 DNS 目标。连接中断后关闭旧通道，不自动复用新进程。监听端使用 OS 的 UID、审计会话和 PID；拒绝 root/未知 UID 与非法 PID。
- 每连接最多三个普通在途请求，另留一个停止名额；监听端最多八个连接。取消查询只取消调用方等待，其名额保持至回复被排空，不因此断开 VPN。取消启动则关闭会话并触发服务清理。超时关闭连接，不把未结束的远端工作当作空闲名额。
- 停止会取消先前的启动工作，等待其返回/清理后再发送停止回复；断开会取消全部在途任务，并等待会话清理。客户端断开通知只发一次。实际核心的退出和资源保留由下述 `WeaveCoreService` 后端承担；早期通信组件测试仍使用合成后端。

采用 macOS 13 起的公开 API，覆盖现有 macOS 14 最低系统版本：[NSXPCConnection 签名要求](https://developer.apple.com/documentation/foundation/nsxpcconnection/setcodesigningrequirement(_:))、[NSXPCListener 签名要求](https://developer.apple.com/documentation/foundation/nsxpclistener/setconnectioncodesigningrequirement(_:))、[代码签名要求语法](https://developer.apple.com/library/archive/documentation/Security/Conceptual/CodeSigningGuide/RequirementLang/RequirementLang.html)。服务注册后续使用 [SMAppService](https://developer.apple.com/documentation/servicemanagement/smappservice)，不使用提升整个 GUI 的方案。

## 已验证与证据范围

15 项测试声明通过，包含原生匿名 XPC、双向错误签名、无机密握手、普通 UID/PID、请求限流/停止名额、取消查询排空、取消启动、超时、监听端退出、单次断开通知，以及帧解析边界。启动/停止竞争重复 20 次通过。原应用 Release 构建通过。

开发中的通道初版已实测复现并修正两项问题：错误服务仍会收到第一条敏感请求；停止先返回成功，较早启动随后仍返回运行状态。这些是新模块的接入前回归，不是已经部署的 GUI 服务故障。

早期 XPC 测试使用同一普通用户进程中的两个真实 OS 端点和合成后端；新增后端集成测试仍使用同进程端点，但实际启动锁定核心并完成回环转发/DNS。它们均没有启动 VPN、修改系统代理、注册服务或读取真实订阅，不能替代独立 GUI/服务进程、不同用户、Developer ID 分发包、真实 TUN 或守护进程崩溃后的核心清理验收。

## 共享运行实现

`WeaveCoreRuntime` 不依赖 GUI 或用户订阅元数据，配置生成只取 provider 正文、节点选择和本地调用方提供的运行目录。代理组与证书材料文件使用新 UUID，写盘前再次确认节点确实存在。YAML 资源限制、锚点展开、私有目录租约、真实转发就绪和核心 DNS 都由原实现迁移，GUI 与后续服务无需各维护一份。

`CoreProcess` 固定核心命令参数并继承目录租约。并发停止共享一个不随等待方取消的任务；先 SIGTERM，再有界升级 SIGKILL，只有确认进程退出才允许调用方释放目录。停止失败仍保留对象和资源，后续可重试。系统代理设置和恢复仍属于原用户 GUI。

定向运行实际执行通过 40 项声明，另 1 项性能基准按默认策略跳过。覆盖原有核心/导入/转发回归，以及实际核心被 SIGSTOP 后，已取消的首个等待方与另两个停止等待方共同完成强制终止、租约保留和退出确认。测试只使用随机回环端口与合成订阅；没有启用 TUN 或改写用户代理。

## Provider 能力与证书边界

运行入口现在只接受 `proxies`，并按锁定核心 `e26714a` 的实际源码验证 14 种协议和 7 种 Shadowsocks 插件的封闭字段集合。未知协议、未知或未核验选项、绑定系统接口、routing mark、dialer override、非数据结构、超限标量/列表/头部均在写盘前拒绝。原 GUI 导入完整 Clash 文档时仍先提取节点；历史 Trojan URI 的冗余 `tls: true` 保持兼容，`false` 明确拒绝。未核验的核心扩展会阻止连接，不宣称支持全部 Mihomo 选项。

锁定核心的 [TLS 加载器](https://github.com/MetaCubeX/mihomo/blob/e26714a181ac0e2fa803453c0a8e9a9ce94e31cb/component/ca/keypair.go) 在内联解析失败后尝试读取文件。现在只接受有界 PEM 证书/配套私钥内容，写入本会话 `tls-materials/UUID.pem`（目录 0700、文件 0600、独占且拒绝链接），核心只收到这些生成的相对路径。包含嵌套传输证书，重复内容去重，上限 256 份、5 MiB；清理遇到未知文件或符号链接会保留会话。[SSH 加载器](https://github.com/MetaCubeX/mihomo/blob/e26714a181ac0e2fa803453c0a8e9a9ce94e31cb/adapter/outbound/ssh.go) 的私钥保持经过 PEM 包装校验的内联内容，避免进入文件路径分支；支持加密旧 PEM，由实际核心验证口令。WireGuard 私钥必须为 32 字节的 Base64 内容。

节点现在内联到生成配置，使 `-t` 实际构造各协议适配器；旧文件 provider 会延迟加载，已用缺少必填 alterId 的 VMess 配置 复现配置检查误放行。语义校验由实际锁定核心承担，PEM 外形检查不等于密码学验证；部分传输仍可能把检查推迟至连接阶段。固定节点与自动组使用明确且安全转义的节点列表。

新增 16 项测试声明、64 个场景通过，合并相关导入/网络/目录/进程回归共 67 项声明执行通过。验证包括 14 类协议的实际 `-t`、RSA/ECDSA/Ed25519 格式、错误配对和口令、五处嵌套材料、资源上限及清理保留。要求客户端证书的真实回环 TLS 转发对三种算法均校验收到的证书指纹，缺证书连接失败。测试端使用 PATH 中带 OpenSSL >= 1.1.1 的 Python，CI 显式选择 Python 3.13；系统旧 LibreSSL 的失败证据已保留，没有修改生产 TLS 或系统信任以使测试通过。

`483a746` 的 [完整 macOS CI](https://github.com/sundaysebasidian-byte/weave-client/actions/runs/41003221765) 通过 103 项实际执行测试（另 1 项性能基准跳过）、Release 构建与预览打包。公开合成测试私钥逐个固定路径/哈希登记在发行审计中；未知密钥、改变字节的已知夹具仍被拒绝，三项审计回归通过。

**这仍不是已交付的特权服务。** 核心自身另有 [路径安全检查](https://github.com/MetaCubeX/mihomo/blob/e26714a181ac0e2fa803453c0a8e9a9ce94e31cb/constant/path.go)，本阶段未证明原实现存在任意系统文件读取或提权。共享目录工具必须由宿主的固定私有目录与生产安装能力约束；客户端不能指定目录、执行文件或环境变量。配置检查子进程已由会话后端持有并确认退出。实际 root 服务及独立签名进程验收仍待完成。

## 实际会话后端

`WeaveCoreService` 为每条已认证连接创建独立会话，只有持有本服务实例独占归属的会话才能启动核心；其他连接不能停止或查询它的 DNS。状态只在实际核心、端口和转发就绪后变为 running。启动再次执行 provider 边界检查，并启动实际核心 `-t` 子进程；检查与运行阶段都持有运行目录租约，使用固定参数和仅含 PATH/HOME/TMPDIR 的环境，不继承调用方代理、核心路径或其他环境覆盖。

停止会取消并等待启动工作，随后确认子进程退出和目录回收。调用方取消不取消清理；并发停止共享任务。停止失败或遇到未知文件时保留核心/运行目录和独占归属，并向调用方返回失败；连接已经关闭时，服务仍强引用该会话，可通过服务关闭流程重试，不能因 XPC 对象释放而丢掉存活核心。意外核心退出会回收本会话并报告失败，可重新连接。服务关闭后拒绝新启动。

新增后端 9 项声明（10 个场景）通过；另 2 项进程回归覆盖配置检查的固定参数、隔离环境、超时/取消后仍持有实际子进程和租约。与既有 Broker/provider/目录/就绪测试合并，56 项声明通过。原生匿名 XPC 验证了真实 HTTP 转发、核心 A/AAAA 查询及断连后实际核心/目录回收；停止失败通过包内工厂故障注入验证，不涉及修改系统权限。普通用户间的真实隔离仍未验收。

同一个服务运行根目录下的独占归属现在覆盖不同实例和实际核心进程；宿主已绑定生产固定目录，但不等于生产 TUN 服务已经验收。root 入口必须取得经过独立验证的安装能力，普通路径构造仍拒绝；尚未连接到生产 GUI 或注册任何本机后台程序。以下验收仍是正式交付门禁。

复核修正：先前“无效 UUID 导致 VMess 检查失败”的描述不准确。锁定核心会映射非标准 UUID 字符串；原负例实际缺少必填 `alterId`。已用三份最小配置分别验证字符串 UUID 通过、缺少 `alterId` 和无效 cipher 被拒绝，并修正测试及验收描述。文件 provider 延迟适配器检查的问题仍由原证据支持。

## 完成接入仍需满足

1. 独立验证生产宿主的 Developer ID、launchd 身份、安装能力、固定核心及真实 root 会话；不接受客户端路径或环境变量覆盖。
2. 校验应用/核心安装位置、所有者、写权限、链接和签名；私有服务运行目录使用独占租约，真实核心退出未确认时保留文件和所有权。
3. 全机 TUN 独占租约与用户/连接归属；处理启动取消、GUI/服务退出、系统停止与核心崩溃，真实转发就绪后才报告连接成功。
4. 已实现 Bundled LaunchDaemon、SMAppService 注册/授权/撤销状态、普通用户 GUI 接入与签名打包；仍需真实签名安装、更新/卸载和跨用户恢复验收。测试包不弱化生产签名要求。
5. 在明确可丢弃环境中完成两个实际进程的认证和服务生命周期、不同用户隔离、真实 TUN 与升级/撤销；随后做真机网络切换和耐久。

## 安装完整性校验

打包先固定核心签名 identifier，再生成包含来源 SHA-256、签名后 SHA-256、上游 commit 和架构的 `core-integrity.json`，最后签名整个应用，使清单纳入资源封印。来源哈希仍对应 `core-lock.properties`；代码签名会改变 Mach-O 字节，已安装文件不能直接与签名前哈希比较。

新增 `CoreInstallation.validateManagedBundle` 使用 Security.framework 检查外层应用的全部架构、资源及严格签名规则，并单独核验核心的精确 identifier 和同一 Developer ID 团队，再流式核对签名后哈希和来源锁定记录。生产入口不接受调用方签名规则或 UID，ad-hoc 包不能满足它；测试中的 ad-hoc 规则仅用于验证资源封印和字节绑定。

路径检查从 `/` 逐级以目录描述符打开，拒绝符号链接、非 root 所有者、组/其他用户写权限、扩展 ACL 写入/删除/权限授予、硬链接和特殊文件。还核验整个包树；仅接受本地文件系统，并限制深度、数量和总字节。测试使用自有目录验证权限、ACL、链接、FIFO 和稀疏文件边界，不改系统目录权限。不存在扩展 ACL 的 Darwin `ENOENT` 已通过实际文件描述符探针确认；其他 ACL 读取错误保持拒绝。

**单独调用静态校验不授予执行权限。** `/Applications` 可能允许 admin 组写入，叶文件归 root 所有也不能防止父目录中的替换，因此没有为它豁免权限检查。下述私有快照已经实现复制与安装互斥；生产宿主的固定目录代码已接入，安装升级编排和真实高权限环境的完整恢复仍需验证。静态签名检查本身不是执行租约。Apple 的 [SecStaticCodeCheckValidity](https://developer.apple.com/documentation/security/secstaticcodecheckvalidity(_:_:_:)) 同样要求验证期间及之后代码不发生并发修改。任意路径 root 初始化继续拒绝；生产安装能力入口要求严格宿主环境，未注册本机后台服务或启用 GUI TUN。

## 跨进程租约与退出确认

服务先取得私有运行根目录中的 `.service-lease`，再准备或回收会话目录。锁文件仅接受当前服务所有者、私有权限、无写入 ACL 的本地普通空文件；拒绝链接、非空文件和特殊文件。它保留固定 inode，正常释放只关闭句柄，不 unlink 或显式解锁。未知会话文件或无法确认子进程退出时仍持有锁，另一个服务实例不能启动。

共享 `CoreProcess` 使用 `posix_spawn`：stdin 继续继承会话目录锁，服务锁通过另一个显式描述符继承，stdout/stderr 指向空设备，其他描述符默认关闭。固定参数和隔离环境规则不变。父服务的句柄全部关闭后，真实 Mihomo 仍持有两个锁；因此服务异常退出不会让另一实例误判核心已经离开，也不会回收其目录。该测试通过关闭父句柄模拟句柄丢失，**不是独立服务进程被终止后的完整恢复验收**。

通过 [DispatchSourceProcess 的退出事件](https://developer.apple.com/documentation/dispatch/dispatchsource/processevent/exit) 唤醒后以 `waitpid(WNOHANG)` 确认并回收子进程，连接期间没有定时退出轮询；进程回收和发送信号使用同一把锁，避免释放 PID 后继续发信号。异常失去子进程归属时拒绝进一步发信号或回收资源。本机独立探针发现 `waitid(WEXITED | WNOWAIT)` 也可能返回 SIGSTOP 状态，故不使用该返回作为退出证据。实际 SIGSTOP、取消/并发停止、信号退出状态及无僵尸回收均有回归覆盖。

目前租约防止重复启动和错误回收，不负责在父服务崩溃后自动终止孤儿核心。固定受保护目录、宿主进程组回收与安装互斥已有实现；真实 root 环境和跨用户验证仍是 GUI TUN 正式交付的前置条件。

## launchd 宿主崩溃边界

`CoreProcess` 显式把配置检查器和核心放入宿主当前进程组，不另建组或会话。Apple 的 [launchd.plist 手册](https://github.com/apple-oss-distributions/launchd/blob/main/man/launchd.plist.5) 指定：任务退出时清理同组剩余进程，`AbandonProcessGroup=true` 会关闭该行为。生产宿主必须保留默认清理（显式设为 false）并设置有界 `ExitTimeOut`；不能仅靠服务进程内部的异步停止处理 SIGKILL。

新增未打包的 `WeaveCoreLifecycleProbe`，以普通用户运行实际 `CoreService` 和锁定 Mihomo。CI 在一次性 GitHub macOS 运行器的 user 域临时注册唯一 launchd job，显式指定 `LimitLoadToSessionType=Background`（agent 默认的 Aqua 会话不适用于该域），验证正常 TERM、宿主 KILL、核心 STOP 后宿主 KILL、宿主 STOP 后 bootout；每次核对父子 PGID、实际核心退出、端口关闭、服务锁释放、同根目录/同锁 inode 重启与遗留目录回收。另有 `AbandonProcessGroup=true` 负对照，必须实际观察到孤儿核心仍占锁，随后仅终止该夹具精确身份的核心。结束时 bootout 自建 job，保存逐项证据。

该脚本拒绝在本地或 root 身份执行，不使用 sudo/system 域或持久 enable/disable 设置。普通本地验证只运行独立 helper 的 self-check，不注册后台任务。这里验证的是实际服务后端在 launchd 生命周期下的行为，尚未构成已安装的 Developer ID/root 服务或 GUI TUN 交付；生产宿主已复用相同的信号和清理执行路径，实际安装与签名身份验收尚未完成。各场景是否通过以对应提交的原生 CI 和 artifact 为准。

## 私有核心快照与安装互斥

`ManagedCoreInstallation` 的生产入口先要求 root 和有效 Developer ID 身份，再使用固定的 `/Library/PrivilegedHelperTools/io.weave.client.macos` 私有目录。普通用户入口在任何系统目录操作前拒绝；内部测试只能使用测试进程自己的目录和签名要求。没有放宽 `/Applications` 的祖先权限检查：来源应用被视为不可信输入，通过 no-follow 描述符有界复制到独占暂存目录后，才检查完整资源封印、独立核心身份、锁文件与签名后哈希。

验证成功后以不覆盖的原子重命名发布整个已签名应用身份对应的版本；已有版本须重新通过完整检查、具有相同应用签名身份和核心清单，损坏或未知内容原样保留并拒绝使用。复制不继承来源 ACL 或写权限，拒绝链接、特殊文件、超深目录和超量内容。文件及目录同步后才发布；本次创建的暂存目录在失败或缓存复用后移除，清理失败明确携带路径及原始错误返回，不默默忽略。真实断电持久性尚未验收。

快照持有独立安装租约，`CoreService(installation:)` 固定使用该快照、私有运行根目录，并把安装锁传给每一个检查器与核心。真实签名 Mihomo 在来源应用删除后仍能检查配置及启动；关闭父句柄后，核心仍阻止重新取得安装/服务/会话目录锁，确认退出后释放。另有实际会话后端回归核验自动继承安装锁。安装锁与既有运行租约分开，旧版本不会被此次准备操作覆盖或自动删除。

**root 只接受生产安装能力。** 此组件及普通用户验收不代表 SMAppService、真实签名/root 服务、跨用户升级或 GUI TUN 已交付；ad-hoc 测试要求不会进入生产入口。

## 打包宿主与生产入口

`WeaveCoreServiceHost` 作为独立 Mach-O 打包并以固定服务 identifier 签名，LaunchDaemon plist 位于应用内 `Contents/Library/LaunchDaemons`。按 Apple 的 [SMAppService 迁移说明](https://developer.apple.com/documentation/servicemanagement/updating-helper-executables-from-earlier-versions-of-macos)，plist 使用相对 `BundleProgram`；GUI 使用 SMAppService 完成注册/撤销，注册成功后仍须检查授权状态；没有把 plist 写入本机系统目录。

宿主拒绝额外参数，只从内核提供的自身可执行路径推导固定应用布局；要求 root、父进程 launchd 和独立作业进程组。签名验证同时约束当前进程的精确角色与对端角色。生产安装在私有快照内验证宿主签名和严格作业策略，随后才创建不可由测试路径授予的 root 执行能力。任意核心路径/工厂的 root 构造仍拒绝。

作业策略固定 Mach service、root 用户、组回收和停止超时，拒绝自定义参数、环境变量或额外键。当前进程在信号等待前建立资源所有权；TERM/INT 停止监听，关闭会话并确认服务清理。普通用户的生命周期验收复用同一个 runner 和信号实现；它不代替实际签名/root 安装及多用户验收。

快照键改用 Security.framework 的应用签名唯一身份的 SHA-256，覆盖应用/宿主/策略更新。只有核心哈希相同不足以复用旧应用快照；应用版本改变而核心不变的回归确认生成新目录，旧目录和字节保持原样。已有旧缓存不自动迁移或删除。

## GUI 授权与会话恢复

设置页提供启用、系统授权设置、刷新和停用服务。进入应用前台重新检查状态；未正式签名的预览不执行注册，不发送 provider。注册返回后仍要求 `.enabled`，`.requiresApproval` 引导到系统设置，不当作已经连接。Apple 明确规定 LaunchDaemon 需要管理员批准后才会启动：[register()](https://developer.apple.com/documentation/servicemanagement/smappservice/register())。

TUN 连接只使用认证 Broker，不使用 GUI 核心路径或环境变量。DNS 查询也通过当前服务会话；取消启动、状态检查发现核心退出、XPC 中断均不能静默切换本地代理。失联时不宣称远端已停止。服务返回停止失败时保留同一通道以供重试；确认停止后才能从界面撤销注册。撤销是全机服务操作，可能影响其他已登录用户。普通系统代理模式保持原有事务恢复路径。

控制器每两秒检查当前已连接会话状态，旧通道回调不能改变新会话；AppModel 转发核心状态变化供界面刷新。并发停止等待者在返回前按操作 ID 清理完成标记。新增 GUI 测试使用注入的授权状态，原生集成进一步经过真实匿名 XPC、实际服务后端及锁定 Mihomo 完成三次连接、停止和宿主断连；后端明确关闭 TUN，因此不将其记为已安装 root/真实网络验收。

描述符继承测试故意打开未设置 CLOEXEC 的无关句柄，现独立执行，避免其他并发测试子进程干扰其归属断言。Foundation 子进程继承假设未复现；此前间歇启动失败的根因仍未确认，后续通过不抹除失败证据。
