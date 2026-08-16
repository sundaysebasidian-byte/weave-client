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
        assertEquals("Graphit", localizeWeaveText("石墨灰", WeaveLanguage.GERMAN))
        assertEquals("Pin nocturne", localizeWeaveText("夜松青", WeaveLanguage.FRENCH))
        assertEquals(
            "Neutral graphite and soft silver: restrained, clear and low-distraction",
            localizeWeaveText("中性石墨与柔银，克制、清晰、低干扰", WeaveLanguage.ENGLISH),
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
}
