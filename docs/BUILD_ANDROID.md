# 构建 Android

安装 JDK 17、Android SDK 36、NDK `29.0.14206865` 和 CMake `3.31.6`。
在未提交的 `local.properties` 中配置本机 `sdk.dir`，或设置 `ANDROID_HOME`。
Gradle 版本与校验值以仓库 wrapper 为准。

```sh
bash tools/audit-local-release.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease \
  --no-daemon --no-configuration-cache
```

默认分别构建 ARM64、ARM32、x86、x86_64；不会生成包含四套内核的 universal APK。
只构建现代 ARM64 手机可增加 `-PweaveArm64Only=true`。
无发行签名配置时，`assembleRelease` 输出未签名 APK，不能直接安装。

发行签名只从环境变量或 `~/.gradle/gradle.properties` 读取，永不进入仓库：

```sh
export WEAVE_RELEASE_KEYSTORE=/path/to/release.jks
export WEAVE_RELEASE_STORE_PASSWORD=...
export WEAVE_RELEASE_KEY_ALIAS=...
export WEAVE_RELEASE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

推送 `v*` 标签时，`.github/workflows/android-release.yml` 构建四种 ABI；若仓库配置了
`WEAVE_RELEASE_KEYSTORE_BASE64` 等 Secrets 则签名，否则发布未签名 APK。同时生成 `SHA256SUMS`
与 CycloneDX SBOM（`tools/generate-sbom.py`，完全离线，由锁定文件生成），以草稿 Release 形式上传。
发行包不含 Google 加密的依赖信息块（`dependenciesInfo` 已关闭），便于第三方复现构建。

注意：已安装的开发证书版本无法被生产证书版本覆盖安装；切换签名前请先用“备份与恢复”导出加密备份。

本地测试可使用 `:app:assembleLocalOptimized`：开启 R8 和资源压缩、关闭 debuggable，
但沿用本机开发证书。公开 alpha 预览包的签名边界在对应 Release 中说明；生产分发应使用
独立妥善保管的发行签名。覆盖安装须签名一致，切勿为解决签名不匹配而盲目卸载已有数据。

## 内核与许可证

- [内核来源与复现](CORE_PROVENANCE.md)
- [锁定内核与哈希](../core-lock.properties)
- [第三方说明](../THIRD_PARTY.md)
- [发行检查清单](OPEN_SOURCE_RELEASE_CHECKLIST.md)

本仓库的公开发行目标是 Android。其他平台目录不属于 Android Release。
