# 构建 Android

## 选择源码版本

公开 RC4（Build 110）的固定源码位于 [`v0.4.0-rc4`](https://github.com/sundaysebasidian-byte/weave-client/tree/v0.4.0-rc4)。`main` 已整合 RC4 / versionCode `110` 的代码与测试，并保留既有修复和最近文档。复现公开 APK 时请在工作副本中选择对应标签；后续 main 构建需独立验证：

```sh
git fetch origin tag v0.4.0-rc4
git checkout v0.4.0-rc4
```

完整内核及依赖源码见 [RC4 附件](https://github.com/sundaysebasidian-byte/weave-client/releases/tag/v0.4.0-rc4)中的 `Weave-RC110-Corresponding-source.zip`。该标签内旧 RC107/RC109 状态说明是历史记录；当前发行与验收范围见 [RC4 说明](releases/v0.4.0-rc4.md)。

## 工具链与构建

安装 JDK 17、Android SDK 37.0（`platforms;android-37.0`）、NDK `29.0.14206865` 和 CMake `3.31.6`。SDK 命令行工具使用 `15859902` 或更新版本；旧工具不能完整识别新版 SDK 元数据。`compileSdk` 为 37，`targetSdk` 仍为 36，最低版本仍为 26。
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

CI 在可丢弃的 API 36 模拟器运行加密存储、订阅更新竞争与跨进程偏好回归，并保留报告。
本地复现时先创建独立测试模拟器；不要指定装有个人数据的现有设备。构建 `:app:assembleDebug`
和 `:app:assembleDebugAndroidTest` 后，明确指定它的序列号运行：

```sh
WEAVE_ISOLATED_EMULATOR=1 bash tools/test-android-storage.sh emulator-5580
```

使用独立 ADB 服务时同时设置 `WEAVE_TEST_ADB_PORT`。脚本只向指定模拟器安装测试 APK，
不会卸载已有应用、清空数据或操作其他设备；签名不匹配时直接失败。

本地测试可使用 `:app:assembleLocalOptimized`：开启 R8 和资源压缩、关闭 debuggable，
但沿用本机开发证书。本地构建不自动获得发布包的生产签名。公开 Build 106 使用开发证书，
公开 RC4 使用生产证书；已有生产签名 RC107–RC110 属于同一证书链。生产私钥应独立保管，
不得进入仓库。覆盖安装须签名一致；106 到 RC4 的迁移尚未验收，切勿通过卸载或清空数据强行升级。

## 内核与许可证

- [内核来源与复现](CORE_PROVENANCE.md)
- [锁定内核与哈希](../core-lock.properties)
- [第三方说明](../THIRD_PARTY.md)
- [发行检查清单](OPEN_SOURCE_RELEASE_CHECKLIST.md)

本仓库的公开发行目标是 Android。其他平台目录不属于 Android Release。
