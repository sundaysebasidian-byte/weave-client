package io.weave.client.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.R
import io.weave.client.apps.InstalledApp
import io.weave.client.domain.WeaveLanguage
import io.weave.client.ui.theme.WeaveTheme
import java.io.File
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Uses only test-APK locale data and fixture rows. No real profiles or installed apps are read. */
class StagedLocaleDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val pickerTags = listOf("ar-EG", "es-MX", "ko-KR")
    private val fixturePackage = "io.weave.synthetic.atlas_2026"
    private val fixtureName = "Atlas · المختبر التجريبي 2026 · atlas reports"
    private lateinit var catalogs: Map<String, StagedComposeLocale>

    @Before fun readTestAssets() {
        assumeTrue("Use the isolated Weave acceptance UID", target.packageName.endsWith(".acceptance"))
        catalogs = pickerTags.associateWith { tag ->
            val language = Locale.forLanguageTag(tag).language
            val copy = instrumentation.context.assets.open("$language.jsonl").bufferedReader().useLines { lines ->
                lines.associate { line ->
                    val row = JSONObject(line)
                    row.getString("id") to row.getString("text")
                }
            }
            StagedComposeLocale(language, copy)
        }
    }

    @Test fun explicitArabicMirrorsTheRealPickerAndSettingsOverrideChangesWithoutFlippingPackageId() {
        val selection = mutableStateOf<String?>(null)
        val systemTag = "ko-KR" // Emulate an independently set system configuration without mutating the OS.
        val selectedPackages = mutableListOf<String>()
        compose.setContent {
            val tag = selection.value ?: systemTag
            val resources = remember(tag) { resourcesFor(tag) }
            val density = LocalDensity.current
            val dir = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else LayoutDirection.Ltr
            CompositionLocalProvider(
                LocalResources provides resources,
                LocalLayoutDirection provides dir,
                LocalStagedWeaveLocale provides catalogs.getValue(tag),
                LocalDensity provides Density(density = density.density, fontScale = 1.4f),
            ) {
                WeaveTheme {
                    AppPickerDialog(
                        apps = listOf(InstalledApp(fixturePackage, fixtureName, "A", 0xFFE3F0FF)),
                        onDismiss = {},
                        onSelect = { selectedPackages += it },
                    )
                }
            }
        }

        for (tag in listOf("ko-KR", "ar-EG", "es-MX")) {
            compose.runOnIdle { selection.value = if (tag == systemTag) null else tag }
            val resources = resourcesFor(tag)
            compose.onNodeWithText(resources.getString(R.string.app_picker_title)).assertIsDisplayed()
            val add = compose.onNodeWithContentDescription(
                resources.getString(R.string.app_picker_add, fixtureName), useUnmergedTree = true,
            ).assertIsDisplayed()
            val label = compose.onNodeWithText(fixtureName, useUnmergedTree = true).assertIsDisplayed()
            val addX = add.fetchSemanticsNode().boundsInRoot.center.x
            val labelX = label.fetchSemanticsNode().boundsInRoot.center.x
            assertTrue(tag, if (tag == "ar-EG") addX < labelX else addX > labelX)

            val textLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(fixturePackage, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(textLayouts) }
            assertEquals(ResolvedTextDirection.Ltr, textLayouts.single().getBidiRunDirection(0))
            val directory = File(target.getExternalFilesDir(null), "qa/staged-locales").apply { mkdirs() }
            File(directory, "picker-$tag.png").outputStream().use {
                assertTrue(compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        compose.onNodeWithText(fixtureName).performClick()
        compose.runOnIdle { assertEquals(listOf(fixturePackage), selectedPackages) }
        compose.runOnIdle { selection.value = null }
        compose.onNodeWithText(resourcesFor(systemTag).getString(R.string.app_picker_title)).assertIsDisplayed()
    }

    @Test fun previewOverrideIsReloadedFromATestOnlyStoreAndMergedLabelsKeepTheirPriority() {
        // This checks fresh preview initialization, not persistence of a not-yet-enabled app preference.
        val prefs = target.getSharedPreferences("qa_preview_language_not_product", Context.MODE_PRIVATE)
        val canonical = "确认导入"
        val requested = "es-MX"
        assertTrue(prefs.edit().putString("picked-preview-language", requested).commit())
        try {
            val recoveredTarget = target.createConfigurationContext(Configuration(target.resources.configuration))
            val recoveredTag = recoveredTarget
                .getSharedPreferences("qa_preview_language_not_product", Context.MODE_PRIVATE)
                .getString("picked-preview-language", null)
            assertEquals(requested, recoveredTag)
            val copy = catalogs.getValue(requireNotNull(recoveredTag))
            val localized = copy.static(canonical)
            compose.setContent {
                CompositionLocalProvider(
                    LocalWeaveLanguage provides WeaveLanguage.JAPANESE,
                    LocalStagedWeaveLocale provides copy,
                    LocalResources provides resourcesFor(recoveredTag),
                ) {
                    WeaveTheme {
                        Column {
                            Text(canonical)
                            Text(canonical, translate = false)
                        }
                    }
                }
            }
            compose.onNodeWithText(localized).assertIsDisplayed()
            compose.onNodeWithText(canonical).assertIsDisplayed()
        } finally {
            prefs.edit().clear().commit()
        }
    }


    @Test fun longRecoveryCopyRendersAndWrapsAtLargeFontSizes() {
        val selection = mutableStateOf("ko-KR")
        val copyKey = "配置读取或恢复未完成。原数据和恢复记录已保留，应用不会重置配置。请检查可用存储空间后重试；若持续失败，请保留应用数据以便修复。"
        compose.setContent {
            val selected = selection.value
            val density = LocalDensity.current
            val resources = remember(selected) { resourcesFor(selected) }
            CompositionLocalProvider(
                LocalResources provides resources,
                LocalStagedWeaveLocale provides catalogs.getValue(selected),
                LocalLayoutDirection provides LayoutDirection.Ltr,
                LocalDensity provides Density(density = density.density, fontScale = 1.4f),
            ) {
                WeaveTheme {
                    ProfileRecoveryScreen(failed = true, onRetry = {}, onVpnSettings = {}, onClose = {})
                }
            }
        }
        for (tag in listOf("ko-KR", "es-MX")) {
            compose.runOnIdle { selection.value = tag }
            val copy = catalogs.getValue(tag).static(copyKey)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(copy, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            val layout = layouts.single()
            assertTrue(tag, layout.lineCount > 1 && !layout.didOverflowWidth)
        }
    }

    private fun resourcesFor(tag: String) =
        target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }).resources
}
