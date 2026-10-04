# RC108 device feedback audit

RC108 (versionCode 108, 0.4.0-rc2) addresses production RC107 device feedback. The base is d66e696213459fbd1f26bc6242d8ceb90a9083f5. No remote publication or phone operation is part of this change.

## Changes

- Node headers reserve width for names and protocols. Probe status and failure counts use full width below the header. Translated controls wrap or stack at narrow widths and large system fonts.
- Scoped official Claude Sonnet 5.5 high collaboration supplied the node layout and static Canvas globe glyph; integration, importer behavior and verification were reviewed locally.
- Other-client imports now preview the actual input format, mapped protocols and counts, then require confirmation before encrypted persistence. Preview tokens expire and are consumed once. Inputs are user-selected SAF files, pasted compatible exports/HTTPS links, live QR, or QR images.
- Sources are recognized by content. Client choice changes export guidance and can launch the actual source app; it does not grant private app-data access. Backups/databases and source routing/DNS/groups are excluded. A successful parse is not a reachability claim.
- sing-box Shadowsocks counts and SOCKS authentication survive normalization. V2Ray Trojan passwords use the server field; Reality uses its own identity settings. Multiple V2Ray servers/users and unsupported transports fail explicitly rather than silently losing entries.
- Connection more opens exit selection, diagnostics, active connections, logs and recovery. Settings LAN transfer opens the actual transfer dialog.
- Candidate artifact checks can require the exact existing production certificate, including the candidate channel. No key is generated or copied.

## Entry inventory

This is an implementation/wiring audit. A wired action is not proof of real-phone or live-network acceptance. Information pages intentionally explain their topic.

