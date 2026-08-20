package io.weave.client.ui

import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LanguageTest {
    @Test
    fun `six language choices are stable`() {
        assertEquals(6, WeaveLanguage.entries.size)
        assertEquals("zh-CN", WeaveLanguage.SIMPLIFIED_CHINESE.localeTag)
        assertEquals("de", WeaveLanguage.GERMAN.localeTag)
    }

    @Test
    fun `stable navigation labels translate without changing user text`() {
        assertEquals("Connect", localizeWeaveText("连接", WeaveLanguage.ENGLISH))
        assertEquals("設定", localizeWeaveText("设置", WeaveLanguage.TRADITIONAL_CHINESE))
        assertEquals("Connexion", localizeWeaveText("连接", WeaveLanguage.FRENCH))
        assertEquals("my-node", localizeWeaveText("my-node", WeaveLanguage.JAPANESE))
        assertNotEquals("连接", localizeWeaveText("连接", WeaveLanguage.JAPANESE))
    }

    @Test
    fun `node count pattern keeps runtime values`() {
        assertEquals("3 nodes total", localizeWeaveText("共 3 个节点", WeaveLanguage.ENGLISH))
        assertEquals("3 個節點", localizeWeaveText("3 个节点", WeaveLanguage.TRADITIONAL_CHINESE))
    }

    @Test
    fun `recovery status is localized and retry count is preserved`() {
        assertEquals(
            "Restoring proxy connection (attempt 2)",
            localizeWeaveText("正在恢复代理连接（第 2 次）", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Réseau rétabli ; proxy reconnecté",
            localizeWeaveText("网络已恢复，代理已重新连接", WeaveLanguage.FRENCH),
        )
    }

    @Test
    fun `local release boundary is localized`() {
        assertEquals("Service boundary", localizeWeaveText("服务边界", WeaveLanguage.ENGLISH))
        assertEquals("Dienstgrenze", localizeWeaveText("服务边界", WeaveLanguage.GERMAN))
    }

    @Test
    fun `runtime status and accessibility copy follow the selected language`() {
        assertEquals("Delete subscription?", localizeWeaveText("删除订阅？", WeaveLanguage.ENGLISH))
        assertEquals("DNS リークテスト", localizeWeaveText("DNS 泄漏测试", WeaveLanguage.JAPANESE))
        assertEquals("3 nœuds · chiffrés localement", localizeWeaveText("3 个节点 · 本地加密保存", WeaveLanguage.FRENCH))
        assertEquals("Bestätigungscode: 123456", localizeWeaveText("确认短码：123456", WeaveLanguage.GERMAN))
        assertEquals(
            "Routing rule deleted; safely updating the runtime configuration",
            localizeWeaveText("分流规则已删除，正在安全更新运行配置", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Imported “Friends” safely",
            localizeWeaveText("已安全导入「Friends」", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Audit note: Node count dropped sharply",
            localizeWeaveText("审计提示：节点数量骤降", WeaveLanguage.ENGLISH),
        )
    }

    @Test
    fun `dynamic audit and snapshot values are preserved`() {
        assertEquals(
            "Subscription security audit passed · 2 → 3 nodes",
            localizeWeaveText("订阅安全审计通过 · 2 → 3 节点", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Latest usable snapshot: r42",
            localizeWeaveText("最近可用快照：r42", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Règle d’application : Règle locale",
            localizeWeaveText("应用规则：本地规则", WeaveLanguage.FRENCH),
        )
        assertEquals(
            "2 confirmed from local configuration · 3 require external verification",
            localizeWeaveText("2 项已从本地配置确认 · 3 项需要外部验证", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Cloudflare · DoH · Mainland / overseas split · DNS bypass protection + Local rule",
            localizeWeaveText("Cloudflare · DoH · 国内 / 海外分流 · DNS 旁路保护 + 本地规则", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "5/6 endpoints reachable · median 42 ms",
            localizeWeaveText("5/6 个端点可达 · 中位 42 ms", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "App rule · Chrome",
            localizeWeaveText("应用规则 · Chrome", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Local rule · Full domain example.com",
            localizeWeaveText("本地规则 · 完整域名 example.com", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "STUN port 3478 blocked by rule",
            localizeWeaveText("STUN 端口 3478 已按规则阻断", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "DoH encrypted resolution · Custom endpoint",
            localizeWeaveText("DoH 加密解析 · 自定义端点", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Water Lilies",
            localizeWeaveText("睡莲", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "AdGuard DNS + local rules: blocks ads, trackers and malicious domains",
            localizeWeaveText("AdGuard DNS + 本地规则：过滤广告、跟踪器与恶意域名", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "HTTPS remote subscription",
            localizeWeaveText("HTTPS 远程订阅", WeaveLanguage.ENGLISH),
        )
        assertEquals("Deep Ocean", localizeWeaveText("深海蓝", WeaveLanguage.ENGLISH))
        assertEquals("Weiß-Grün", localizeWeaveText("白绿", WeaveLanguage.GERMAN))
        assertEquals("Pin nocturne", localizeWeaveText("夜松青", WeaveLanguage.FRENCH))
        assertEquals(
            "Pure white surfaces, soft green accents and crisp dark text",
            localizeWeaveText("纯净白底、柔和青绿与清晰深色文字", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "LAN subscriptions imported; safely updating the runtime configuration",
            localizeWeaveText("局域网订阅已导入，正在安全更新运行配置", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Local routing rule deleted; safely applying changes",
            localizeWeaveText("本地路由规则已删除，正在安全应用", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "Connected · 4 subscriptions",
            localizeWeaveText("已连接 · 4 个订阅", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "node-1 is missing server address",
            localizeWeaveText("node-1 缺少服务器地址", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "The QR code does not contain a subscription URL",
            localizeWeaveText("二维码未包含订阅地址", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "The subscription server returned HTTP 403",
            localizeWeaveText("订阅服务器返回 HTTP 403", WeaveLanguage.ENGLISH),
        )
        assertEquals(
            "The candidate has fewer than a quarter of the old nodes; the old version was retained",
            localizeWeaveText("候选节点少于旧版本四分之一，旧版本已保留", WeaveLanguage.ENGLISH),
        )
    }

    @Test
    fun `new navigation migration and privacy surfaces never fall back to simplified Chinese`() {
        val sources = listOf(
            "新手模式",
            "标准模式",
            "自订导航",
            "调整底部导航的真实顺序，也可隐藏分流或订阅。连接与设置是安全入口，始终保留。",
            "恢复默认导航",
            "快速开始",
            "按顺序完成三步即可连接；高级分流不会在新手模式中后台生效。",
            "新手保护方案",
            "白绿",
            "从其他客户端迁移",
            "确认并选择文件",
            "浏览器隐私实验室",
            "运行 WebRTC 与浏览器身份检测",
            "浏览器检测超时，请重新检测",
            "重新检测",
            "WebRTC 候选",
            "浏览器身份表面",
            "当前 WebView 不支持 RTCPeerConnection，结果未知。",
            "未取得 ICE 候选。可能是 STUN 被阻止、网络超时或浏览器策略限制；不能单独据此判定无泄漏。",
            "host 数字地址会暴露本地网络表面；srflx 通常是当前 WebRTC 公网出口，必须与 VPN 出口对照后才能判断泄漏。",
            "WebView 不能代表 Chrome、Firefox 的扩展、Secure DNS 或 WebRTC 策略。以下页面会交给系统浏览器打开。",
            "这些字段组合后可能用于跨站指纹识别；本页只在本机展示，不保存唯一标识。",
            "出口交叉验证",
            "WebRTC 公网候选与 HTTPS 代理出口不一致，请检查分流或泄漏。",
            "DNS 泄漏不能仅靠本机代码准确判定；必须由独立权威 DNS 服务观察查询来源。下方外部测试才是实际 DNS 泄漏验证。",
        )
        val translatedLanguages = listOf(
            WeaveLanguage.ENGLISH,
            WeaveLanguage.JAPANESE,
            WeaveLanguage.FRENCH,
            WeaveLanguage.GERMAN,
        )
        translatedLanguages.forEach { language ->
            sources.forEach { source ->
                assertNotEquals("$language left Chinese text: $source", source, localizeWeaveText(source, language))
            }
        }
        assertEquals(
            "3 compatible clients detected · confirm and choose an exported file",
            localizeWeaveText(
                "检测到 3 个兼容客户端 · 由你确认后选择导出文件",
                WeaveLanguage.ENGLISH,
            ),
        )
        assertEquals(
            "4 advanced rules paused · switch to Standard mode to restore",
            localizeWeaveText(
                "4 项高级规则已暂停 · 切换标准模式可恢复",
                WeaveLanguage.ENGLISH,
            ),
        )
    }
}
