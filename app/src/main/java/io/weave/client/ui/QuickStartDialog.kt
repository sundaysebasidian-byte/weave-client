package io.weave.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.weave.client.ui.theme.LocalWeavePalette
import kotlin.math.PI
import kotlin.math.sin

private const val STEP_COUNT = 3
private const val SLIDE_DIVISOR = 10

/** Phones fill the window; at this size the guide becomes a bounded card over a scrim. */
private val WideMinWidth = 600.dp
private val WideMinHeight = 480.dp
private val ContentMaxWidth = 560.dp
private val WideCardMargin = 24.dp
private val VignetteHeight = 88.dp

private class GuideState(
    val hasNodes: Boolean,
    val hasProxyTarget: Boolean,
    val connected: Boolean,
    val connecting: Boolean,
    val connectionError: Boolean,
    val reducedMotion: Boolean,
)

/**
 * Optional three-stage quick start: IMPORT -> NODE -> CONNECT.
 *
 * Pure presentation. Every callback fires from an explicit tap or system back; nothing is invoked
 * from an effect, no permission is requested here, and stage changes never start a connection.
 * The caller hides this dialog while its own import/node dialogs run and owns all persistence.
 */
@Composable
internal fun QuickStartDialog(
    step: QuickStartStep,
    hasNodes: Boolean,
    hasProxyTarget: Boolean,
    connected: Boolean,
    connecting: Boolean,
    reducedMotion: Boolean,
    connectionError: Boolean,
    onReducedMotionChange: (Boolean) -> Unit,
    onPrimaryAction: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val state = GuideState(hasNodes, hasProxyTarget, connected, connecting, connectionError, reducedMotion)

    Dialog(
        // System back steps backwards; only the first stage closes the guide.
        onDismissRequest = { if (step == QuickStartStep.IMPORT) onDismiss() else onBack() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            // The window is full size, so "outside" is the scrim drawn below on large screens.
            dismissOnClickOutside = false,
        ),
    ) {
        val colors = MaterialTheme.colorScheme
        val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val wide = maxWidth >= WideMinWidth && maxHeight >= WideMinHeight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (wide) Modifier.background(Color.Black.copy(alpha = 0.40f)) else Modifier)
                    .pointerInput(Unit) { detectTapGestures { currentOnDismiss() } },
            )
            val cardShape = RoundedCornerShape(WeaveUiTokens.panelRadius + 4.dp)
            val cardModifier = if (wide) {
                Modifier
                    .align(Alignment.Center)
                    .padding(safeInsets)
                    .padding(WideCardMargin)
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .clip(cardShape)
                    .border(WeaveUiTokens.panelBorderWidth, colors.outlineVariant, cardShape)
            } else {
                Modifier.fillMaxSize()
            }
            Box(
                modifier = cardModifier
                    .testTag("quick-start")
                    .background(colors.background)
                    // Swallow taps on the card so only the scrim dismisses.
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                MonetAtmosphere(palette = LocalWeavePalette.current, modifier = Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(if (wide) Modifier else Modifier.padding(safeInsets))
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxSize(),
                ) {
                    GuideTopBar(step = step, onBack = onBack, onSkip = onDismiss)
                    AnimatedContent(
                        targetState = step,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        transitionSpec = {
                            if (reducedMotion) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (
                                    fadeIn(WeaveMotion.standard()) +
                                        slideInHorizontally(WeaveMotion.standard()) { direction * it / SLIDE_DIVISOR }
                                    ) togetherWith (
                                    fadeOut(WeaveMotion.standard()) +
                                        slideOutHorizontally(WeaveMotion.standard()) { -direction * it / SLIDE_DIVISOR }
                                    )
                            }
                        },
                        label = "quick-start-step",
                    ) { pageStep ->
                        // A page that is entering or leaving ignores touches, so a fast double tap
                        // can never land on the next stage's button.
                        val settled = transition.currentState == EnterExitState.Visible &&
                            transition.targetState == EnterExitState.Visible
                        StepPage(
                            step = pageStep,
                            state = state,
                            onReducedMotionChange = { if (settled) onReducedMotionChange(it) },
                            onPrimaryAction = { if (settled) onPrimaryAction() },
                            onNext = { if (settled) onNext() },
                            onDismiss = { if (settled) onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTopBar(step: QuickStartStep, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.minTouchTarget)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step != QuickStartStep.IMPORT) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(WeaveUiTokens.headerActionSize)
                    .testTag("quick-start-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = localizedContentDescription("返回"),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .heightIn(min = WeaveUiTokens.minTouchTarget)
                .testTag("quick-start-skip"),
        ) {
            Text(
                "跳过",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/**
 * One stage. The whole page scrolls, and the footer is pushed to the bottom only when there is
 * spare room, so at 200% font the buttons simply follow the copy instead of being clipped.
 */
@Composable
private fun StepPage(
    step: QuickStartStep,
    state: GuideState,
    onReducedMotionChange: (Boolean) -> Unit,
    onPrimaryAction: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(horizontal = WeaveUiTokens.screenHorizontal),
        ) {
            StepPath(current = step.ordinal)
            Spacer(Modifier.height(WeaveUiTokens.sectionGap))
            WeaveThread(
                step = step.ordinal,
                complete = step == QuickStartStep.CONNECT && state.connected,
            )
            Spacer(Modifier.height(24.dp))
            when (step) {
                QuickStartStep.IMPORT -> ImportBody(state)
                QuickStartStep.NODE -> NodeBody(state)
                QuickStartStep.CONNECT -> ConnectBody(state)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))
            GuideFooter(
                step = step,
                state = state,
                onPrimaryAction = onPrimaryAction,
                onNext = onNext,
                onDismiss = onDismiss,
            )
            MotionToggle(reducedMotion = state.reducedMotion, onChange = onReducedMotionChange)
            Spacer(Modifier.height(WeaveUiTokens.screenBottom))
        }
    }
}

@Composable
private fun StepHeading(step: QuickStartStep, title: String, description: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        // Digits only, so it needs no translation.
        "0${step.ordinal + 1} / 0$STEP_COUNT",
        color = colors.secondary,
        style = MaterialTheme.typography.labelLarge.merge(TabularNumbers).copy(letterSpacing = 1.sp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(10.dp))
    Text(description, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ImportBody(state: GuideState) {
    StepHeading(
        step = QuickStartStep.IMPORT,
        title = if (state.hasNodes) "订阅已准备好" else "导入你的订阅",
        description = if (state.hasNodes) "已有可用节点，可以继续。"
            else "订阅由你提供，支持链接、二维码或文件。",
    )
    if (state.hasNodes) {
        GuideNotice(icon = Icons.Rounded.CheckCircle, title = "已有可用节点", tone = NoticeTone.Positive)
        Spacer(Modifier.height(WeaveUiTokens.sectionGap))
    }
    GuidePanel {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("链接" to Glyph.LINK, "二维码" to Glyph.QR, "文件" to Glyph.FILE).forEach { (label, glyph) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        GlyphIcon(glyph, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NodeBody(state: GuideState) {
    StepHeading(QuickStartStep.NODE, "选择一个节点", "选一条代理线路，之后随时可以更换。")
    GuideNotice(icon = if (state.hasProxyTarget) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
        title = if (state.hasProxyTarget) "已选好节点" else "还没有选择节点",
        tone = if (state.hasProxyTarget) NoticeTone.Positive else NoticeTone.Neutral, live = true)
    Spacer(Modifier.height(WeaveUiTokens.sectionGap))
    GuidePanel {
        GuideRow("不确定选哪个？", "也可以使用自动选择。") {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(WeaveUiTokens.iconSize))
        }
    }
}

@Composable
private fun ConnectBody(state: GuideState) {
    StepHeading(
        step = QuickStartStep.CONNECT,
        title = if (state.connected) "一切就绪" else "准备连接",
        description = if (state.connected) {
            "已连接，现在可以开始使用。需要时可以随时断开。"
        } else {
            "点击连接时，按需确认系统 VPN 授权。"
        },
    )
    when {
        state.connected -> {
            GuideNotice(icon = Icons.Rounded.CheckCircle, title = "已连接", tone = NoticeTone.Positive, live = true)
            Spacer(Modifier.height(WeaveUiTokens.sectionGap))
        }
        state.connecting -> {
            GuideNotice(icon = Icons.Rounded.Info, title = "正在连接…", tone = NoticeTone.Neutral, live = true)
            Spacer(Modifier.height(WeaveUiTokens.sectionGap))
        }
        state.connectionError -> {
            GuideNotice(
                icon = Icons.Rounded.Warning,
                title = "这次没有连上",
                detail = "可以重试、返回更换节点，或稍后再连接。",
                tone = NoticeTone.Gentle,
                live = true,
            )
            Spacer(Modifier.height(WeaveUiTokens.sectionGap))
        }
    }
    GuideNotice(icon = Icons.Rounded.Lock, title = "权限在使用时确认",
        detail = "VPN 用于连接；相机仅扫码；通知可稍后设置。", tone = NoticeTone.Neutral)
}

@Composable
private fun GuideFooter(
    step: QuickStartStep,
    state: GuideState,
    onPrimaryAction: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reduced = state.reducedMotion
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WeaveUiTokens.itemGap),
    ) {
        when (step) {
            QuickStartStep.IMPORT -> if (state.hasNodes) {
                GuideButton("下一步", onNext, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-primary"))
                GuideTextAction("导入其他订阅", onPrimaryAction, Modifier.testTag("quick-start-secondary"))
            } else {
                GuideButton("导入订阅", onPrimaryAction, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-primary"))
            }

            QuickStartStep.NODE -> if (state.hasProxyTarget) {
                // Once a node is chosen the natural move is forward; choosing again stays available.
                GuideButton("下一步", onNext, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-next"))
                GuideButton("选择节点", onPrimaryAction, ButtonTone.Outlined, reduced, Modifier.testTag("quick-start-primary"))
            } else {
                GuideButton("选择节点", onPrimaryAction, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-primary"))
            }

            QuickStartStep.CONNECT -> when {
                state.connected ->
                    GuideButton("开始使用", onDismiss, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-primary"))
                state.connecting ->
                    GuideButton(
                        "正在连接…", {}, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-primary"),
                        enabled = false, busy = true,
                    )
                else ->
                    GuideButton("连接", onPrimaryAction, ButtonTone.Filled, reduced, Modifier.testTag("quick-start-primary"))
            }
        }
    }
}

@Composable
private fun MotionToggle(reducedMotion: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .heightIn(min = WeaveUiTokens.minTouchTarget)
            .clip(RoundedCornerShape(WeaveUiTokens.iconTileRadius))
            .toggleable(value = reducedMotion, role = Role.Switch, onValueChange = onChange)
            .testTag("quick-start-motion")
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "减少动效",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = reducedMotion, onCheckedChange = null)
    }
}

// region Progress path and vignette

/** Small three-part path: finished stages are green and checked, the current one is solid. */
@Composable
private fun StepPath(current: Int) {
    val labels = listOf("导入", "节点", "连接")
    val description = localizedContentDescription(
        when (current) {
            0 -> "快速开始进度：第 1 步，共 3 步"
            1 -> "快速开始进度：第 2 步，共 3 步"
            else -> "快速开始进度：第 3 步，共 3 步"
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        labels.forEachIndexed { index, label ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PathMarker(index = index, current = current)
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    textAlign = TextAlign.Center,
                    color = if (index == current) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PathMarker(index: Int, current: Int) {
    val colors = MaterialTheme.colorScheme
    val done = colors.secondary
    val idle = colors.outlineVariant
    val solid = colors.primary
    val paper = colors.background
    val outline = colors.outline
    val onDone = colors.onSecondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val lineWidth = 2.dp.toPx()
        if (index > 0) {
            drawLine(if (index <= current) done else idle, Offset(0f, cy), Offset(cx, cy), lineWidth)
        }
        if (index < STEP_COUNT - 1) {
            drawLine(if (index < current) done else idle, Offset(cx, cy), Offset(size.width, cy), lineWidth)
        }
        val center = Offset(cx, cy)
        when {
            index < current -> {
                drawCircle(done, 9.dp.toPx(), center)
                drawCheck(center, 9.dp.toPx(), onDone, 1.8.dp.toPx())
            }
            index == current -> {
                drawCircle(solid.copy(alpha = 0.18f), 12.dp.toPx(), center)
                drawCircle(solid, 6.dp.toPx(), center)
            }
            else -> {
                drawCircle(paper, 6.dp.toPx(), center)
                drawCircle(outline, 6.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/**
 * Vignette: two strands cross three times on their way across the panel, one knot per stage.
 * Progress colors the thread up to the current knot; a finished connection completes it. Static
 * drawing, no animation of its own.
 */
@Composable
private fun WeaveThread(step: Int, complete: Boolean) {
    val colors = MaterialTheme.colorScheme
    val strandA = colors.primary
    val strandB = colors.secondary
    val faint = colors.outlineVariant
    val outline = colors.outline
    val paper = colors.surface
    val warm = colors.tertiary
    val onPrimary = colors.onPrimary
    val onSecondary = colors.onSecondary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.panelRadius),
        color = colors.surface,
        border = BorderStroke(WeaveUiTokens.panelBorderWidth, colors.outlineVariant),
        shadowElevation = WeaveUiTokens.panelElevation,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(VignetteHeight)
                .padding(horizontal = 16.dp),
        ) {
            val w = size.width
            val h = size.height
            val midY = h / 2f
            // Crossings sit every half period; knots use every second one (18%, 50%, 82%).
            val startX = w * 0.02f
            val half = w * 0.16f
            val endX = startX + half * 6f
            val amplitude = h * 0.27f
            val threadStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

            fun strand(sign: Float): Path = Path().apply {
                val segments = 120
                for (i in 0..segments) {
                    val x = startX + (endX - startX) * i / segments
                    val y = midY + sign * amplitude * sin(PI.toFloat() * (x - startX) / half)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }

            val upper = strand(1f)
            val lower = strand(-1f)
            drawPath(upper, faint, style = threadStroke)
            drawPath(lower, faint, style = threadStroke)

            val knots = floatArrayOf(w * 0.18f, w * 0.50f, w * 0.82f)
            val reach = if (complete) endX else knots[step.coerceIn(0, knots.lastIndex)]
            clipRect(left = 0f, top = 0f, right = reach, bottom = h) {
                drawPath(lower, strandB, style = threadStroke)
                drawPath(upper, strandA, style = threadStroke)
            }

            knots.forEachIndexed { index, x ->
                val center = Offset(x, midY)
                val finished = complete || index < step
                val current = !finished && index == step
                if (current) {
                    val glow = 36.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(warm.copy(alpha = 0.30f), Color.Transparent),
                            center = center,
                            radius = glow,
                        ),
                        radius = glow,
                        center = center,
                    )
                }
                drawCircle(paper, 13.dp.toPx(), center)
                when {
                    finished -> {
                        drawCircle(strandB, 9.dp.toPx(), center)
                        drawCheck(center, 9.dp.toPx(), onSecondary, 2.dp.toPx())
                    }
                    current -> {
                        drawCircle(strandA, 9.dp.toPx(), center)
                        drawCircle(onPrimary, 3.dp.toPx(), center)
                    }
                    else -> drawCircle(outline, 8.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawCheck(center: Offset, radius: Float, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(center.x - radius * 0.42f, center.y + radius * 0.02f)
        lineTo(center.x - radius * 0.12f, center.y + radius * 0.34f)
        lineTo(center.x + radius * 0.46f, center.y - radius * 0.30f)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private enum class Glyph { LINK, QR, FILE }

/** Small hand-drawn glyphs for ways to import; the core icon set has none that fit. */
@Composable
private fun GlyphIcon(glyph: Glyph, tint: Color) {
    Canvas(modifier = Modifier.size(WeaveUiTokens.iconSize)) {
        val u = size.minDimension / 20f
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            Glyph.LINK -> rotate(-45f, Offset(size.width / 2f, size.height / 2f)) {
                drawRoundRect(
                    tint, Offset(1.5f * u, 7f * u), Size(10f * u, 6f * u),
                    CornerRadius(3f * u), style = stroke,
                )
                drawRoundRect(
                    tint, Offset(8.5f * u, 7f * u), Size(10f * u, 6f * u),
                    CornerRadius(3f * u), style = stroke,
                )
            }

            Glyph.QR -> {
                val finder = Size(6.5f * u, 6.5f * u)
                val dot = Size(2.3f * u, 2.3f * u)
                listOf(1.5f to 1.5f, 12f to 1.5f, 1.5f to 12f).forEach { (x, y) ->
                    drawRoundRect(tint, Offset(x * u, y * u), finder, CornerRadius(1.2f * u), style = stroke)
                    drawRect(tint, Offset((x + 2.1f) * u, (y + 2.1f) * u), dot)
                }
                drawRect(tint, Offset(12.5f * u, 12.5f * u), Size(2.5f * u, 2.5f * u))
                drawRect(tint, Offset(16f * u, 16f * u), Size(2.5f * u, 2.5f * u))
            }

            Glyph.FILE -> {
                val page = Path().apply {
                    moveTo(4.5f * u, 2f * u)
                    lineTo(11f * u, 2f * u)
                    lineTo(16f * u, 7f * u)
                    lineTo(16f * u, 17.5f * u)
                    lineTo(4.5f * u, 17.5f * u)
                    close()
                }
                val fold = Path().apply {
                    moveTo(11f * u, 2f * u)
                    lineTo(11f * u, 7f * u)
                    lineTo(16f * u, 7f * u)
                }
                drawPath(page, tint, style = stroke)
                drawPath(fold, tint, style = stroke)
                drawLine(tint, Offset(7.5f * u, 11.5f * u), Offset(13f * u, 11.5f * u), 1.6f * u, StrokeCap.Round)
                drawLine(tint, Offset(7.5f * u, 14.5f * u), Offset(11f * u, 14.5f * u), 1.6f * u, StrokeCap.Round)
            }
        }
    }
}

// endregion

// region Shared pieces

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun GuidePanel(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(WeaveUiTokens.compactPanelRadius),
        color = colors.surface,
        border = BorderStroke(WeaveUiTokens.panelBorderWidth, colors.outlineVariant),
        shadowElevation = WeaveUiTokens.panelElevation,
    ) {
        Column { content() }
    }
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = WeaveUiTokens.rowHorizontal),
        thickness = WeaveUiTokens.panelBorderWidth,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Icon tile, title and a line of copy. Text wraps freely; nothing here has a fixed height. */
@Composable
private fun GuideRow(title: String, body: String, glyph: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.rowMinHeight)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = WeaveUiTokens.rowHorizontal, vertical = WeaveUiTokens.rowVertical),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(WeaveUiTokens.iconTile)
                .clip(RoundedCornerShape(WeaveUiTokens.iconTileRadius))
                .background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSecondaryContainer) { glyph() }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class NoticeTone { Positive, Neutral, Gentle }

@Composable
private fun GuideNotice(
    icon: ImageVector,
    title: String,
    tone: NoticeTone,
    detail: String? = null,
    live: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (tone) {
        NoticeTone.Positive -> colors.secondaryContainer
        NoticeTone.Neutral -> colors.surfaceVariant
        NoticeTone.Gentle -> colors.tertiary.copy(alpha = 0.14f)
    }
    val content = when (tone) {
        NoticeTone.Positive -> colors.onSecondaryContainer
        NoticeTone.Neutral -> colors.onSurfaceVariant
        NoticeTone.Gentle -> colors.onSurface
    }
    val iconTint = if (tone == NoticeTone.Gentle) colors.tertiary else content
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WeaveUiTokens.compactPanelRadius))
            .background(container)
            .then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(WeaveUiTokens.iconSize),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = content, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(detail, color = content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private enum class ButtonTone { Filled, Outlined }

/** Full-width action; grows with its label instead of truncating, and never sits in a row. */
@Composable
private fun GuideButton(
    label: String,
    onClick: () -> Unit,
    tone: ButtonTone,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val buttonModifier = modifier
        .fillMaxWidth()
        .heightIn(min = WeaveUiTokens.actionHeight)
        .then(if (reducedMotion) Modifier else Modifier.pressScale(interaction))
    val shape = RoundedCornerShape(WeaveUiTokens.actionRadius)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    when (tone) {
        ButtonTone.Filled -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            interactionSource = interaction,
        ) { ButtonLabel(label, showSpinner = busy && !reducedMotion) }

        ButtonTone.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = padding,
            interactionSource = interaction,
        ) { ButtonLabel(label, showSpinner = busy && !reducedMotion) }
    }
}

/**
 * The spinner is the only repeating motion, shown only while a connection is actually in flight.
 * Reduced motion drops it and keeps the text.
 */
@Composable
private fun ButtonLabel(label: String, showSpinner: Boolean) {
    if (showSpinner) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
    }
    Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun GuideTextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WeaveUiTokens.minTouchTarget),
    ) {
        Text(
            label,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// endregion
