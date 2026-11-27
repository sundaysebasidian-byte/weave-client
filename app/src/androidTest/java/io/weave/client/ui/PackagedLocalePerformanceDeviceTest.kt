package io.weave.client.ui

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class PackagedLocalePerformanceDeviceTest {
    @Test fun measureOpeningLanguageChoicesAndSelectingRussian() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weaveMeasureLocalePicker") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val labels = PackagedReleaseCopyAsset.Seal.values().map { PackagedLocaleChoice(it.tag, it.label) }

        fun decodeAll(): String {
            val copies = labels.map { checkNotNull(PackagedAppLocale.readChoice(context, it)) }
            return copies.first { it.tag == "ru-RU" }.copy.renderer.static("传输完成")
        }

        fun decodeOne(): String {
            val choices = PackagedAppLocale.availableChoices(context, active = null)
            assertEquals(labels, choices)
            return checkNotNull(PackagedAppLocale.readChoice(context, choices.first { it.tag == "ru-RU" }))
                .copy.renderer.static("传输完成")
        }

        fun measure(round: Int, label: String, block: () -> String) {
            val start = System.nanoTime()
            val seen = block()
            val elapsed = System.nanoTime() - start
            assertEquals("Передача завершена", seen)
            Log.i("WeaveLocaleBenchmark", "$round,$label,$elapsed")
        }

        repeat(2) { assertEquals(decodeAll(), decodeOne()) }
        for (round in 1..10) {
            if (round % 2 == 0) {
                measure(round, "decode-selected", ::decodeOne)
                measure(round, "decode-all", ::decodeAll)
            } else {
                measure(round, "decode-all", ::decodeAll)
                measure(round, "decode-selected", ::decodeOne)
            }
        }
    }
}
