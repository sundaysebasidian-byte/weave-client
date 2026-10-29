# Windows 核心服务迁移

状态：服务协议、会话后端和 Windows 管道验证代码已加入；尚未安装或接入 WinUI，当前 TUN 仍使用原提升权限流程。不得把本文件或组件测试当作权限分离已交付。

## 已实现的边界

- `BrokerProfile` 只携带本次使用的 provider、节点标识、应用规则和固定设置；不含订阅来源、展示名、任意配置/核心/geodata 路径。服务强制生成 TUN 配置，重新核验节点和规则；DNS 不允许文件、用户信息或选择接口的 fragment。
- `BrokerRuntimeInfo` 只返回代理端口、IPv6 设置和 provider 数量，不返回核心控制密钥、控制端口或会话文件路径。测速、流量和 DNS 只能通过对应命令；测速 URL 固定，节点须属于当前会话。
- 消息采用有版本的长度帧，单帧 40 MiB；拒绝重复/未知字段、未知操作、缺失或非法 null 参数。已开始的帧 30 秒超时，已认证空闲连接不会被当作传输超时。每连接最多四个未完成请求。
- `BrokerWindowsPipe` 使用首次实例与精确客户端访问权限，拒绝网络/匿名身份。客户端以 Identification 打开本机管道，发送任何连接数据前核对 SCM 报告的服务 PID 和进程映像；服务读取固定握手后核对 OS 客户端 SID、PID、会话与指定 GUI 映像。安装目录保护仍须由服务宿主完成。
- `BrokerSession` 持有私有会话、核心和控制器；连接 EOF、停止请求或服务停止会撤销在途启动并清理本会话。校验探针测试实际启动一个无网络子进程，证明 EOF/停止会终止它；清理失败保留文件及状态。
- Windows YAML 在别名展开时累计 UTF-8 标量预算，在输出时限制缓冲区和最终字节数，避免小输入通过别名或转义绕过 5 MiB provider 上限。

管道策略依据 [Windows 管道访问权限](https://learn.microsoft.com/en-us/windows/win32/ipc/named-pipe-security-and-access-rights)、[客户端身份与模拟级别](https://learn.microsoft.com/en-us/windows/win32/ipc/impersonating-a-named-pipe-client) 和 [OS 客户端 PID 查询](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-getnamedpipeclientprocessid)。客户端没有创建同名服务端实例的权限；首次实例检查仍不能代替 SCM 身份核验。

## 完整接入前必须完成

1. 服务宿主只从管理员保护的安装目录加载固定核心及依赖；验证路径、ACL、重解析点和核心哈希。ProgramData 会话根目录原子创建私有 ACL，未知/弱权限旧目录拒绝使用。
2. SCM 生命周期、受限服务权限与服务崩溃时子进程退出。核心启动前即建立进程生命周期约束，不能只在子进程启动后补 Job。
3. 普通权限 GUI 接入：连接、取消、断开、流量、测速、订阅 DNS 和异常退出；系统代理仍由用户进程按归属恢复。服务不可用不能回退为提升整个 GUI。
4. 安装/升级/卸载事务：受保护的机器级二进制、现有每用户数据保留、服务停止确认、失败恢复。不同管理员凭据授权不得切换用户配置。
5. 原生 Windows 上验证标准用户访问、假管道/错误映像拒绝、其他用户不能控制现有会话、GUI/服务进程退出、真实 TUN 转发和卸载恢复。

组件本地测试使用 macOS 回环或合成数据，不接管主机网络。Windows 专属身份测试在其他系统明确跳过。`Weave.BrokerProbe` 仅为测试进程，未包含在产品发布目录。
