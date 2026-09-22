# Changelog

This project follows semantic versioning while in alpha. Breaking storage or
configuration changes may still occur before 1.0.

## Unreleased

## Android `0.3.0-alpha83` (public ARM64 preview)

- 汇总 alpha78–83：改善底层网络变化恢复、失效出口处理、订阅更新预览与敏感数据存储；保留原有主题与操作路径。
- 首页统计在滚动和后台暂停、空闲时降频；策略包导入、加密存储及启停操作移到后台线程。未宣称实测耗电降幅或保证 120 fps。
- 网络与隐私检测逐项发布结果，固定卡片位置；区分 DNS/TLS/超时、重定向及服务响应，不将其等同于流媒体解锁或无泄漏。
- 取消检测或切换网络后拒绝旧结果覆盖当前状态；新增可预览、手动复制的脱敏摘要，不含实际 IP、订阅令牌、节点名称或浏览器指纹。
- 浏览器检测拒绝第三方 Cookie，增加渲染进程终止处理；补齐新增提示的六语言翻译。
- 本地 229 项 JVM 测试与 3 项检测界面回归通过。alpha83 已在 vivo 上保留数据覆盖安装；这不代表长时间连接、全机型或耗电测试完成。
- 仅发布 Android ARM64；沿用本地开发签名、不可调试的优化预览包。Windows/macOS 本轮改动不进入此发布。

## Android `0.3.0-alpha79` (local preview)

- 同一底层网络的 DNS、路由、地址或 MTU 实际变化触发去抖恢复；重复回调不触发，网络过渡计时使用单调时钟，主动断开后不继续出站重试。
- 首页恢复统计前等待 350 ms，连续快速滑动时取消短暂恢复，保留后台暂停与空闲降频；未实测耗电降幅。
- 订阅准备流程核对主文件与集合的节点总数；手动出口失效不再静默改成自动选择，相关应用规则阻止联网，默认出口要求重新选择。
- 应用连接记录可点选查看配置出口解释；明确不是内核最终规则命中证据。
- IP、站点和 WebView 检测展示时间及范围，刷新保留的是上次结果；断开或恢复中不把过滤配置标成已生效保护。
- 恢复运行配置先完成暂存复制，再替换目录；恢复中心可滚动并复制仅含允许字段的诊断摘要，不导出原始日志或订阅。
- 原始节点名称匹配测速结果，避免去装饰后的同名节点串结果；搜索和订阅选择保留界面重建状态，新文案覆盖六种语言。保持既有主题外观。

## Android `0.3.0-alpha78` (local preview)

- 移除首页未显示却每轮更新的连接时长字段；相同运行状态不再反复发布，流量变化复用节点对象。
- 首页无流量连续三次后，统计查询从 3 秒降为 15 秒；有流量恢复 3 秒。返回首页立即刷新，弹窗遮挡、滚动和切到后台时暂停首页查询。
- 首页主卡片与出口卡片只接收各自需要的状态，避免流量数字变化导致无关卡片重组；不改变外观。
- 仅优化 UI 统计，不更改 VPN 前台服务、隧道、DNS、健康检查与断网保护；尚未实测耗电降幅。

## Android `0.3.0-alpha77`

- GitHub 发布构建号 83：排除本地历史运行缓存，更新项目介绍、八主题展示图与 Android 构建指引；保持既有本地预览签名。
- 修复订阅详情节点滚动区域被表单/质量矩阵挤压的问题：详情与节点合为单个有界 LazyColumn，节点按需加载，标题和底部按钮保持固定。颜色、卡片外观、订阅及内核逻辑不变。

## Android `0.3.0-alpha76`

- 修正订阅请求遗漏 CMFA `.Meta` 标识、强制绕开当前 VPN 的问题；刷新保留原订阅源，不持久化临时重定向地址。
- Clash 主文件/节点集合支持 JSON、flow YAML、Base64 包装；支持有界 gzip/deflate 响应解压。统一准备流程检查规范化前后节点一致性。
- 订阅详情显示主文件、集合、最终导入的安全计数；保留更新失败和节点骤减保护。真实 Walless 节点数量仍需更新确认，不将合成测试当作完整修复证明。

## Android `0.3.0-alpha75`

