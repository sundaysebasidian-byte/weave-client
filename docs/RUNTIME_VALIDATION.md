# Android 运行验证

## 2026-08-16：出站保护长连接恢复（alpha50，待真机长时回归）

- 原生内核晚到的 `VpnService.protect()` 或物理 Network 绑定失败不再直接关闭前台 VPN；服务会
  保留 fail-closed 状态，按 0.5/1.5/3/6/12 秒退避重建上一份健康运行时。
- 无可用 Wi‑Fi/移动网络时不启动新 TUN；底层网络回调重新出现后再触发恢复。重试耗尽会显示
  明确错误并等待下一次网络变化，不会回退到未保护的物理网络。
- 本轮需要 Pixel / Android 17 真机验证：Wi‑Fi/蜂窝切换、锁屏 12 小时、DHCP/IPv6 更新、
  Always-on 与系统省电策略；本地单元测试、Lint 和 ARM64 APK 构建已通过，当前 ADB 无设备，
  尚未覆盖安装或完成长时真机回归。

## 2026-08-15：应用内多语言（alpha49，待真机排版回归）

- 设置新增简体中文、繁體中文、English、日本語、Français、Deutsch 六个应用内语言选项，选择保存
  在 `runtime_settings_v1`，重启后恢复。
- Compose 稳定文案在渲染边界本地化；节点名、订阅名、应用名、URL 和用户输入错误原文不翻译，避免
  改写外部数据。长法语/德语、日语字体和二维码导入页面仍需在 Pixel / Android 17 真机检查换行。
- 单元测试、Android Lint、ARM64 debug APK 已通过；当前 ADB 无设备，尚未覆盖安装。

## 2026-08-15：隐私与 DNS 旁路加固（alpha46，待真机覆盖）

- Mihomo 配置默认 `log-level: error`，避免 warning 级别将失败连接的域名/SNI 写入 logcat；Weave
  自身恢复记录继续只保存 allowlist 错误类别。
- 所有配置的前置规则拒绝应用自发的 TCP/UDP 53、TCP/UDP 853、已知公共 DoH 域名和公共 DNS
  IPv4/IPv6；过滤配置额外保留广告/家庭域名拒绝。核心自己的加密 DNS socket 仍通过
  `VpnService.protect()` 走已验证物理网络。
- Privacy Observatory 新增“DNS 旁路拒绝”和“系统断网保护”两项证据。后者必须在 Android VPN
  设置中手动打开 Always-on 与“阻止无 VPN 连接”，应用无法代替系统开关。
- 单元测试已通过；尚未覆盖 Pixel / Android 17 的真实明文 DNS、浏览器 Secure DNS、断网和
  system always-on 矩阵，不能把静态规则结论当作外部泄漏测试。

## 2026-08-15：移动网络稳定性增强（alpha45，待真机覆盖）

- 自动节点组健康探测从 180 秒 / 连续失败 2 次调整为最低延迟 60 秒、故障切换 45 秒，
  单次失败即可剔除异常节点；5 秒探测超时保持不变，避免坏节点长时间拖住应用内网页。
- 选定的加密 DNS 仍保持首选，但阿里/腾讯兼容解析器同时加入 Mihomo 的根 `nameserver`，
  覆盖 TXT/PTR 等不会稳定触发 `fallback-filter` 的查询类型，降低中国大陆网络下的解析抖动。
- 版本号升至 `0.3.0-alpha45`（versionCode 47）。需要在 Pixel / Android 17 真机重新连接后，
  观察节点自动切换、Wi‑Fi/蜂窝切换和 Binance 内页连续加载，不能用单元测试替代数据面结论。

## 2026-08-15：海外 DNS 回退与 Binance 内页真机验证

- Pixel 现场日志确认 `com.binance.dev` 的 UID 10409 已命中 `DIRECT`，失败点是
  `api.saasexch.info` 解析；用户启用的 AdGuard DoH 在当前中国电信 Wi‑Fi 上连续超时。
- 过滤 DNS 和 Cloudflare/Google/Quad9/Mullvad 等海外 DNS 现保留用户选择的上游为首选，并加入阿里/腾讯加密 DoH/DoT 回退；Mihomo 的
  `fallback-filter` 仅使用随 APK 提供的 CN GeoIP 数据（不依赖未打包的 `gfw` GeoSite 集合），
  应用内本地广告/家庭域名拒绝规则不因回退而关闭。
- `0.3.0-alpha44`（versionCode 46，`arm64-v8a`）已覆盖安装到 Pixel / Android 17，
  并在断开后重新连接 Weave；运行时配置更新时间为 `2026-08-15 00:18:33`，实际包含
  `fallback: doh.pub + dns.alidns.com`。
