# Changelog

This project follows semantic versioning while in alpha. Breaking storage or
configuration changes may still occur before 1.0.

## Unreleased

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