- Walless 不再强制添加旧 `client=cfa&provider=false`；仅迁移旧版自动生成的完整参数组合，保留自定义参数，继续解析节点集合并拦截节点数骤减。用户确认 CMFA 刷新后有 65 个；本版尚未用脱敏实样验证恢复数量。
- 节点测速明确显示 0%/部分失败/100% 与成功次数；统一称为 HTTP 探测失败率，不宣称是真实 ICMP/UDP 丢包率。内核控制或读取失败不再静默舍弃一轮。
- 网络与隐私检测中两个 HTTPS 端点各进行 3 次短探测，显示每个端点及整体失败率、样本数；仅用户触发，无后台定时检测。少量样本仅供参考。

## Android `0.3.0-alpha74`

- Walless 请求默认加入官方 `cluster=false`，展开被服务端聚合的服务器；保留显式参数和其他供应商地址，须刷新远程订阅才会拉取完整列表。
- 节点减少超过一半时阻止覆盖并保留旧订阅；修复只给警告却继续保存以及不实的可回退文案。真实 Walless 定向刷新仍为 23 个，65 节点差异尚待新鲜对照配置确认。
- Clash 元数据与保留的原生节点对象一一对应，不再用 URI 转换名单静默过滤原生类型；缺失名称/类型明确拒绝。升级重建本地索引，保留既有节点 ID 与密文，原生启动校验不变。
- 迁移识别补齐 CMFA 主包/Alpha/Meta、Clash Mi、FlClash 开发版等；采用精确可见性查询，保留名称匹配与手动导入，支持重新检测，不增加全量应用扫描权限。
- 粘贴客户端包装链接与扫码共用 HTTPS 解码；新增 Clash Meta/Verge/Mi 包装格式，迁移弹窗支持滚动。仅导入兼容订阅文件/链接/二维码，不宣称支持所有客户端的完整备份。
- 新增极简“素纸”外观：黑白中性色、平整卡片，跳过玻璃高光和卡片阴影；其他主题保持原样。新文案补齐六种语言。

## Android `0.3.0-alpha73`

- 分享范围改为显式勾选，默认不选，支持全选/全不选与整行点按；订阅列表更新不重置选择。
  二维码生成后展示实际分享清单；修改范围先使旧链接失效；后端拒绝空选择或已失效 ID，绝不回退为全部分享。
- 分享编码与加密移出主线程；生成取消时清理监听器，旧分享任务不能关闭后来生成的新链接。

- 订阅索引修复、解密和列表读取移出首页主线程，合并元数据/节点读取；完成加载后再校正引用，避免把尚未加载的节点误删。
- 首页滚动和打开检测/节点选择时暂停统计轮询；检测结果保留在内存，手动刷新或连接失效时更新，关闭检测页取消任务。
- 节点选择加入搜索、收藏、当前订阅定位与检测时间，最多保留八份订阅的测速结果；固定节点选择沿用原生固定出口。
- 检测中心加入按需短时下载测速（最多 1 MiB）；IP 检测耗时改用单调时钟。
- 系统断网保护读取 Android 10+ 的实际 Always-on/lockdown 状态，未知或未连接不伪报已保护，检测中心可进入系统 VPN 设置。
- 分流加入大陆直连/海外代理和全部代理预设；路由解释先匹配安全规则，补充真实优先级，地域规则保持待内核确认。
- 按需应用连接记录仅记录系统应用归属、协议、目标端口，最多 64 条且不落盘；不是内核最终规则命中日志，关闭即清除。
- 新界面文案覆盖简中、繁中、英语、日语、法语、德语。

## Android `0.3.0-alpha72`

- 修复真机 `S02:wm1`：旧版逐行导入器误将未缩进的规则尾部附在 proxies 列表之后。
  只兼容“完整节点对象 + 可识别规则文本尾部”，保留全部节点，不执行尾部规则，原始加密订阅不变。
  任意损坏节点、混合类型和不明确文本仍拒绝，原生校验和 VPN 就绪检查仍保留。
- 扫码改成 CameraX 实时取景、本地 ZXing 识别，支持点击对焦与补光；不拍照、不保存画面，
  离开扫描页释放相机。局域网二维码使用同一实时扫描器。
- 恢复始终可见的迁移入口，支持导出文件、链接和二维码，不读取其他客户端私有数据。
- 移除新手模式、自订导航及其隐藏规则逻辑；固定连接、分流、订阅、设置四页。
- 白绿使用纯白背景、草木绿操作按钮和中性色卡片；重做深色层次，移除深海蓝与夜松青并迁移到
  深色模式。艺术风四种配色保留；加强缓存高光和浅层阴影，不引入背景模糊或切页动画。
  修复透明页面容器未提供正文色，导致深色模式下部分标题和图标仍继承黑色的问题。