- 真机复测确认：Binance 设为直连时，应用外壳可打开但内页会被大陆网络阻断；切换为
  `tokyo-gcp` 订阅的自动出口后，Binance 主界面与“学院”内页均能加载，流量命中
  `GCP-Tokyo-HY2`，未出现 DNS 解析失败或 TCP 超时。美国 LAX 出口会触发 Binance
  的美国 IP 提示，因此不作为 Binance 的默认出口。

## 2026-08-14：应用直连 WebView/QUIC 规则修复（待真机覆盖安装）

- 复核 Binance “应用外壳可打开、内置网页打不开”的规则链后，确认每条 UID 应用规则
  末尾原先都附带 UDP `REJECT` 守卫；这会误伤明确选择直连的应用内 WebView/HTTP3。
- 现仅对自动策略和固定代理节点保留 UDP 防泄漏守卫：当代理不支持 UDP 时继续匹配并
  命中 `REJECT`，不会落到默认出口；`DIRECT` 由 Mihomo 的原生 direct 出站承载 TCP、UDP
  和 QUIC，`BLOCK` 直接由应用目标拒绝。
- 已增加 JVM 回归测试，覆盖 Binance 类 direct UID 规则不再生成额外 UDP 拒绝，同时保留
  代理 UDP 失败关闭行为。本轮需要重新构建并在 Android 17 真机验证 Binance 内置网页。

## 2026-08-13：alpha34 轻量化改动（待重新构建）

- 原生桥接改为按需加载；界面只检查 split APK 是否包含 `libbridge.so` 与 `libclash.so`，
  真正的 `System.loadLibrary` 延后到 VPN 配置校验边界。
- 首页运行遥测在非首页或 Activity 非 RESUMED 时停止，可见首页间隔为 3 秒；莫奈背景渐变
  使用 `drawWithCache`，二维码识别改为系统相机预览 + 本机 ZXing，并限制 Bitmap 解码尺寸。
- 本轮代码与依赖锁已完成静态检查，但当前执行环境的提权构建额度已用尽，尚未重新运行
  JVM 单测、Lint 和 Release 构建；下面历史记录中的通过结果不代表本轮新包已生成。

## 2026-08-13：alpha34 测速哨兵与网络隐私加固（待真机）

- 延迟在原生查询边界统一限制为 `1..10000 ms`，`65535/65553` 等未初始化或失败值不再进入
  首页、节点列表和三轮质量聚合；首轮全无效时最多轮询 150 ms 等待核心历史状态稳定。
- 代理模式 `DEFAULT` 组不再附加 `WEAVE-DIRECT` 作为隐式备选；默认订阅失效、删除或已无
  可用订阅时保持断开，只有用户明确选择直连才生成直连默认出口；连接中删除最后一个代理
  会立即停止旧运行时，不通过事务回滚继续使用已删除凭据。
- VPN 仅接受 `VALIDATED + NOT_VPN` 底层网络，Mihomo 出站 fd 在 protect 后绑定到首选物理
  网络；Wi-Fi/蜂窝切换同步更新，全部丢失时调用空 `underlyingNetworks` 保持无上游。
- 广告/家庭过滤增加 TCP/UDP 853 与常见公共 DNS IPv6 地址拦截；远程订阅在每个 HTTPS hop
  前验证 DNS 答案，拒绝本地/私网/CGNAT/组播目标；敏感导入/编辑/互传界面阻止截图，
  一次性互传链接 60 秒后清除剪贴板。
- JVM 单元测试、Android Lint、四 ABI debug APK 构建及锁定 Mihomo 配置解析已通过。Pixel 8 /
  Android 17 的真实首次测速、断网与 Wi-Fi/蜂窝切换仍待验证，不能用构建结果替代数据面结论。
- 最终 ARM64 debug APK 为 `0.3.0-alpha34`（versionCode 36），v2 签名验证通过，SHA-256：
  `71771af0a7aa65ee22b834dd64abc5125c7d50b1452cff8f90571e59f38a3175`；本轮 ADB 未发现设备，
  因此未声称已覆盖安装。

## 2026-08-02：alpha22 Pixel × Liquid Glass 视觉系统

- 四个主页面统一为克制的 Pixel 信息层级与轻量玻璃材质：浮动胶囊底栏、珍珠雾灰色系、
  低阴影珍珠 Surface、中文副标题和统一圆角/图标容器。
- 没有使用实时背景模糊；玻璃感由同色雾面材质、边界高光与少量阴影构成，避免增加滚动重绘
  和低端设备 GPU 压力。Pixel 8 首轮真机截图发现 Android 17 会把半透明 Surface 的子布局
  二次叠亮；最终使用完全不透明的同色珍珠面消除矩形色块。
