package io.weave.client.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** App content only. Keep Material's modal window, focus management, bounded body and wrapping
 * action row; native permission, document and install activities never go through this surface. */
@Composable
internal fun WeaveDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: @Composable () -> Unit,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(WeaveUiTokens.panelRadius)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        modifier = modifier.border(WeaveUiTokens.panelBorderWidth, colors.outlineVariant, shape),
        shape = shape,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = if (title == null && icon == null) null else {
            {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().semantics { heading() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        icon?.invoke()
                        if (title != null) {
                            Box(Modifier.weight(1f)) {
                                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                            }
                        }
                    }
                    HorizontalDivider(thickness = WeaveUiTokens.panelBorderWidth, color = colors.outlineVariant)
                }
            }
        },
        text = { ProvideTextStyle(MaterialTheme.typography.bodyMedium, text) },
        dismissButton = dismissButton,
        confirmButton = confirmButton,
    )
}