- versionCode 75，沿用包名与本地签名；使用递增构建号避免 OEM 同版本重装问题，覆盖安装不清空订阅。
  验证范围见 `docs/RUNTIME_VALIDATION.md`。

## Android `0.3.0-alpha71`

- 修正内核启动与系统 VPN 路由发布之间的状态竞态：内核返回成功后仍等待系统默认网络切换到
  VPN，再报告连接成功；等待仅查询本机状态，有 5 秒上限，不向外部服务器发送探测。
- 启动失败显示稳定的 S01—S06 阶段码，恢复中心保留阶段及脱敏类别；日志仅写异常类型和
  堆栈位置，不写节点地址、订阅令牌或原始异常消息。补齐 `proxies` 与小写 DNS 校验错误分类。
- 不再用“已保留安全状态”描述未建立的连接；通用失败明确表示 VPN 未连接。错误阶段码不影响
  既有语言切换。versionCode 73，保留应用 ID 和覆盖签名，不修改或删除订阅。
- 新增 Hysteria2 配置组合校验和真实 VpnService 转发回归。用户截图中的启动失败尚未在真机
  定位；本版包含已复现时序修复和诊断改善，不代表真实 Walless/DMIT 连接问题已确认解决。

## Android `0.3.0-alpha70`

- 导入主订阅中的 HTTPS/inline `proxy-providers`，把完整节点集合加密保存到本地；同一子 URL
  只下载一次，限制集合数量、总大小和递归。子集合失败不提交半份订阅，不执行其控制面与规则下载。
- 使用有大小、深度和展开数量限制的 SafeConstructor 解析 Clash YAML；正确处理 Unicode 名称、
  不同缩进和 YAML 合并字段。删除了容易丢节点的手写 YAML 扫描器。
- 原生运行配置改为内嵌节点集合，使每个节点在内核校验阶段就参与解析；不再依赖第二份临时
  provider 文件。修正 alpha69 将 `COMPATIBLE` 误当短暂观察延迟而继续连接的处理。
- 订阅存储使用进程内共享互斥，避免另一个实例清理正在提交的文件；从保留的加密内容重建
  损坏/旧版节点索引，保留可匹配节点 ID。首次生成 Keystore 密钥也避免并发覆盖。
- 修复匿名 HTTP/SOCKS 链接或未填写节点名称时的空指针；可省略字段按空值处理，必需凭据
  缺失仍明确拒绝导入。新增实际仓库导入、重新打开后节点保留的 Android 回归测试。
- 修复压缩包中 YAML 库因类被移动到无名包而初始化失败；只保留库的包名，不关闭全局压缩。
- versionCode 72，沿用原包名和本地签名。已有订阅不清空；旧版遗漏的远程集合需更新该订阅
  才能补齐，已被旧版本删除且无备份的内容不能凭空恢复。

## Android `0.3.0-alpha69`

- 修复 alpha64—alpha68 的连接回归：不再把 CMFA `queryGroup` 在 provider 异步载入期间的
  短暂空值、`COMPATIBLE` 或不同 JSON 形状当成启动失败。订阅内容规范化与原生配置校验仍然
  是强制步骤，但节点组观察仅写入不含端点和凭据的诊断信息，不再阻止 TUN 启动。
- 恢复 Mihomo 并发 IPv4/IPv6 地址拨号，避免双栈节点域名先返回不可用地址族时表现为所有流量
  断网。versionCode 升至 71。

## Android `0.3.0-alpha68`

- 修复在已有 VPN/代理接管系统默认网络时导入远程订阅容易在 10 秒后超时的问题：仅当系统默认
  网络确实是 VPN 时，订阅请求会依次尝试当前可用的实体以太网、Wi‑Fi 和移动数据网络，并让
  每次尝试的 DNS 解析与 HTTPS socket 使用同一网络；没有 VPN 时保持系统原有网络路径，
  不改变用户的代理意图。
- Walless 当前及历史订阅域名会自动补齐 Clash for Android 兼容参数；支持一次 Base64 包装的 Clash
  YAML、sing-box JSON 和 V2Ray JSON，导入后仍只保留节点 provider，不执行远程控制面配置。
- 订阅连接失败改为脱敏的可操作提示，不把主机、令牌或底层异常文本直接展示给界面。
- 修正原生 provider 集成测试使用的路径示例，与 CMFA Android wrapper 的实际路径解析保持一致。