- 过滤 `65535 ms` 等无效节点延迟，测速结果超出 1–10000 ms 时显示“— / 等待测速”。
- 61 项 JVM 单元测试、Android Lint、四 ABI 与 universal debug APK 均通过；ARM64 APK
  SHA-256 为 `e32994f0ee711b32abb8f38fe0dcdc3b51a00315dd67a6a4e3b016bd7a6d2d3b`。
- `adb install -r` 已成功覆盖安装到 Pixel 8，系统报告 `versionCode=24`、
  `versionName=0.3.0-alpha22`、主 ABI `arm64-v8a`，订阅和规则数据保留；手机端 base APK
  与本地构建哈希完全一致。
- 首页、订阅页与设置页均已在 Pixel 8 / Android 17 真机截图复核：颜色一致、无矩形合成
  色块、底栏未遮挡交互内容。临时 USB 保持亮屏设置已在截图完成后恢复。

## 2026-08-02：alpha19 VPN 槽位冲突提示与 Android 14+ 前台服务修正

- Android 14+ 启动 VPN 前台服务时显式声明 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`，与清单
  中的服务类型保持一致。
- Pixel VPN 或其他 VPN 抢占 Android 唯一 VPN 槽位并触发 `onRevoke()` 时，界面现在直接
  提示关闭冲突 VPN 后重试，不再笼统显示为节点或授权故障。
- 61 项 JVM 单元测试全部通过；Android Lint 完成且无 error（保留 8 项 warning）；
  arm64-v8a、armeabi-v7a、x86、x86_64 与 universal debug APK 均成功生成。
- ARM64 APK SHA-256 为
  `0b1f730a6c18c5cbbcbdbe6939f85662a225a3e8392204190b7fb13cd7a77d60`，已用
  `adb install -r` 覆盖安装到 Pixel 8 / Android 17。系统报告 `versionCode=21`、
  `versionName=0.3.0-alpha19`、主 ABI `arm64-v8a`，原应用首次安装日期与数据目录保留。
- 真机冷启动返回 `Status: ok` / `LaunchState: COLD`，应用进程保持运行。本轮没有代替用户
  启动 VPN；Pixel VPN 仍启用时会占用 Android 唯一 VPN 槽位，使用 Weave 前需先手动关闭。

## 2026-07-30：alpha18 订阅原子更新、CN 智能路由与换网恢复（待真机）

- 2026-08-02 复核发现 Pixel VPN (`com.google.android.apps.privacy.wildlife`) 开启时会重新
  抢占 Android 唯一 VPN 槽位并触发 Weave `onRevoke()`；临时停用 Pixel VPN 后，
  Weave 在 Pixel 8 / Android 17 保持“已保护 / 连接安全”。这是系统 VPN 槽位冲突，
  不是节点或 Mihomo 崩溃。
- Android 14+ 前台服务启动已显式传入
  `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`。同一 ARM64 APK 在 2026-08-02 再次以 `install -r`
  覆盖安装，手机端与本地包 SHA-256 完全一致，订阅数据保留。
- 订阅原位替换不再覆盖唯一 payload：新版本先加密落盘，再与节点元数据一起提交，成功后
  才删除旧版本；节点重排保留稳定 ID，完成后提示新增、移除、保留及可能重复项。
- 增加默认开启的国内智能直连。APK 内置固定来源与 SHA-256 的 lite GeoSite/GeoIP 数据，启动时
  校验后原子安装；CN 域名加入 fake-IP 例外并返回真实地址，使 `GEOIP,CN` 能覆盖 IP-only/QUIC
  流量；应用规则位于 CN 规则之前，因此 Chrome 等应用的显式出口不会被覆盖；CN direct 同时使用
  独立加密解析策略。
- `ConnectivityManager` 优先监听已验证的非 VPN 底层网络；若 Android 尚未及时提供
  `VALIDATED`，会安全回退到具备 INTERNET/NOT_VPN 能力的物理网络，过滤重复回调；Wi‑Fi/蜂窝变更
  1.5 秒去抖后走事务重启与回滚路径，无网络时等待恢复。
- 首页流量、节点和应用归属指标只在连接页可见时轮询；连接时长改用单调时钟计算，离开首页
  不再每秒更新整个根 Compose 状态。
- 61 项 JVM 单元测试、Android Lint、四 ABI 与 universal debug APK，以及包含真实 Geo
  数据的锁定 Mihomo YAML 解析均通过。ARM64 APK 已覆盖安装到 Pixel 8 / Android 17，
  系统报告 `versionCode=20`、`versionName=0.3.0-alpha18`，冷启动首页显示 `Mihomo ready`。
- 本轮没有代替用户启动真实 VPN 或切换手机网络，因此订阅更新回滚、CN 实际命中与
  Wi‑Fi/蜂窝自动恢复仍标记为真机待测，不把构建和冷启动结果外推为数据面结论。

## 2026-07-30：alpha17 事务回退、DNS 引导收敛与真实节点健康（待真机）

- CMFA 补丁新增 `validateConfiguration`：执行完整配置和 provider 解析但不调用
  `hub.ApplyConfig`。Android 候选配置验证因此不会替换正在工作的旧内核状态。
- 重载期间在应用私有 cache 创建候选与回滚快照；候选校验失败时不停止旧 TUN，候选启动
  失败时恢复旧配置、旧 provider 和旧应用映射。成功、失败、服务销毁都会清理明文快照。
- JNI 接入 CMFA 原生 `healthCheck` 与 `queryGroup`，订阅详情显示内核记录的真实节点延迟；
  同时补上游健康检查完成后遗漏的跨语言对象释放。
- DNS 增加 `proxy-server-nameserver`，使节点服务器域名使用所选 DoH/DoT。保留的明文
  `default-nameserver` 只用于引导加密 DNS 上游域名；没有把已被服务商建议弃用的固定
  DNSPod DoH/DoT IP 伪装成长期安全方案。
- 55 项 JVM 单元测试、Android Lint、四 ABI debug 构建及锁定 Mihomo YAML 解析通过。
  四个 `libclash.so` 均由锁定源码、Go 1.26.5 和 `GOPROXY=off` 重新生成，ARM64 导出符号
  已确认包含 `validateConfiguration`、`healthCheck` 和 `queryGroup`。
- ADB 仍未检测到设备；事务启动失败回退、真实 provider 测速和 DNS 流量边界尚待 Pixel 8
  / Android 17 覆盖安装验证，不能以静态构建结论代替。

## 2026-07-30：alpha16 可审计网络策略（待真机覆盖安装）

- 新增最低延迟、故障切换与一致性哈希负载均衡三种订阅自动策略；设置状态持久化并进入
  Mihomo 配置组装，连接中使用既有的停止、清理、重新校验与重建 TUN 流程。
- 新增 DoH / DoT、双栈 / 仅 IPv4，以及可选 UDP STUN 阻断。仅 IPv4 不移除 Android
  `::/0` TUN 捕获，而是在关闭内核和 DNS IPv6 后以 `IP-CIDR6` 拒绝，避免直连泄漏。
- 52 项 JVM 单元测试、Android Lint 和 armeabi-v7a / arm64-v8a / x86 / x86_64 四 ABI
  debug 构建通过。锁定 commit `e26714a` 的 Mihomo arm64 二进制也已实际解析包含
  `url-test`、`fallback`、`load-balance`、DoT、IPv6 拒绝和 STUN AND 规则的 smoke
  配置。
- 本轮检查时 ADB 没有连接设备，因此尚未记录 alpha16 的 Pixel 8 覆盖安装和真实热重载；
  alpha15 的数据面验证结果不外推为 alpha16 真机结论。

## 2026-07-29：alpha15 UDP 重试归属保持

- alpha14 现场日志确认 Chrome 首次 QUIC 已以 UID 10200 命中 `REJECT`，TCP 同时正确命中
  `CF官方优选11`；但 Chrome 从同一 UDP 本地 socket 重试时，Android
  `getConnectionOwnerUid()` 随即返回 `-1`，第二次请求仍可能回落到 `DMIT-LAX-HY2`。
- alpha15 增加仅存在于 VPN 服务内存中的短期 UID 归属缓存：以协议和 TUN 本地 socket
  为键，10 秒过期、最多 2048 项，停止服务或重载时清空。它只保留 UID，不记录目标域名、
  IP、订阅或连接内容。
- 新增立即重试、过期、同一 UDP socket 多目标和主动清空测试；alpha15 共 46 项单元测试
  通过，Lint、四 ABI APK 构建及 ARM64 APK v2 签名验证通过。
- alpha15 已以保留数据方式安装到 Pixel 8。Chrome 访问 IP 检测页时，`ippure.com`、
  `icanhazip`、`ipinfo`、`ipapi` 等 TCP 请求均以 UID 10200 命中
  `CF官方优选11`；同一 UDP socket 对多个 STUN 目标的连续重试始终保留 UID 10200 并命中
  `REJECT`，未再观察到这些检测请求回落 `DMIT-LAX-HY2`。
- Android 将 Weave VPN 标记为 `VALIDATED`；IP 检测页的地区字段显示日本东京，未在验证
  记录中保存或输出公网 IP。

## 2026-07-29：alpha14 Chrome UDP 防泄漏

- alpha13 真机日志确认补丁已让 `Metadata.Uid` 在 TCP 和 UDP 上都得到 UID 10200；但
  `CF官方优选11` 不支持 UDP，Mihomo 在 UID 规则命中后继续寻找可承载 UDP 的规则，最终
  回落到 `MATCH,DEFAULT` 的 `DMIT-LAX-HY2`。
- alpha14 在每条 Android UID 应用规则后增加 UDP 防泄漏守卫。所选出口支持 UDP 时仍由
  原规则处理；不支持时命中 `REJECT`，让 Chrome 的 QUIC/HTTP3 快速失败并改用同一应用
  出口上的 TCP，禁止借默认出口发送。
- 42 项单元测试、Lint、四 ABI APK 构建和 ARM64 APK v2 签名验证通过；alpha14 已以保留
  数据方式安装到 Pixel 8，VPN 为 `VALIDATED`。
- 真机日志证实防泄漏守卫会拦截第一轮 Chrome QUIC，但 Android 在后续重试中失去 UID，
  因而 alpha14 仍未完全消除默认出口回落；后续由 alpha15 处理。

## 2026-07-29：alpha13 CMFA UID 元数据修复

- 锁定版 CMFA 的 Android 回调能查到 UID 与包名，却只把包名写进路由元数据，导致 Mihomo
  `UID` 规则始终读到 0。Weave 以单文件可审计补丁写入 `Metadata.Uid`，重建四 ABI 原生
  内核并记录补丁与产物 SHA-256。
- Pixel 8 日志确认微信 TCP 从 `MATCH,DEFAULT` 改为
  `Uid(10381) using DIRECT`，Chrome TCP 改为
  `Uid(10200) using ... CF官方优选11`；VPN 同时保持 `VALIDATED`。

## 2026-07-29：alpha12 Chrome UDP/QUIC 分流

- Pixel 8 现场日志确认 Chrome TCP 已按 `PROCESS-NAME` 命中 `CF官方优选11`，但 Chrome
  QUIC/HTTP3 的 UDP 连接虽已归属到 UID 10200 / `com.android.chrome`，仍回落
  `MATCH,DEFAULT` 并使用主出口 `DMIT-LAX-HY2`。
- alpha12 首次让应用规则优先生成 `UID`，但现场验证表明 CMFA 未把查询结果写入
  `Metadata.Uid`，因此该版本没有解决 UDP/QUIC 分流。

## 2026-07-29：alpha11 启动连接修复

- Pixel 8 / Android 17 现场日志确认旧版启动时会立即健康检查所有已导入 provider 的全部
  节点；大型 OpenVPN 等订阅触发数百个并发 DNS、TCP 和 UDP 探测，导致当前固定节点虽已
  建立 TUN，真实应用连接仍大量超时。
- 运行配置现只写入默认出口与应用规则真正引用的订阅；未使用订阅不再解密到运行目录，
  provider 不再独立全量健康检查，自动策略组改为按需懒检查。
- 国内网络不可达的 `1.1.1.1` DNS/DoH 已替换为 `119.29.29.29` 与腾讯 DoH，并保留阿里
  DNS/DoH 作为双上游。
- alpha11 共 40 项单元测试通过，Lint、四 ABI 与通用 debug APK 构建通过；ARM64 APK v2
  签名验证通过，并以保留数据方式覆盖安装到 Pixel 8。
- 真机重启 VPN 后运行配置只加载默认固定节点和一条应用固定节点规则实际引用的 2 个
  provider，启动日志中的 `Health Checking / Health Checked` 从数百条降为 0；Android
  将 VPN 标记为 `VALIDATED`，海外 `www.gstatic.com:443` 与国内 `www.baidu.com:443`
  均通过当前 `DMIT-LAX-HY2` 默认出口建立 TCP 连接。
- 真机还检测到并非 Weave 写入的全局代理残留 `127.0.0.1:8888`。经用户明确授权后已清除
  主机、端口、排除列表和 PAC；Android HTTP/HTTPS 网络探测均重新返回 204，Wi-Fi 与
  Weave VPN 同时恢复 `VALIDATED`。

## 2026-07-29：alpha10 Android/macOS 局域网互传

- Android 新增订阅页局域网互传入口，可导出全部订阅为一次性二维码/链接，也可粘贴链接或
  使用系统相机预览 + ZXing 本机扫描导入；成功读取一次或 5 分钟后发送端立即停止。
- Android 和 macOS 对同一中文订阅编码得到固定 SHA-256
  `3762f88e5dbbb4598b84219faf58fcbf7620607c08c51df1a218e9fb039040c1`，
  AES-256-GCM、链接解析、篡改拒绝与公网地址拒绝自测通过。
- Android `0.3.0-alpha10` 共 36 项单元测试，Lint 和四 ABI APK 构建通过。并行任务中
  `packageDebug` 曾一次无详细原因失败，单独复跑成功，记录为待观察的增量分包瞬态问题。
- Pixel 8 / Android 17 后续重新接入 ADB，已保留数据覆盖安装
  `0.3.0-alpha10`（versionCode 12）。真机无障碍树确认订阅页的“局域网互传”和“添加订阅”
  入口存在，互传对话框的“从链接导入”和“扫描二维码”均可操作；验收未生成导出码，也未
  读取或输出用户订阅内容。
- macOS `0.1.0-alpha04` 以 SwiftUI 构建为 arm64 App；内置 Mihomo 为 arm64，
  SHA-256 `0cf93bb94fdb322e91120b8c6b67e35997a0962ba7cea7fe1f988a1c7060ee1a`，
  配置 smoke test 与 ad-hoc `codesign --verify --deep --strict` 均通过。
- macOS 四个页面已改用与 Android 相同的 Ink/Acid/Canvas/Paper/Good 色板、圆角卡片、状态
  标签和主操作层级；导航保留桌面端侧栏，避免机械复制手机底栏。
- alpha02 把节点 YAML 解析、Mihomo 运行目录生成以及互传编解码/加密移出主线程，节点结果
  按订阅缓存；实际运行连续切换四页均成功。应用图标改为与 Android 一致的荧光 V，侧栏四
  枚导航图标改为自绘路径，不再直接沿用 SF Symbols 外观。
- alpha03 的 8 秒交互采样中主线程约 98% 等待事件，没有业务阻塞堆栈；剩余响应感问题改为
  固定 HStack 双栏、关闭隐式页面转场，并以可搜索 `LazyVStack` 弹层替换同步构建完整菜单
  的 `Picker`。空订阅弹层从自动化点击到完整 AX 树出现为 293 ms，其中工具自身点击等待约
  250 ms。
- alpha04 将连接、订阅、互传、设置四页改为常驻视图，侧栏点击只改变可见性，不再销毁并
  重建整页；订阅页新增真实的 HTTPS / Weave 链接、二维码图片和 Clash/Mihomo YAML 文件
  导入入口，导入内容须通过大小、来源与有效节点校验后才写入本机加密库。
- macOS 当前只验证本地代理控制面和构建产物；完整 VPN 未获 Network Extension entitlement，
  不作为已实现能力。

## 2026-07-29：alpha09 订阅删除

- 订阅详情底部新增红色“删除订阅”，二次确认会显示节点数量、受影响应用规则数量、默认
  出口回退行为和“无法撤销”警告。
- 删除时先把加密 payload 原子移动为不可达 tombstone，再移除加密 URL、节点元数据和订阅
  索引；元数据提交失败时恢复 payload，残留 tombstone 会在下次初始化时清理。
- 引用被删订阅的应用规则同步移除，使应用重新继承默认出口；默认出口引用被删订阅时优先
  切到剩余订阅的自动策略，没有其他订阅时保持断开，不静默回落直连。
- 新增 3 项引用协调测试；`0.3.0-alpha09` 共 33 项单元测试，Android Lint、四 ABI 构建
  和 APK v2 签名验证通过。
- alpha09 已覆盖安装到 Pixel 8 / Android 17，versionCode 11；真机确认删除入口和二次
  确认可点击，验收时取消最终删除，用户现有订阅保持不变。

## 2026-07-29：alpha08 Clash 订阅兼容

- 远程订阅请求使用包含 `ClashMetaForAndroid` 的 CMFA/Mihomo 兼容标识，并声明接受 YAML，
  降低自适应订阅服务误返回通用 Base64 的概率。
- 解析器新增 Clash flow-style 节点支持，包括多行 `- {name: ..., type: ...}` 和整行
  `proxies: [{...}, {...}]`，且能跳过传输配置中的嵌套 map、逗号与冒号。
- 订阅地址误返回 HTML 落地页时给出明确提示，不再笼统显示无有效节点。
- 新增 3 项回归测试；`0.3.0-alpha08` 共 30 项单元测试，Android Lint、四 ABI 构建与
  APK v2 签名验证通过。
- alpha08 已覆盖安装到 Pixel 8 / Android 17，versionCode 10；远程私有订阅需由用户在
  设备上重新粘贴完整链接验证，测试过程不读取或输出 token、UUID 与节点凭据。

## 2026-07-29：alpha07 分流规则删除

- 应用规则的出口编辑弹窗新增明确的红色“删除规则”入口。
- 点击删除先显示二次确认，并说明删除后该应用会改用默认出口；取消不会改变现有规则。
- 确认删除后规则会从列表与持久化存储同步移除；VPN 已连接时触发配置热重载。
- Pixel 8 / Android 17 覆盖安装后，真机确认删除入口和二次确认均可点击；验收时取消最终
  删除，保留用户原有规则。测试结束时 VPN network 数量为 0。
- `0.3.0-alpha07` 的 27 项单元测试、Android Lint、四 ABI 原生构建和 APK 签名验证通过。

## 2026-07-29：alpha06 条件出口选择

- 默认出口与单应用出口统一为两步流程：第一步只列订阅，第二步只列所选订阅的自动策略和节点。
- 节点行只呈现核心名称，不再添加订阅名前缀、协议或空延迟说明；显示层会移除开头的国家
  旗帜 Emoji 和 `\u...` 转义装饰，Mihomo 匹配仍使用未修改的原始名称。
- 已持久化的旧式“订阅名 · 节点名”展示标签会在启动时迁移为核心节点名，不改变订阅 ID、
  节点 ID 或实际路由。
- 模拟器验证第一步没有自动策略或节点列表；选择订阅后第二步出现自动策略、两个测试节点和
  “更换订阅”，节点行没有订阅名前缀、协议或延迟副标题。

## 2026-07-29：alpha05 订阅管理

- 在 Android 16 ARM64 模拟器导入含 2 个节点的最小 Clash YAML，订阅卡进入真实详情页，
  两个节点均可见。
- 名称修改后按钮回到不可重复保存状态，关闭并重新打开详情仍显示新名称。
- 使用含 1 个不同节点的文件原位替换，订阅 ID 与名称保留，节点列表立即从 2 变为 1。
- 将替换前即将被移除的节点设为默认固定出口；替换后该引用自动降级到同一订阅的自动策略，
  不保留失效节点 ID。
- Pixel 8 / Android 17 覆盖安装 `0.3.0-alpha05` 后保留原加密数据，真实 43 节点订阅可进入
  详情并显示搜索框；无匹配查询正确显示空结果，测试未输出节点名、订阅地址或凭据。

## 2026-07-29：Pixel 8 / Android 17

在 Pixel 8 ARM64 真机上验证 `0.3.0-alpha03` 数据面，并覆盖安装
`0.3.0-alpha04` 验证新增交互：

| 项目 | 值 |
|---|---|
| Android | 17 / API 37 |
| 设备 | Pixel 8 |
| ABI | `arm64-v8a` |
| APK | `app-arm64-v8a-debug.apk` |
| target SDK | 36 |
| 原生核心 | CMFA `82b73a4` / Mihomo `e26714a` |

### 已通过

- 全新安装、冷启动、CMFA 原生库装载及 Android 系统 VPN 首次授权。
- 从系统文件选择器导入真实 Clash/OpenVPN 配置，共识别 43 个节点。
- 导入后立即删除公共“下载”目录中的明文临时文件，仅保留 Android Keystore AES-GCM 密文。
- `specialUse` 前台服务处于 Android 17 认可的 foreground 状态。
- 双栈 `tun0` 建立，MTU 9000；Android VPN network 为 `IS_VALIDATED`。
- 真实 OpenVPN provider 完成连接，首页能查询实际协议、活动节点、延迟和流量。
- Chrome 固定节点规则成功编译为 `PROCESS-NAME` 与 provider 精确过滤组。
- Chrome 新建 HTTPS 连接后 UID 归属命中，UI 显示“应用识别正常”。
- Wi‑Fi 切换到蜂窝数据时 VPN 保持已保护和已验证；恢复 Wi‑Fi 后 HTTPS 与固定规则继续工作。
- 切网瞬间出现 8 条 OpenVPN 重连错误，恢复后重新测试为 0，未出现 fatal、group 或持续 OpenVPN 错误。
- 断开后 `tun0`、VPN network 与全部明文运行文件消失，加密订阅仍保留。
- alpha04 保留原有 43 节点加密订阅与应用规则，首页默认出口可进入真实节点选择器。
- 旧版本相机扫码入口曾启动 Google Code Scanner；该依赖已在 alpha34 移除，当前实现改为
  首次使用时按需申请 `android.permission.CAMERA`，再由系统相机预览和随包 ZXing 本机识别。
- 相册二维码入口继续使用系统图片选择器和随包 ZXing；本轮轻量化改动后的真机回归待补。

### 尚未覆盖

- 熄屏、Doze、系统回收进程、开机启动、always-on 与“阻止无 VPN 连接”。
- IPv6-only、QUIC 专项、系统级 DNS 泄漏检测和长时间弱网重连。
- 共享 UID、工作资料、克隆应用以及更多厂商 ROM。

## 2026-07-29：Android 16 ARM64

本轮使用隔离的官方 Google APIs ARM64 模拟器验证 `0.3.0-alpha03` 数据面，并回归
`0.3.0-alpha04`：

| 项目 | 值 |
|---|---|
| Android | 16 / API 36 |
| 系统镜像 | `google_apis;arm64-v8a` revision 7 |
| 构建指纹 | `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323` |
| 模拟器 | 36.6.11 |
| 虚拟设备 | Pixel 6，`arm64-v8a` |
| APK | `app-arm64-v8a-debug.apk` |
| 原生核心 | CMFA `82b73a4` / Mihomo `e26714a` |

### 已通过

- APK 冷启动，`libbridge.so` 和 `libclash.so` 成功装载，CMFA 完成初始化。
- 系统 VPN 授权对话框和 Android 16 前台服务正常。
- CMFA 接管 detached TUN fd；系统创建 `tun0`，MTU 9000。
- IPv4 `172.19.0.1/30` 与 IPv6 `fdfe:dcba:9876::1/126` 地址生效。
- IPv4/IPv6 默认路由和双栈 DNS 都进入 TUN。
- Android 把 VPN network 标记为 `IS_VPN`、`IS_VALIDATED`，底层网络为 Wi‑Fi。
- CMFA 实际记录到 DNS hijack、TCP、UDP 流量，并命中 `DEFAULT[WEAVE-DIRECT]`。
- UI 连接后显示“已保护 / 断开”，断开后显示“未连接 / 连接”。
- 断开后前台服务、VPN network、TUN 接口、生成的 `config.yaml` 和 provider 运行副本均消失。
- 最终回归没有 Java fatal exception、SIGSEGV 或新增 tombstone。
- 使用系统文件选择器导入包含 YAML merge anchor 的真实 Clash/OpenVPN 配置，共识别 43 个节点。
- 订阅正文仅持久化为 Android Keystore AES-GCM 密文；断开后明文 provider 和配置文件数量为 0。
- 真实 OpenVPN 出口完成握手，首页能查询当前叶子节点、协议、延迟与实时流量。
- Chrome 的固定节点规则编译为 `PROCESS-NAME` 规则和单 provider 精确过滤组。
- Chrome 发起 HTTPS 流量后，Android UID 归属回调命中已配置应用，UI 显示“应用识别正常”。
- 固定节点连接期间 Android VPN network 为 `IS_VALIDATED`，运行错误为 0。
- alpha04 可从首页手动选择默认固定节点，生成配置中该节点排在 `DEFAULT` 组首位。
- 连接期间改选另一个默认节点会触发安全热重载；配置哈希变化、VPN 恢复
  `IS_VALIDATED`，且 fatal、group 与 OpenVPN 错误均为 0。
- 旧版本模拟器缺少可启动的 Google Code Scanner UI 模块；alpha34 已改用系统相机预览，
  申请权限路径和 Android 17 真机扫码回归待补。
- 使用不含敏感信息的最小 Clash YAML 测试二维码完成相册端到端导入：随包 ZXing 被选用，
  对话框自动关闭，总节点数由 43 增至 44，随后已清空模拟器测试数据与测试图片。

### 运行验证发现并修复

1. CMFA `load()` 接受 profile 目录而非 `config.yaml` 完整路径。
2. CMFA 的 C ABI 要求宿主注册 callback function pointers；回调参数内存由 CMFA 包装器释放。
3. 零订阅直连 profile 仍须包含一个显式 proxy，现使用 `WEAVE-DIRECT`。
4. 首页按钮原先始终执行连接，现按状态切换连接/断开，连接中禁用重复点击。
5. 内核停止时删除生成配置目录，避免路由元数据残留在 cache。
6. 订阅 parser 增加 YAML merge anchor 和 OpenVPN 节点识别，并提供 5 MiB、严格 UTF-8 的本地文件导入。
7. CMFA 在 `<profile>/providers/` 下解析 file provider，配置已改用相对于 provider 目录的文件名。
8. provider 节点不能作为内联 proxy 直接引用；固定节点组现使用 `use + filter` 精确选择。
9. JNI 增加运行时 group 查询，首页展示真实活动节点、协议、延迟和流量。
10. 增加只计数、不采集目标地址的应用 UID 归属健康指标。
11. 首页“当前出口”改为真实默认节点选择器，并支持连接中的配置热重载。
12. 订阅导入增加 HTTPS/代理 URI 二维码解析、相机扫描和本机相册二维码识别。
13. 移除设置页假开关、订阅卡假箭头等无行为控件；仅为真实可操作项显示点击反馈。

### 尚未覆盖

- 更多 ARM64 真机和厂商 ROM。
- 多订阅之间的自动组与固定节点交叉组合。
- QUIC 专项、DNS 泄漏测试、IPv6-only、Wi‑Fi/蜂窝切换、Doze、always-on 和 kill switch。
- 100 次连接/断开、内核崩溃恢复、共享 UID、工作资料和克隆应用。
- release 签名、性能、电量、SBOM 和生产更新链路。

因此本记录证明真实 OpenVPN 订阅和单应用固定节点数据面已在 Android 16 ARM64 模拟器及
Pixel 8 / Android 17 真机运行，不构成生产发布结论。
