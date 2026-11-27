package io.weave.client.ui

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PackagedAppLocaleIntegrationTest {
    @Test fun apkCatalogsPassAdmissionAndRenderRegionalCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locales = PackagedAppLocale.availableChoices(context, active = null).associateBy { it.tag }
        val expected = mapOf(
            "ar-EG" to View.LAYOUT_DIRECTION_RTL,
            "es-MX" to View.LAYOUT_DIRECTION_LTR,
            "ko-KR" to View.LAYOUT_DIRECTION_LTR,
            "ru-RU" to View.LAYOUT_DIRECTION_LTR,
            "hi-IN" to View.LAYOUT_DIRECTION_LTR,
        )
        assertEquals(expected.keys, locales.keys)
        for ((tag, direction) in expected) {
            val locale = checkNotNull(PackagedAppLocale.readChoice(context, locales.getValue(tag)))
            val configuration = locale.wrap(context).resources.configuration
            assertEquals(tag, configuration.locales[0].toLanguageTag())
            assertEquals(tag, direction, configuration.layoutDirection)
            for (key in translationTable(WeaveLanguage.ENGLISH).keys) {
                assertNotNull("$tag lacks $key", locale.copy.renderer.lookupStatic(key))
            }
            val label = resolveAppOwnedCopy("传输完成", WeaveLanguage.ENGLISH, staged = null, released = locale.copy)
            assertEquals(
                tag,
                when (tag) {
                    "ar-EG" -> "اكتمل النقل"
                    "es-MX" -> "Transferencia completada"
                    "ru-RU" -> "Передача завершена"
                    "hi-IN" -> "ट्रांसफ़र पूरा हुआ"
                    else -> "전송 완료"
                },
                label,
            )
        }
    }
}