本版仍需在 Android 17 真机上验证不同运营商、VPN 叠加及 Walless 实际账号返回内容；构建通过不
等于所有订阅服务器都可达。

## Android `0.3.0-alpha67`

- 修复 CMFA Android provider 路径回归：配置现在传入 provider 文件名，由 CMFA 统一解析到
  `mihomo-runtime/providers/`；不再错误地传入 `providers/<文件名>` 导致实际查找
  `providers/providers/<文件名>`，从而策略组只显示 `COMPATIBLE`。
- 连接就绪检查增加兼容 JSON 形状与隐私安全诊断：支持 CMFA 对象数组及旧桥接的字符串/`all`
  列表，并在失败时仅记录组类型、成员计数和数据形状，不记录节点地址或凭据。
- 仅对当前数据路径上的自动策略组执行阻断式就绪检查；手动固定节点不再被未使用的自动组拖累。

- Android `0.3.0-alpha66`：放宽 provider 发布就绪的有界等待，从 0.9 秒增加到最多 3.9 秒，
  覆盖大体积 Clash/OpenVPN 锚点配置在较慢 OEM 存储上的异步解析延迟；仍然只接受真实节点，
  不会把 `COMPATIBLE` 当成可用出口。versionCode 升至 68。

- Android `0.3.0-alpha65`：首次尝试修复订阅 provider 运行路径，但未区分 CMFA Android
  wrapper 会自动补上 `providers/`；该尝试在 alpha67 已回溯为仅传文件名。versionCode 升至 67。

- Android `0.3.0-alpha60`：主页面列表使用小型前后预组装窗口，避免快速滑动时集中执行阴影、裁剪和文字测量；节点、订阅、分流、应用和页面状态声明为不可变，减少无关重组。新增仅供本地覆盖测试的 `localOptimized` 构建变体，沿用正式版 R8/资源压缩但使用本机调试证书。卡片、颜色、圆角、阴影和滚动中的视觉效果保持原版不变。versionCode 升至 62。

- Android `0.3.0-alpha59`：撤销所有会在拖动/惯性滚动期间改变卡片外观的运行时降级。列表始终使用
  原来的 LazyColumn、阴影、圆角裁剪、渐变与玻璃边框；保留不改变像素的 Brush 缓存、稳定 key /
  content type 和多语言计算缓存，避免为了流畅牺牲视觉一致性。versionCode 升至 61。

- Android `0.3.0-alpha58`：恢复 alpha56 的完整液态玻璃静态外观，包括极简风 3 dp、艺术风
  8 dp 的卡片阴影、圆角裁剪、原渐变与高光边框。性能优化改为运动感知：列表拖动或惯性滚动时
  临时使用无阴影轻量表面，停止后立即恢复原设计且不改变尺寸；alpha57 的稳定 key、content type、
  渐变缓存和多语言计算优化继续保留。versionCode 升至 60。

- Android `0.3.0-alpha57`：优化长列表快速滑动。滚动卡片不再为每一项建立独立实时阴影与
  裁剪合成层，液态玻璃渐变按主题缓存，并为节点、应用、订阅、规则和检测结果补充稳定 key /
  content type，提升 LazyColumn 复用效率。多语言动态文案的正则表达式改为进程内只编译一次，
  节点名、订阅名与应用名按原文直接绘制，避免非中文界面的无效匹配与重复翻译。四套艺术背景、
  页面结构和点击行为保持不变。versionCode 升至 59。

- Android `0.3.0-alpha56`：修复隐私观测主动检测无结果。浏览器探测 WebView 不再因首次重组
  被误销毁，改为轮询完成状态、12 秒明确超时并支持原位重试；主动检测改为独立页面，避免嵌套
  对话框失效，结果返回后保留在隐私观测中。IPv4、IPv6、出口信息、边缘出口和两项 HTTPS
  可达性由串行改为六路并行，故障网络的最长等待从约 24 秒收敛到约 4 秒。versionCode 升至 58。

- Android `0.3.0-alpha55`：撤回未发布 alpha54 中按网站硬编码 DNS、代理选择器和 QUIC 回落的
  实验，恢复统一且可预测的 DNS 配置：只按用户选择的解析模式、解析器和国内直连策略生成配置，
  不再为 ChatGPT/OpenAI 或其他单个服务暗中改写解析与路由。底部导航取消选中项的颜色渐变、
  点击涟漪和字重变化，页面立即切换且布局不跳动。保留新手模式真正停用隐藏高级规则的修复。
  versionCode 升至 57。

