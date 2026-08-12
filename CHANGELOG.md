# Changelog

This project follows semantic versioning while in alpha. Breaking storage or
configuration changes may still occur before 1.0.

## Unreleased

- Android：根据真机视觉复核进一步减法重构：移除主要卡片硬描边和高不透明白块，将连接页主卡改为不规则叠色笔触，并把大标题切换为更轻的衬线层级，避免“白灰卡片 + 几何圆形”拼贴感。
- Android / macOS：加入低透明度的莫奈式冷压纸张纹理、叠色晕染和短笔触；主连接卡不再是纯色矩形，卡片层级改为连续画布上的纸张透叠，减少白灰块拼接感。
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