| Entry | Source | Actual handler / behavior |
| --- | --- | --- |
| 新手引导 | SettingsScreen.kt:274 | onOpenQuickStart |
| 减弱引导动画 | SettingsScreen.kt:281 | onQuickStartMotionChanged |
| 外观 | SettingsScreen.kt:292 | { showPalette = true } |
| 语言 | SettingsScreen.kt:300 | { showLanguage = true } |
| DNS | SettingsScreen.kt:314 | { showDnsSettings = true } |
| IPv4 / IPv6 | SettingsScreen.kt:325 | { showIpv6Mode = true } |
| 自动节点策略 | SettingsScreen.kt:332 | { showAutomaticStrategy = true } |
| Always-on 与断网保护 | SettingsScreen.kt:339 | onOpenVpnSettings |
| 安全与隐私 | SettingsScreen.kt:354 | { showSecurityDetails = true } |
| 网络与隐私检测 | SettingsScreen.kt:361 | onOpenPrivacyObservatory |
| 阻止 UDP STUN | SettingsScreen.kt:368 | onBlockUdpStunChanged |
| 恢复中心 | SettingsScreen.kt:376 | onOpenRecoveryCenter |
| 自动更新订阅 | SettingsScreen.kt:386 | { showAutoUpdate = true } |
| 备份与恢复 | SettingsScreen.kt:399 | onOpenBackup |
| 局域网互传 | SettingsScreen.kt:406 | onOpenLanTransfer |
| 策略组范围 | SettingsScreen.kt:432 | { showStrategyScope = true } |
| 引导 DNS | SettingsScreen.kt:439 | { showBootstrapDns = true } |
| 直连应用绕过 VPN | SettingsScreen.kt:446 | onBypassDirectAppsChanged |
| 系统 HTTP 代理 | SettingsScreen.kt:454 | onSystemHttpProxyChanged |
| 局域网代理共享 | SettingsScreen.kt:463 | { showLanProxy = true } |
| 高级路由 | SettingsScreen.kt:478 | { showRoutingDetails = true } |
| 国内智能直连 | SettingsScreen.kt:485 | onDomesticDirectChanged |
| 本地域名 / IP 规则 | SettingsScreen.kt:493 | onOpenLocalRouteRules |
| 远程规则集 | SettingsScreen.kt:500 | onOpenRuleSets |
| 自定义策略组与链式代理 | SettingsScreen.kt:507 | onOpenCustomGroups |
| 离线策略包 | SettingsScreen.kt:514 | onOpenPolicyPacks |
| 实时连接 | ToolScreens.kt:529 | ConnectionsScreen -> CoreApi.connections/close/closeAll; pause, search and private live display; active runtime required |
| 内核日志 | ToolScreens.kt:536 | LogsScreen -> CoreApi.streamLogs; level, pause, memory clear, LogRedactor.redacted clipboard copy |
| Weave ${BuildConfig.VERSION_NAME} | SettingsScreen.kt:547 | { showOpenSourceDetails = true } |
| VPN 数据路径说明 | SettingsScreen.kt:554 | onShowVpnDisclosure |
| 通知权限状态/授权入口 | SettingsScreen.kt:349 | MainActivity.requestNotifications / NotificationPermissionPolicy |
| 连接/断开 | MainActivity.kt | MainActivity.requestVpnPermission -> VpnService/CoreClient; onConnect/onDisconnect |
| 分流模式 | AppViewModel.kt | AppViewModel.selectMode -> RuntimeSettingsStore + reloadIfConnected |
| 默认出口、应用出口、搜索、收藏、手动/自动选择、测速 | RouteTargetDialogs.kt | DefaultRouteTargetDialog/RouteTargetDialog -> setDefaultRouteTarget/setRouteTarget/toggleFavoriteNode/checkSubscriptionHealth |
| 连接页 ⋯ 五个操作 | ConnectionActionsDialog.kt | ConnectionActionsDialog -> choose exit / diagnostics / active connections / logs / recovery |
| 应用规则 添加/删除/预设/路由解释 | WeaveApp.kt | addAppRoute/removeAppRoute/applyRoutingPreset; RouteLensDialog |
| 订阅 新建/HTTPS/文件/二维码/外部分享 | SubscriptionRepository.kt | SubscriptionRepository + SafeSubscriptionFetcher/SAF + QrSubscriptionDecoder; explicit confirmation for external shares |
| 其他客户端导入 | ClientExportImportDialog.kt | previewClientImportFile/Text/QrImage -> applyClientImport -> SubscriptionStore.save; source client launch is a real Android Intent |
| 订阅 刷新/单项刷新/失败重试/更新预览 | AppViewModel.kt | refreshRemoteSubscriptions + SubscriptionAudit; explicit update confirmation |
| 订阅详情 改名/替换 HTTPS 或文件来源/删除/脱敏显示/测速与质量矩阵 | SubscriptionDialogs.kt | SubscriptionManagerDialog -> loadEditor/renameSubscription/replaceSubscriptionRemote/replaceSubscriptionFile/deleteSubscription/checkSubscriptionHealth; repository operations and health state |
| 网络与隐私 完整/浏览器/下载检测、取消、脱敏摘要 | NetworkPrivacyCenter.kt | runIpQualityProbe/runDownloadProbe/BrowserPrivacyProbe + cancellation/revision gates; explicit active checks |
| 实时连接 刷新/清理 | ToolScreens.kt | ConnectionsScreen -> CoreApi.connections/close/closeAll; pause, search and private live display; active runtime required |
| 内核日志 查看/复制 | ToolScreens.kt | LogsScreen -> CoreApi.streamLogs; level, pause, memory clear, LogRedactor.redacted clipboard copy |
| 远程规则集 添加/编辑/下载/启停/删除 | RoutingToolScreens.kt | RoutingRulesViewModel.saveRuleSet/refreshAllRuleSets/setRuleSetEnabled/deleteRuleSet |
| 自定义策略组、链式代理 保存/删除 | RoutingToolScreens.kt | AppViewModel.saveCustomGroup/deleteCustomGroup -> encrypted store + assembler |
| 本地域名/IP规则 添加/启停/删除 | RoutingToolsDialogs.kt | RoutingRulesViewModel.addLocalRouteRule/setLocalRouteRuleEnabled/deleteLocalRouteRule |
| 离线策略包 导入/启停/删除 | RoutingToolsDialogs.kt | RoutingRulesViewModel.importPolicyPack/setPolicyPackActive/deletePolicyPack |
| 备份 导出/读取预览/确认恢复 | RoutingToolScreens.kt | AppViewModel.exportBackup/readBackup/confirmRestore -> BackupCodec/SAF/encrypted stores |
| 局域网互传 导出/停止/链接或二维码导入 | AppViewModel.kt | LanTransferDialog -> startLanExport/stopLanExport/importLanTransfer/importLanTransferQr |
| 新手引导 导入/选出口/连接/关闭/减弱动画 | WeaveApp.kt | QuickStartPolicy + actual parent callbacks + persisted preference |
| 系统快捷磁贴、VPN通知 | core/vpn | WeaveTileService / TrafficNotificationPolicy -> actual runtime state |

## Validation boundaries

- JVM mapping, preview lifetime, QR and release guards have real fixture assertions. Android tests exercise real Compose components and Keystore-backed subscription persistence with synthetic data.
- The 72-case native matrix changes the isolated emulator’s actual Android viewport and font scale: 320/360/411dp × 1/1.3/1.5/2 font scale × six UI languages. It checks actual layout-owner font scale, horizontal protocols and visible glyph extents.
- The legacy CompositionLocal-only matrix is explicitly marked actualAndroidConfiguration=false; it is not presented as real system-font evidence.
- Screenshot collection waits for Android rendering and scrolls the unmerged Compose semantics tree to the target node when needed.
- No test starts the VPN or contacts real subscriptions, nodes or probe endpoints. Camera, source-client launch, system permission dialogs, LAN peers, real network reachability and user backup/restore remain owner acceptance.
- Windows 10/11 VPN rollback was not tested by this Android/Mac work.
- RC107 installer authentication prompt cause remains unknown. Public RC107 signature verification cannot identify that transient device prompt.

## Candidate signing

The owner-only local flow must require the exact existing RC107 production certificate SHA-256: `1c8f2258965d95784c7a9756119ced127bdbcee2c21867de2c1abbd483fac4e1`. Official apksigner receives the existing password through the owner local console only. Unsigned build output is not a phone-installable signed candidate.