- macOS `0.1.0-alpha08`：默认启用 Mihomo 原生 `utun_weave` 双栈 TUN，显式安装 IPv4/IPv6
  分半路由、DNS 劫持和严格路由；加密 DoH 上游与 fake-IP 保持统一，连接前检查 TUN 接口及
  两套路由表。任一条件未就绪都会 fail-closed 拒绝连接，避免 IPv4/IPv6/DNS 静默直连；关闭
  双栈 TUN 开关后才允许本地 HTTP/HTTPS/SOCKS 代理模式。补充 3478/5349 与 19302–19309
  常见 STUN 端口阻断。此版本仍是 Apple Silicon 私有 ad-hoc 包，未伪装成签名的 Network Extension。

- macOS `0.1.0-alpha09`：macOS UI 对齐 Android 极简浅色布局。移除整页米色纹理和深色巨型状态块，
  统一为冷中性画布、浅色卡片、低对比边界和 Android 风格的连接主卡、出口选择、当前出口与状态提示；
  连接与双栈 TUN 行为不变。

- Android alpha51：针对“同一节点在 CMFA 稳定、Weave 偶发无网”收敛 CMFA 数据面路径。Mihomo
  使用 Android `system` TUN 栈并接管任意 IPv4/IPv6 DNS 53 端口；Android 10+ 核心出站只做
  `VpnService.protect()`，不再绑定易失效的 `Network` 或在换网时重建健康 TUN；自动节点连续
  3 次健康探测失败才切换，并使用轻量 connectivity probe，减少移动网络瞬时抖动导致的断网。
  新版本号为 `0.3.0-alpha51`（versionCode 53）。

- 发布边界：新增 `local-open-source` 本地发行配置、网络端点清单和 `audit-local-release.sh`；明确公开版不运营账号、云端控制、节点中继、遥测、崩溃上报或应用远程更新，并在首次 VPN 数据路径说明中展示该边界。发布文案、隐私说明、第三方清单和 PR 模板同步加入事实核对与敏感信息脱敏门槛。

- Android：稳定长连接的出站保护恢复链路。区分底层 Network 暂时不可用与原生内核出站 socket
  保护失败；发生晚到的 `protect`/`bindSocket` 失败时保留前台服务，按 0.5/1.5/3/6/12 秒
  退避重建上一份健康配置，网络恢复后继续自动重连。恢复期间始终 fail-closed，不把未保护的
  socket 放出；重试耗尽后明确显示错误并等待下一次网络变化，而不是静默直连。

- Android：新增应用内语言选择，支持简体中文、繁體中文、English、日本語、Français、Deutsch；选择
  保存在本机设置中，重启后保持。导航、设置、连接模式、订阅导入和主要对话框会立即切换语言；
  节点名、订阅名、应用名和用户输入内容保持原文。

- Android：补齐语言切换的运行时覆盖。订阅解析、二维码/文件导入、局域网互传、DNS 探测、Mihomo
  启停、VPN 恢复和 Subscription Guard 的错误与状态现在统一经过本地翻译层；带节点名、数量、HTTP
  状态码和重试次数的动态文案也会保留变量并翻译上下文，避免页面标题已切换但 Snackbar 或错误弹窗仍为中文。

- Android：修复暗色极简模式下分流应用徽标使用浅色底、浅色字导致的低可读性。暗色徽标现在
  使用与石墨面板协调的低饱和色，并使用高对比文字；浅色和四套艺术风的徽标行为保持不变。

- Android：极简浅色/深色主题收敛为低噪声配色。浅色使用单一中性画布与统一面板，深色使用两级
  石墨灰和低饱和蓝色强调；极简卡片取消彩色渐变并降低阴影、边框对比度。四套艺术风主题的
  配色、渐变与绘制保持不变。

- Android：极简风新增“深海蓝 / 石墨灰 / 夜松青”三套夜间配色，并重新整理原深色模式的
  画布、面板和强调色。浅色模式与四套艺术风保持原样；旧的“深色模式”设置仍可正常读取。

- Android：隐私与网络防旁路加固：Mihomo 默认日志级别收紧为 `error`，避免失败连接把域名/SNI
  写入 logcat；所有配置在 TUN 规则前置拒绝应用自发的明文 TCP/UDP 53、DoT/DoQ 853、已知公共
  DoH 域名和公共 DNS IPv4/IPv6，广告/家庭模式继续叠加本地过滤。Privacy Observatory 新增
  DNS 旁路证据和系统 kill switch 提醒；Always-on 与“阻止无 VPN 连接”仍由 Android 系统设置负责。

