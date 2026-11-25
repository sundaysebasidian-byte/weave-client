package io.weave.client.ui

import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.R
import io.weave.client.apps.InstalledApp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerLocaleAndIconTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun packagedPickerAndQuantityResourcesResolveWithoutUsingSystemLocale() {
        val labels = mapOf("ko-KR" to "앱 선택", "es-MX" to "Elegir una aplicación", "ar-EG" to "اختيار تطبيق")
        for ((tag, title) in labels) {
            val explicit = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val resources = context.createConfigurationContext(explicit).resources
            assertEquals(title, resources.getString(R.string.app_picker_title))
            assertEquals(if (tag == "ar-EG") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                resources.configuration.layoutDirection)
            val label = "Atlas 12"
            val add = resources.getString(R.string.app_picker_add, label)
            assertTrue(add, label in add)
            if (tag == "ar-EG") assertTrue(add, "\u2068$label\u2069" in add)
            for (n in listOf(0, 1, 2, 3, 11, 100, 101)) {
                val text = resources.getQuantityString(R.plurals.update_failed_body, n, n)
                assertTrue(text, text.any(Char::isDigit) && !text.contains("subscription could") && !text.contains("subscriptions could"))
            }
        }
    }

    @Test fun rendersOwnLauncherActivityAndFallsBackForAWithdrawnPackage() {
        // Do not enumerate or capture the owner's apps; the target APK is the only real fixture.
        val manager = context.packageManager
        val activity = manager.getLaunchIntentForPackage(context.packageName)?.component
        assertNotNull(activity)
        val app = InstalledApp(context.packageName, "Weave", "W", 0, launcherActivity = activity!!.className)
        for (row in listOf(app, app.copy(launcherActivity = null))) {
            val image = loadPickerIcon(manager, row, 96)?.asAndroidBitmap()
            assertNotNull(image)
            assertEquals(96, image!!.width)
            assertEquals(96, image.height)
            val pixels = IntArray(96 * 96)
            image.getPixels(pixels, 0, 96, 0, 0, 96, 96)
            assertTrue(pixels.any { Color.alpha(it) != 0 })
        }
        val absent = app.copy(packageName = "invalid.weave.absent", launcherActivity = null)
        assertNull(loadPickerIcon(manager, absent, 96))
    }
}
