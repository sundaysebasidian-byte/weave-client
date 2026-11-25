package io.weave.client.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.weave.client.domain.WeaveLanguage
import io.weave.client.subscription.ExternalImport
import io.weave.client.ui.theme.WeaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExternalImportOwnershipUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun newerPreviewLooksIdleAndCanBeDismissedWhileOlderImportRuns() {
        var dismissals = 0
        var confirmations = 0
        compose.setContent {
            CompositionLocalProvider(LocalWeaveLanguage provides WeaveLanguage.SIMPLIFIED_CHINESE) {
                WeaveTheme {
                    ExternalImportDialog(
                        value = ExternalImport.Inline("synthetic subscription"),
                        running = ExternalImportPreviewOwner.isRunning("B", "A", true),
                        blockedByOtherImport = ExternalImportPreviewOwner.isBlockedByOtherImport("B", "A", true),
                        error = null,
                        onDismiss = { dismissals++ },
                        onConfirm = { confirmations++ },
                    )
                }
            }
        }

        compose.onNodeWithText("正在校验").assertDoesNotExist()
        compose.onNodeWithText("导入").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(0, confirmations)
        }
    }
}