- macOS：新增 Apple Silicon 私有版 `0.1.0-alpha06`。连接成功后通过事务化快照接管当前网络服务的
  HTTP/HTTPS/SOCKS 代理并关闭 PAC，停止、Mihomo 异常退出、应用退出或下次启动会恢复原设置；完整 TUN/VPN 仍需
  经过 Apple 签名的 Network Extension。
- macOS：订阅导入与编辑统一清理 Clash 控制面字段，只保留节点 provider；支持 HTTPS 原位刷新、
  编辑替换和严格的节点重新解析。局域网服务只绑定私有 IPv4，并严格校验请求、响应头、长度和
  AES-256-GCM 密文，适合仅限朋友的 ad-hoc 分发。

- Android：隐私与网络安全加固：节点元数据和自定义 DNS 端点改用 Keystore AES-GCM 保存；Clash
  导入在进入 Mihomo 前只保留节点 provider，丢弃控制器、监听端口、TUN、脚本、远程规则、DNS
  和 proxy-groups；局域网互传改为绑定当前私有 IPv4，并严格校验请求行、响应类型、长度和 AEAD。
- Android：恢复中心与 DNS 探测不再持久化或展示异常原文，避免节点主机、SNI、URL 和凭据进入日志、
  错误记录或诊断面；网络安全配置显式只信任系统 CA 并拒绝明文流量。

- Android：新增 DNS 端点可用性与延迟检测。DoH 仅发送 HEAD，DoT 仅完成 TLS 握手，不发送域名查询；结果按真实可达端点展示，失败不会回填无效延迟。
- Android：修复国内直连在 fake-IP 下被误判为默认代理的问题。国内/私有域名现在返回真实地址，增加
  `GEOSITE,private`、`GEOIP,LAN` 直连规则，并启用本地 DNS 映射与 TLS/HTTP/QUIC 主机识别；应用级出口
  仍优先于国内直连，Chrome 等显式分流应用不会被 CN 规则悄悄改写。
- Android：新增 DNS 解析策略“统一解析 / 国内·海外分流”，通过 Mihomo `nameserver-policy` 将 `geosite:cn` 与海外域名交给不同加密上游；自定义、广告和家庭过滤配置会保持原有过滤语义。
- Android：自动节点策略新增“跨订阅自动”范围，把当前运行配置中的可用订阅合并为一个 `url-test`、`fallback` 或 `load-balance` 组；应用分流规则在该范围下统一命中跨订阅组。

- Android：深色极简模式改为深靛蓝/石墨三层色阶，修正浅色主色与浅色文字对撞，并降低玻璃面板亮边；DNS 新增阿里、腾讯、Cloudflare、Google、Quad9 与 Mullvad 加密上游，可分别选择 DoH 或 DoT。

- Android：修复部分 vivo/OriginOS 将未压缩 `.so` 保留在 APK 内、但 `nativeLibraryDir` 没有实体文件时被误报为“无法加载 Mihomo”；安装检测现在校验 base/ABI split APK 的原生库条目，加载时先解析 `libclash.so`，并为厂商 linker namespace 增加绝对路径兜底。

- Android：外观选择改为“极简风 / 艺术风”两级分组；极简风提供固定浅色和深色模式，艺术风保留四套莫奈主题。无历史外观设置的新安装默认使用极简浅色；已有艺术主题不会被覆盖。极简模式关闭装饰性渐变，减少视觉噪声和 GPU 绘制。

- Android：alpha39 修复部分国内 Wi‑Fi/蜂窝网络未及时获得 Android `VALIDATED` 能力时的启动失败；底层网络现在优先已验证网络，并安全回退到非 VPN 的可用物理网络。国内智能直连默认开启，CN 域名使用独立加密解析策略；IP 质量检测增加 DNS、IPv6/WebRTC 和综合 IP 的外部浏览器复核入口。

