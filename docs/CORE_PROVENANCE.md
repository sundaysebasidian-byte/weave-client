# 原生内核来源锁

Weave 0.3 已携带由锁定源码本地复现的原生内核；`core-lock.properties` 固定构建输入和
四个 ABI 的 SHA-256，避免跟随可变的 `main`、`Alpha` 或 `latest`。

| 输入 | 固定值 |
|---|---|
| CMFA | `82b73a4bca24f1606e4b443bc9574cf1758c9693` |
| CMFA 版本 | `2.11.32` |
| Weave CMFA 补丁 | `core-patches/cmfa-weave-runtime.patch` |
| 补丁 SHA-256 | `8ac691f191bfa3d1368a231fbcbc6cf8e6d0d953f23a0786cc53bab956906590` |
| Mihomo submodule | `e26714a181ac0e2fa803453c0a8e9a9ce94e31cb` |
| Mihomo commit archive SHA-256 | `a75220bf11ab56ef0d304c14c4d4c407edffe700761cdcc5b06105dfd8305168` |
| Go | `1.26.5` |
| Android NDK | `29.0.14206865` |
| CMake | `3.31.6` |
| build tags | `foss,with_gvisor,cmfa` |
| 预期 native artifacts | `libclash.so`, `libbridge.so` |
| macOS arm64 Mihomo SHA-256 | `0cf93bb94fdb322e91120b8c6b67e35997a0962ba7cea7fe1f988a1c7060ee1a` |

这些值来自 2026-07-29 对 CMFA 上游源码、submodule tree 和构建插件字节码的核对。Weave
补丁包含三个窄改动：把 Android 已查询到的连接所有者 UID 写入 Mihomo `Metadata.Uid`；
导出只解析、不应用配置的 `validateConfiguration`；在手动健康检查完成后释放跨语言回调
对象。构建脚本会同时校验补丁文件哈希并确认全部改动已应用。Go
依赖先由 `go.sum` 与 Go checksum database 校验，再以 `GOPROXY=off` 编译。当前桥接层由
Weave 自行维护，只开放配置解析/加载、TUN、socket protect、UID 查询、运行组/健康检查和
流量计数。

macOS 版本不跟随 Mihomo 仓库当前默认分支。审计时该仓库默认分支的内容已与历史 Go
内核无关，因此构建输入改为 GitHub 上按完整 commit 定位的不可变 archive，并同时核对
CMFA 固定 commit 的 gitlink 确实指向该 Mihomo commit。archive 与最终 darwin/arm64
可执行文件的 SHA-256 都记录在 `core-lock.properties`；任一不匹配，macOS 打包脚本都会拒绝。

## 本地复现

先初始化锁定的 Mihomo submodule，并准备 Go 模块缓存。随后在断网模式执行：

```bash
GOPROXY=off bash tools/build-core.sh \
  /path/to/ClashMetaForAndroid \
  /path/to/android-sdk/ndk/29.0.14206865
```

脚本会核对两个 Git commit、补丁及其 SHA-256、Go/NDK 版本，并拒绝未应用补丁或
SHA-256 不匹配的产物。构建参数与 CMFA
1.0.4 构建插件一致：`c-shared`、`-trimpath`、`-s -w`、API 21 clang 以及
`foss,with_gvisor,cmfa` tags。

## 更新流程

1. 查看两个 commit 间的全部差异，重点检查 JNI、TUN、DNS、下载器、controller 和依赖替换。
2. 对 `go.mod` / `go.sum` 生成依赖清单和许可证报告。
3. 用仓库内脚本从源码构建四个 ABI，禁止上传外部群组提供的 `.so`。
4. 记录每个 ABI 的 SHA-256、ELF build ID、Go build info 和 SBOM。
5. 运行连接、DNS/IPv6 泄漏、包名路由、休眠唤醒和 100 次重连测试。
6. 两名维护者复核后更新 `core-lock.properties`。

联网环境可执行：

```bash
bash tools/verify-core-lock.sh
```

该脚本只验证提交仍由指定 GitHub 仓库公开引用；不能替代源码审计或签名验证。
