# Weave Next 0.4.0-rc11 · Build 117

Android ARM64 生产签名预发布版。使用与 RC10 相同的应用 ID 和签名证书，可以覆盖安装已签名的 RC10。

## 连接、诊断与共享

- 首页分别显示 HTTPS 响应头耗时与下载吞吐，网络路径变化后清除旧测速。网络与隐私页的一键检查绑定当前 VPN，报告显示探测依据。手动 DNS 对比使用当前物理网络，核验 DoH 和 DoT 的实际应答，并复用同批重复端点的结果。新增固定 IP 的加密引导 DNS。
- 新增仅 IPv6，在 Android TUN 内阻止 IPv4，关闭应用绕过，并过滤 VPN DNS 的 IPv4 应答。规则、全局和直连沿用现有 TUN。
- 新增可选的重启解锁后重连。此前成功连接且 VPN 授权仍有效时会尝试恢复，手动断开会取消恢复。系统始终开启和断网保护仍由 Android 管理。
- 局域网代理共享显示设备地址、端口和按需查看的加密存储凭据。补齐同一 Wi‑Fi、手机热点、HTTP 和 SOCKS5 接收端的配置说明。 [连接指南](https://github.com/sundaysebasidian-byte/weave-client/blob/c8f46aa20a02b8ac243ce98d5ee3ac2354461d2f/docs/android-network.md)

## 订阅、隐私与使用体验

- 支持通过 Android 文件选择、分享和剪贴板迁移订阅。加固局域网订阅互传、超大或损坏内容处理，以及导入后首页的状态同步。
- 新增俄语、印地语、西班牙语、阿拉伯语和韩语。修复定时更新间隔在窄屏上的换行，并在「关于」中添加 GitHub 仓库入口。
- 临时导入链接留在内存中。外部快捷方式更改 VPN 状态前需要应用内确认。运行配置仅在内核校验或载入时短暂写盘，加密存储校验同时检查记录归属和完整性。
- 集成 #36 的 VPN 生命周期和跨进程状态修复，改善快速启停、网络切换后的恢复和健康检查取消。加固订阅格式、体积和压缩输入处理，离线备份预览保持离线。修复设置搜索、失效策略组和安装确认。 [PR #36](https://github.com/sundaysebasidian-byte/weave-client/pull/36)

## 性能实测

以下为进入本版的特定工作负载的既有基准，结果不外推到整机内存、耗电或运营商 DNS 延迟。

| 场景 | 优化前 | 优化后 |
| --- | ---: | ---: |
| 10,000 节点排序 | 26.44 ms | 6.72 ms |
| 10,000 节点排序的线程分配量 | 13.85 MiB | 0.92 MiB |
| 2 MiB 运行配置文件处理 | 18.64 ms | 7.78 ms |
| Android ARM64 模拟器语言选择加载 | 15.76 ms | 6.73 ms |

[诊断、导入和语言](https://github.com/sundaysebasidian-byte/weave-client/pull/32) · [隐私加固](https://github.com/sundaysebasidian-byte/weave-client/pull/33) · [订阅与分流基准](https://github.com/sundaysebasidian-byte/weave-client/pull/34) · [Android 连接和语言基准](https://github.com/sundaysebasidian-byte/weave-client/pull/35)