- Android：加入按需 IP 质量检测：通过固定 HTTPS 端点读取当前 IPv4/IPv6 出口、地区、ASN、ISP、边缘节点、代理/VPN/Tor/托管标签和多端点 RTT；结果只在内存中展示，DNS 泄漏和 WebRTC 明确保持“未测试”。
- Android：第三阶段加入本地域名、域名后缀、关键词和 IPv4/IPv6 CIDR 规则；规则使用 Android Keystore 加密保存，严格校验后编译进 Mihomo，应用规则优先，支持启停和删除。
- Android：Route Lens 现在可以解释本地规则命中（含域名/IP 输入），并正确区分规则模式、全局模式和直连模式；订阅页增加 HTTPS 远程订阅逐项手动刷新、进度和失败状态，不启用常驻后台任务。
- Android：第二阶段质量矩阵已接入订阅详情，使用三轮真实健康探测的中位延迟、P95、抖动、丢包和成功样本数做可解释排序；未探测字段保持未知。
- Android：新增离线策略包 `weave-policy/v1`，支持 SHA-256、可选 Ed25519 签名、受限规则类型、Keystore 加密存储和热重载；无签名包会显式标记为需复核。
- Android：局域网互传增加按订阅选择、6 位带外短码和安全同源合并；macOS/iOS 同步加入 6 位带外短码校验；继续使用 WVLAN001 兼容线协议，旧版跨端客户端仍可接收，Android 同名同源订阅更新前运行 Subscription Guard。

- Android：第一阶段安全能力落地。分流页新增本地 Route Lens 路由解释器；订阅原位更新新增
  Subscription Guard，空内容与节点数量灾变式变化会在加密存储提交前阻断并保留旧版本；
  设置页新增 Privacy Observatory 与 Recovery Vault，分别提供证据标注的隐私检查和候选/回滚失败后的
  安全模式恢复。以上功能不执行隐蔽联网测试、不保存明文凭据，也不生成虚假安全百分比。
- Android：原生桥接改为按需加载，应用启动、设置和订阅页面不再提前映射 Mihomo
  核心；后台/非首页时暂停运行时遥测轮询，可见首页的遥测间隔调整为 3 秒，并缓存
  渐变绘制资源，降低空闲耗电与内存峰值。
- Android：移除 Google Play services code scanner，二维码相机和图片识别统一改为本机
  系统相机预览 + ZXing；生成二维码改用 512px RGB565，图片解码限制为 1536px，减少
  临时 Bitmap 内存和依赖体积。

- Android：修复首次节点测速把 Mihomo 未初始化/失败哨兵显示为 `65535/65553 ms`；延迟值在核心边界统一限制为 1–10000 ms，三轮聚合把异常值计作失败，并对首轮全无效状态做最多 150 ms 的有界等待。
- Android：代理模式移除隐式直连兜底；失效或已删除的默认订阅不再自动授权物理网络直连，连接中删除最后一个代理会立即关闭旧内核。VPN 出站 socket 绑定到已验证的非 VPN 底层网络，Wi-Fi/蜂窝切换同步更新，底层网络全失时显式进入无上游状态。
- Android：广告/家庭 DNS 模式增加 DoT/DoQ 853 端口和常见公共 DNS IPv6 地址防绕过；HTTPS 订阅每次请求及重定向前检查 DNS 结果，拒绝回环、私网、链路本地、CGNAT 与组播目标；订阅编辑/导入/局域网互传界面阻止截图与最近任务预览，一次性链接按敏感剪贴板处理并在 60 秒后清除。
- iOS：新增 iOS 17+ 原生 SwiftUI 客户端初稿，包含 Liquid Glass × 莫奈四主题、连接/域名分流/订阅/设置四页、订阅与节点条件选择、二维码/照片/文件/HTTPS 导入和跨端局域网互传。
- iOS：新增 Keychain 主密钥保护的 AES-256-GCM 订阅库、事务化 App Group 运行配置、SHA-256 清单校验、IPv4-only 系统路由贯通和独立 `NEPacketTunnelProvider` target。
- iOS：移动内核通过窄 `WeaveMihomoMobile.xcframework` 契约隔离；framework 缺失、配置损坏或签名能力不足时 fail closed，不提供虚假连接状态。个人 iOS 版分流按域名工作，按应用 VPN 明确保留为 MDM 能力。
- Android：根据 Pixel 8 / Android 17 真机视觉复核重建为 Liquid Glass × 莫奈视觉系统：恢复清晰的无衬线字阶，以蓝紫、睡莲青和珊瑚柔光构成连续背景，并用冷暖折射渐变、白色高光边缘和悬浮阴影统一首页、分流、订阅、设置与底栏。
- Android：移除会让界面发脏的纸张纹理、伪笔触和半透明白灰叠层；针对 Android 17 的透明 RenderLayer 矩形残影，改用全不透明色彩折射模拟玻璃，保留玻璃感同时避免卡片内容区域出现白块。
- Android：新增 4 套低饱和莫奈风艺术主题（“日出·印象”“睡莲”“罂粟田”“暮色花园”），设置中可切换并持久化；画布、纸张、浮层、描边和状态色会随主题成组变化，避免白色与浅灰模块混用。
- Android / macOS：内部 UI 与新版编织结图标统一为暖象牙、深靛蓝、海玻璃青绿、淡紫和珊瑚配色；Android 面板增加轻微透纸层次与柔和背景渐变，macOS 同步降低分割线和纯白块的突兀感。
- Android / macOS：应用图标改为“编织结”艺术标记，使用深靛蓝、雾青、淡紫和珊瑚节点的柔和印象派配色，去除直白的 W/V、斜线和荧光色；Android 保留单色主题回退轮廓。
- Android：订阅导入统一支持 HTTPS、粘贴 URI/Base64、Clash YAML、sing-box JSON、基础 V2Ray JSON、二维码和文件；URI/JSON 会在本机转换为 Mihomo provider，复杂或缺字段协议 fail-closed。
- Android：手动节点测速继续保持按订阅懒加载，新增 SOCKS5/HTTP/SSR/AnyTLS 基础 URI 字段、V2Ray VMess/VLESS/Trojan/SS 基础 JSON 转换，并为旧版本记录增加运行时兼容转换。
- Docs：同步更新格式边界、能力矩阵和开源路线图，避免把尚未接通的入口标为已实现。

