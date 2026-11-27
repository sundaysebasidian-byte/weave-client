package io.weave.client.ui

import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class PackagedAppLocaleIntegrationTest {
    @Test fun apkCatalogsPassAdmissionAndRenderRegionalCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locales = PackagedAppLocale.availableChoices(context, active = null).associateBy { it.tag }
        val expected = mapOf(
            "ar-EG" to View.LAYOUT_DIRECTION_RTL,
            "es-MX" to View.LAYOUT_DIRECTION_LTR,
            "ko-KR" to View.LAYOUT_DIRECTION_LTR,
        )
        assertEquals(expected.keys, locales.keys)
        for ((tag, direction) in expected) {
            val locale = locales.getValue(tag)
            val configuration = locale.wrap(context).resources.configuration
            assertEquals(tag, configuration.locales[0].toLanguageTag())
            assertEquals(tag, direction, configuration.layoutDirection)
            val label = resolveAppOwnedCopy("传输完成", WeaveLanguage.ENGLISH, staged = null, released = locale.copy)
            assertEquals(
                tag,
                when (tag) {
                    "ar-EG" -> "اكتمل النقل"
                    "es-MX" -> "Transferencia completada"
                    else -> "전송 완료"
                },
                label,
            )
        }
    }
}
