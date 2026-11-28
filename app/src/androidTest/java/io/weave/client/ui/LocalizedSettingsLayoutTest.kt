package io.weave.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.weave.client.domain.DashboardState
import io.weave.client.domain.RoutingMode
import io.weave.client.domain.WeaveLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocalizedSettingsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longTranslatedValuesWrapBelowTheLabelWithoutEllipsis() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val choices = PackagedAppLocale.availableChoices(context, active = null)
        val selected = mutableStateOf(checkNotNull(PackagedAppLocale.readChoice(context, choices.first())))
        val sourceLabel = "引导 DNS"
        val sourceValue = "中国大陆（阿里 / 腾讯）"
        compose.setContent {
            FixtureShell(language = WeaveLanguage.ENGLISH, fontScale = 2f) {
                val locale = selected.value
                CompositionLocalProvider(
                    LocalReleasedAppCopy provides locale.copy,
                    LocalLayoutDirection provides if (locale.tag == "ar-EG") LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Box(Modifier.requiredWidth(320.dp)) {
                        LinkSetting(Icons.Rounded.Language, sourceLabel, sourceValue) {}
                    }
                }
            }
        }
        for (option in choices) {
            val locale = checkNotNull(PackagedAppLocale.readChoice(context, option))
            compose.runOnIdle { selected.value = locale }
            val title = localizeStagedWeaveText(sourceLabel, locale.copy.renderer)
            val value = localizeStagedWeaveText(sourceValue, locale.copy.renderer)
            val labelNode = compose.onNodeWithText(title, useUnmergedTree = true)
            val valueNode = compose.onNodeWithText(value, useUnmergedTree = true)
            val textResults = mutableListOf<TextLayoutResult>()
            valueNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(textResults)) }
            val layout = textResults.single()
            assertFalse("${option.tag}: value overflow", layout.hasVisualOverflow)
            assertFalse("${option.tag}: value ellipsized", (0 until layout.lineCount).any(layout::isLineEllipsized))
            if (option.tag in setOf("ar-EG", "ru-RU", "hi-IN")) {
                assertTrue("${option.tag}: value must have its own row",
                    valueNode.fetchSemanticsNode().boundsInRoot.top >= labelNode.fetchSemanticsNode().boundsInRoot.bottom)
            }
        }
    }

    @Test fun narrowDockAvoidsBrokenWordsAndKeepsLocalizedNavigationActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = checkNotNull(PackagedAppLocale.readChoice(context, PackagedLocaleChoice("ru-RU", "Русский")))
        val active = mutableStateOf(Destination.HOME)
        val renderer = locale.copy.renderer
        val mode = localizeStagedWeaveText(RoutingMode.RULE.label, renderer)
        val exit = localizeStagedWeaveText("尚未选择出口", renderer)
        val dashboard = DashboardState(routingMode = RoutingMode.RULE, hasUsableSubscription = false)
        compose.setContent {
            FixtureShell(language = WeaveLanguage.ENGLISH, fontScale = 2f) {
                CompositionLocalProvider(LocalReleasedAppCopy provides locale.copy) {
                    Column(Modifier.requiredWidth(320.dp)) {
                        StatusBlock(dashboard, dashboard)
                        ConnectionCapsule(dashboard, dashboard, {}, {}, {}, {})
                        WeaveNavigationDock(
                            destinations = Destination.entries,
                            selected = active.value,
                            highlighted = active.value.ordinal,
                            indicatorPosition = { active.value.ordinal.toFloat() },
                            onSelect = { active.value = it },
                            onScrub = {},
                            onScrubEnd = {},
                        )
                    }
                }
            }
        }
        val summary = compose.onNodeWithText("$exit · $mode", useUnmergedTree = true)
        val summaryResults = mutableListOf<TextLayoutResult>()
        summary.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(summaryResults)) }
        val summaryLayout = summaryResults.single()
        assertFalse(
            "Summary overflow at ${summaryLayout.size}, width=${summaryLayout.didOverflowWidth}, height=${summaryLayout.didOverflowHeight}",
            summaryLayout.hasVisualOverflow,
        )
        val chooser = localizeStagedWeaveText("选择出口", renderer)
        compose.onNodeWithText(chooser).assertIsDisplayed()
        for (tab in Destination.entries) {
            val label = localizeStagedWeaveText(tab.label, renderer)
            compose.onNodeWithText(label).assertDoesNotExist()
            compose.onNode(hasTestTag("navigation-tab-${tab.name}") and hasContentDescription(label))
                .performClick()
                .assertIsSelected()
        }
    }
}