## 0.3.0-alpha31 — 2026-08-12

- Android / macOS：品牌图标改为非字母的“日蚀轨道”符号，由开放光环、酸橙轨道和单一节点组成，减少视觉噪声并提升小尺寸识别度。

## 0.3.0-alpha30 — 2026-08-12

- Android / macOS：再次重做品牌标记，采用上下交叠的织环弧线，去除直白的 W 轮廓和多余装饰，适配小尺寸启动器图标。

## 0.3.0-alpha29 — 2026-08-12

- Android：重做浅色主题的描边和分隔线，改用暖灰、半像素和内缩留白，减少黑线带来的突兀感。
- Android / macOS：将直白的编织 W 标记改为两条交叠丝带组成的抽象网络结，统一启动图标和侧边栏品牌标记。

## 0.3.0-alpha28 — 2026-08-12

- Android：广告过滤和家庭过滤现在会在 AdGuard DoH/DoT 之外，使用内置广告、跟踪器和成人域名规则；规则位于分流链最前面，能拦截浏览器和应用发起的域名连接。
- Android：fake-IP 对局域网、`.local` 和 `home.arpa` 使用真实地址，减少局域网服务被错误映射造成的卡顿。
- Android：设置页明确显示“DNS + 本地规则”，避免把 DNS 过滤误解成浏览器级元素隐藏。

## 0.3.0-alpha27 — 2026-08-12

### Fixed

- Filtering DNS profiles now block common browser Secure DNS endpoints so Chrome/Firefox cannot
  silently bypass the selected AdGuard resolver over HTTPS.
- DNS and fake-IP state is cleared when a runtime profile is reloaded, so changing from privacy DNS
  to ad/family filtering takes effect immediately instead of retaining old cached mappings.

## 0.3.0-alpha26 — 2026-08-12

### Changed

- Removed the universal Android APK from normal ABI builds; release output is now split per CPU architecture.
- Replaced bundled ML Kit image QR decoding with ZXing to reduce package size and offline model overhead.
- Flattened the Android paper surface, softened borders/accent contrast, and reduced bottom navigation/card elevation.
- Lazy-loaded the installed-app catalog and reduced visible runtime polling from 1 second to 2 seconds, while
  retaining the session timer and avoiding unchanged dashboard emissions.

## 0.3.0-alpha25 — 2026-08-12

### Added

- Versioned, affirmative VPN data-path disclosure before first connection.
- In-app security/privacy, routing, LAN transfer, and open-source component details.
- GitHub contribution, security, privacy, CI, dependency update, and release files.
- Adaptive woven-ribbon application icon shared by Android and macOS artwork.

### Security

- VPN runtime failures no longer expose arbitrary native exception text to logs or UI.

## 0.3.0-alpha24 — 2026-08-12

- Improved node availability testing, latency ordering, and retained prior results.
- Refined the warm mineral visual system.
- Verified Android 17 arm64 installation and startup.
