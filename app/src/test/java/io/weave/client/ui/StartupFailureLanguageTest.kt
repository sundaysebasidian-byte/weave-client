package io.weave.client.ui

import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupFailureLanguageTest {
    @Test fun `startup stage is preserved without breaking error localization`() {
        val messages = listOf(
            "代理启动失败，VPN 未连接；请查看恢复中心的最近失败记录",
            "DNS 配置未通过校验，请检查自定义 DNS 地址或重新选择预设",
            "系统 VPN 路由尚未就绪，请关闭其他 VPN 后重新连接",
            "订阅节点参数未通过内核校验，请更新订阅或检查协议、端口及认证字段；原订阅未删除",
        )
        for (language in listOf(WeaveLanguage.ENGLISH, WeaveLanguage.FRENCH, WeaveLanguage.GERMAN)) {
            for (message in messages) {
                val translated = localizeWeaveText("$message [S03]", language)
                assertTrue(translated.endsWith("[S03]"))
                assertFalse(translated, translated.any { it.code in 0x4E00..0x9FFF })
            }
        }
    }
}
