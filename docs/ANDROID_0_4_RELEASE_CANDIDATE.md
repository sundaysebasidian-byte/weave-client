# Android 0.4 发布候选

本地候选：`0.4.0-rc1` / versionCode `107` / ARM64 / Android 8.0+。当前公开版本仍为
Build 106，没有发布新的 GitHub Release。本候选不代表 Windows 或其他平台验收。

## 引导体验

首次、无配置的新安装在本地数据加载成功后显示三步引导：导入订阅 → 选择节点 → 连接。
升级安装、已有订阅/路由/出口、已确认 VPN 说明或外部导入入口均不强制弹出。设置 →
开始使用 → 新手引导可重新查看。跳过、返回或翻页不启动 VPN、不授予权限，也不更改配置。
导入与选节点复用已有对话框；取消返回当前步骤，失败不会推进，成功后继续。
正在提交的导入沿用原有事务保护，完成前不关闭对话框。

动画用于页面变化与点击反馈；“减少动效”或设置中的“减弱引导动画”保存于本机，
关闭引导转场、按压缩放与连接转圈。普通 Compose 动画仍遵从系统动画时长设置。
这是引导偏好，不会声称关闭全应用所有 Material 控件动效。支持六种语言与主题色。

`docs/previews/quick-start.html` 是无网络的交互设计预览，标注“非实机截图”。它模拟
取消、导入失败重试、授权取消与连接失败；不能作为 Android 设备或 VPN 测试证据。

## 构建、签名与升级

本候选关闭调试，启用 release 同等 R8 和资源压缩，但仍由现有开发证书签名。
生产 signing 的四项输入尚未配置。没有读取、复制或新建私钥。

开发证书公开 SHA-256：
`54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`。

应用 ID 保持 `io.weave.client`，107 比 106 的版本码高且签名相同，满足覆盖升级的
必要条件；实际安装和数据保留仍未验收。换用其他正式证书通常不能覆盖开发证书安装，
须由维护者安全确认生产签名保管与现有用户迁移方案。不要在仓库、交付包或聊天保存密钥。

验收前使用应用内加密备份保存可用配置和备份口令。候选新增的只有 UI 偏好，未修改
订阅存储或备份格式；这不替代升级实测。Android 通常拒绝低版本码覆盖高版本码，
保留旧 APK 不等于保证可直接回滚。不得盲目卸载、清数据或强制降级。

## 已完成和待验收

真实离线 JVM、lint、压缩构建、APK 字节检查、依赖与源码哈希结果在交付包 `QA107.json`。
JVM 使用 debug variant 的相同 main 源码；静态 APK 检查不证明 R8 后运行时兼容性。
新增 Android UI 测试只编译，不冒称运行通过。用户已停止手机安装和实际 VPN/网络操作，
本次没有执行设备测试，也不恢复极限开关、强制断网或 100 次重连。

待授权后，以指定设备和用户测试配置做有界验收：备份 → 106 到候选的覆盖升级 →
首次/已有用户引导与取消/返回/重试 → 主题、窄屏、大字体与语言 → 通知权限拒绝后的恢复 →
一次连接与断开、后台返回。宣传实际分流或 DNS/IPv6/WebRTC 防护效果前应记录独立核验结果。

正式版本拟为 `0.4.0` / versionCode `108`，须生产签名、那个确切二进制的真机验收与
用户公开发布确认后生成；不得将 RC 改名充当 stable。

## 本地命令

```bash
./gradlew :app:testDebugUnitTest :app:lintLocalOptimized :app:assembleLocalOptimized \
  -PweaveArm64Only=true --offline
python3 tools/verify-android-artifact.py \
  app/build/outputs/apk/localOptimized/app-arm64-v8a-localOptimized.apk \
  --channel candidate --version 0.4.0-rc1 --version-code 107 --output candidate-check.json
python3 tools/test-release-artifact.py
bash tools/audit-local-release.sh
```

APK 检查不安装软件。stable 检查需要维护者确认的生产证书公开指纹；工具会拒绝调试包、
开发签名和预发布版本名。当前原生内核哈希固定，未来内核变更需单独复核工具与来源锁。

## 源码与材料

候选交付含 Android Maven 与 ARM64 内核实际 Go module SBOM、许可证/NOTICE、
固定 CMFA/Mihomo 源码、补丁、构建说明和与内核嵌入 h1 校验和匹配的 Go 模块源码。
依赖元数据不保证每个类经 R8 后仍打包，也不是跨平台或法律认证。

对外文字与技术 QA 分开。原始和已确认的 Build 105 宣传视频保留原字节，它们是历史
品牌/界面素材，不能标为本候选的实机录像。最新引导实机画面仍需上述设备验收授权。
