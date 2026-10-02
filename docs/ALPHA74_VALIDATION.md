# Android alpha74：订阅完整性、迁移识别与素纸外观

## 依据与修改

- [Walless 官方公告](https://t.me/s/WallessPKUChannel?before=83)说明：服务端会把多个服务器聚合为一个展示节点，`cluster=false` 可单独列出。Weave 在已知 Walless 主订阅请求中补齐该参数，保留显式 `cluster=true/false`、其他参数和其他供应商地址；provider 子请求仍不附加主订阅参数。实际节点数量由当时服务端响应决定，不硬编码 65。
- Clash 元数据不再使用 URI 转换名单过滤原生节点。结构有效的节点逐一列出，仍需通过原生校验才能连接；不将“列出”当作“内核支持”。索引版本递增，保留原始密文和已匹配节点 ID。
- 客户端识别补齐 [CMFA 主包及 Alpha/Meta](https://github.com/MetaCubeX/ClashMetaForAndroid/blob/main/build.gradle.kts)、[Karing](https://github.com/KaringX/karing/blob/main/android/app/build.gradle.kts)、[Clash Mi](https://github.com/KaringX/clashmi/blob/main/android/app/build.gradle.kts)、[FlClash 与开发包](https://github.com/chen08209/FlClash/blob/main/android/app/build.gradle.kts)。使用精确 package queries 与启动器名称匹配，无 QUERY_ALL_PACKAGES；识别不代表身份/安全认证。
- 迁移支持重新检测、滚动列表、兼容文件/包装链接/二维码。粘贴包装链接与扫码复用解码和 HTTPS 限制。不读取客户端私有数据、不解析未支持的专有 ZIP 备份，不自动导入他端的系统代理、脚本或规则。
- 新增“素纸”：黑白中性色，平整卡片分支不执行玻璃高光、渐变和投影代码；四款艺术主题与原极简主题不变。

## 定向验证

- 71 项 JVM 测试通过、0 失败：订阅解析/抓取参数、URL/QR、节点完整性、客户端识别与可见性清单、新外观分组、多语言覆盖。
- 最终防丢失补丁另跑审计和完整语言覆盖 4 项，0 失败；含新增的 65→23 阻止覆盖回归用例。
- 65 节点 fixture 包括 23 个常见类型及 42 个原生类型元数据，解析和规范化后均保留 65；此测试只证明对象不丢失，不验证 fixture 连接能力。
- 本地发布审计通过；不构成安全或法律认证。
- 覆盖前真机旧 Walless 缓存显示 65 个节点。旧缓存不能用于证明新请求会返回相同数量。

## 真机揭示的未解决项

- versionCode 78 已成功覆盖。真实定向刷新仍返回/导入 23 个节点，不能宣称恢复 65；编辑页只核对参数，确认 `client=cfa`、`provider=false`、`cluster=false`、无 group。未输出订阅地址或令牌。
- 此次刷新触发已有缺陷：`large_removal` 仅 REVIEW，Repository 仍提交覆盖；UI 的“旧版本仍可回退”不符合存储实现，旧 payload 在成功保存后会删除。本次旧 65 节点缓存已被替换为 23，不宣称已恢复。
- 最终补丁将减少超过一半设为 BLOCKED，真实保持旧订阅而非只警告；追加 65→23 用例。该保护避免以后重复丢失，不能恢复已删缓存。
- 已询问用户另一客户端同地址刷新后是否仍有 65 个节点。需要新鲜的对照配置或服务端完整列表才能继续确认 Walless 差异；未伪造节点或把旧缓存当作新请求成功。
- 真机外观选择器已显示“素纸”，原四款艺术风保留；原选择为“暮色花园”。进一步渲染检查时手机切到其他应用，已停止操作并删除临时截图。未宣称完成新主题截图验收或恢复了主题选择。
- 最终 versionCode 79 防丢失包待用户覆盖；当前手机已安装的是 versionCode 78，不含最终 BLOCKED 修正。未继续操作用户正在使用的手机。

没有进行长时间压力或耗电测试。

## 最终安装包

- `dist/Weave-0.3.0-alpha74-arm64.apk`，versionCode 79，19,176,875 字节。
- SHA-256：`3023cdf5d6bd524dfffa2f9331c192fdcb2db5bce26a60595dc0e27b7f9f8c02`。
- R8 优化构建与 vital lint 成功，签名证书与原包一致；未提交或推送代码。
