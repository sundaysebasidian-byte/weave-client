package io.weave.client.ui

import io.weave.client.domain.ConnectionState
import io.weave.client.domain.DashboardState
import io.weave.client.domain.NetworkPathStatus
import io.weave.client.domain.RoutingMode

/*
 * Presentation model for the Home connection hero.
 *
 * The hero is a fixed frame: a status mark, one headline, one supporting line and one primary
 * action. Everything that varies between states is chosen here, as plain data, so the layout can
 * measure every candidate string up front (see StableTextSlot) and keep an identical outer
 * geometry whichever state is active. Longer guidance or recovery actions never enter the frame;
 * they belong to [HeroIssue], which is shown as its own surface below the hero.
 */

/** Stable label written by RouteReferenceSanitizer when a saved exit no longer exists. */
internal const val INVALID_EXIT_LABEL = "出口已失效，请重新选择"

internal enum class ExitReadiness {
    /** A valid default exit is selected. */
    READY,
    /** No exit chosen; the runtime falls back to the first usable subscription. */
    MISSING,
    /** The saved exit was deleted; strict validation requires a new choice. */
    INVALID,
    /** Direct mode ignores the default exit entirely. */
    NOT_USED,
}

internal fun exitReadiness(state: DashboardState): ExitReadiness {
    val target = state.defaultRouteTarget
    return when {
        state.routingMode == RoutingMode.DIRECT -> ExitReadiness.NOT_USED
        target == null -> ExitReadiness.MISSING
        target.label == INVALID_EXIT_LABEL -> ExitReadiness.INVALID
        else -> ExitReadiness.READY
    }
}

/**
 * Only an exit verified by the app's own check is shown as positive. An established tunnel is
 * progress, not proof that the exit works or that the network is private.
 */
internal fun pathTone(status: NetworkPathStatus): WeaveStatusTone = when (status) {
    NetworkPathStatus.VERIFIED -> WeaveStatusTone.POSITIVE
    NetworkPathStatus.TUN_READY, NetworkPathStatus.STARTING -> WeaveStatusTone.PROGRESS
    NetworkPathStatus.WAITING_NETWORK, NetworkPathStatus.RECOVERING -> WeaveStatusTone.CAUTION
    NetworkPathStatus.INACTIVE -> WeaveStatusTone.NEUTRAL
}

/** The four headlines the hero can show. One per connection state. */
internal enum class HeroHeadline(val source: String) {
    DISCONNECTED("未连接"),
    CONNECTING("正在连接"),
    CONNECTED("已连接"),
    FAILED("连接未建立"),
}

/**
 * The supporting line. Each entry is one short phrase that fits a single line at the hero's
 * narrowest supported width; anything longer is an issue surface, not hero copy.
 */
internal enum class HeroDetail(val source: String, val tone: WeaveStatusTone) {
    READY("准备就绪", WeaveStatusTone.NEUTRAL),
    EXIT_INVALID("出口已失效", WeaveStatusTone.CAUTION),
    CORE_UNAVAILABLE("内核不可用", WeaveStatusTone.CRITICAL),
    ESTABLISHING("正在建立隧道", WeaveStatusTone.NEUTRAL),
    EXIT_VERIFIED("出口已验证", WeaveStatusTone.NEUTRAL),
    /**
     * The tunnel is up but the app holds no reachability evidence yet. A plain statement of fact:
     * it does not claim the network works, does not claim it failed and does not promise that a
     * check is running. Only the user-started network check turns this into [EXIT_VERIFIED].
     */
    REACHABILITY_UNCHECKED("连通性未检测", WeaveStatusTone.NEUTRAL),
    WAITING_NETWORK("等待网络", WeaveStatusTone.CAUTION),
    RECOVERING("正在恢复", WeaveStatusTone.CAUTION),
    CHECK_EXIT("请检查出口或网络", WeaveStatusTone.CRITICAL),
}

/**
 * The status mark. [CHECK] is a proof symbol and is reserved for an exit the app has verified;
 * a tunnel that is merely up, waiting or recovering shows [LINK], which says "linked" and nothing
 * about whether traffic gets through.
 */
internal enum class HeroGlyph { POWER, PROGRESS, LINK, CHECK, WARNING }

internal enum class HeroAction { CONNECT, DISCONNECT, IN_PROGRESS, RETRY, CHOOSE_EXIT }

internal data class HeroModel(
    val headline: HeroHeadline,
    val detail: HeroDetail,
    val tone: WeaveStatusTone,
    val glyph: HeroGlyph,
    val action: HeroAction,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val actionEmphasized: Boolean,
)

internal fun heroModel(state: DashboardState): HeroModel {
    val readiness = exitReadiness(state)
    val coreMissing = !state.coreAvailable
    return when (state.connectionState) {
        ConnectionState.CONNECTED -> HeroModel(
            headline = HeroHeadline.CONNECTED,
            detail = connectedDetail(state.networkPathStatus),
            tone = pathTone(state.networkPathStatus),
            glyph = if (state.networkPathStatus == NetworkPathStatus.VERIFIED) HeroGlyph.CHECK else HeroGlyph.LINK,
            action = HeroAction.DISCONNECT,
            actionLabel = "断开",
            actionEnabled = true,
            actionEmphasized = false,
        )
        ConnectionState.CONNECTING -> HeroModel(
            headline = HeroHeadline.CONNECTING,
            detail = HeroDetail.ESTABLISHING,
            tone = WeaveStatusTone.PROGRESS,
            glyph = HeroGlyph.PROGRESS,
            action = HeroAction.IN_PROGRESS,
            actionLabel = "正在连接",
            actionEnabled = false,
            actionEmphasized = true,
        )
        ConnectionState.ERROR -> HeroModel(
            headline = HeroHeadline.FAILED,
            detail = if (coreMissing) HeroDetail.CORE_UNAVAILABLE else HeroDetail.CHECK_EXIT,
            tone = WeaveStatusTone.CRITICAL,
            glyph = HeroGlyph.WARNING,
            action = HeroAction.RETRY,
            actionLabel = "重试连接",
            actionEnabled = !coreMissing && readiness != ExitReadiness.INVALID,
            actionEmphasized = true,
        )
        ConnectionState.DISCONNECTED -> if (readiness == ExitReadiness.INVALID) {
            // A deleted exit cannot pass validation, so choosing a new one leads.
            HeroModel(
                headline = HeroHeadline.DISCONNECTED,
                detail = if (coreMissing) HeroDetail.CORE_UNAVAILABLE else HeroDetail.EXIT_INVALID,
                tone = if (coreMissing) WeaveStatusTone.CRITICAL else WeaveStatusTone.CAUTION,
                glyph = HeroGlyph.POWER,
                action = HeroAction.CHOOSE_EXIT,
                actionLabel = "选择出口",
                actionEnabled = true,
                actionEmphasized = true,
            )
        } else {
            HeroModel(
                headline = HeroHeadline.DISCONNECTED,
                detail = if (coreMissing) HeroDetail.CORE_UNAVAILABLE else HeroDetail.READY,
                tone = if (coreMissing) WeaveStatusTone.CRITICAL else WeaveStatusTone.NEUTRAL,
                glyph = HeroGlyph.POWER,
                action = HeroAction.CONNECT,
                actionLabel = "连接",
                actionEnabled = !coreMissing,
                actionEmphasized = true,
            )
        }
    }
}

private fun connectedDetail(status: NetworkPathStatus): HeroDetail = when (status) {
    NetworkPathStatus.VERIFIED -> HeroDetail.EXIT_VERIFIED
    NetworkPathStatus.TUN_READY -> HeroDetail.REACHABILITY_UNCHECKED
    NetworkPathStatus.WAITING_NETWORK -> HeroDetail.WAITING_NETWORK
    NetworkPathStatus.RECOVERING -> HeroDetail.RECOVERING
    NetworkPathStatus.STARTING, NetworkPathStatus.INACTIVE -> HeroDetail.ESTABLISHING
}

internal enum class HeroIssueKind { CORE_UNAVAILABLE, CONNECTION_FAILED }

/**
 * Essential guidance that is too long, or too actionable, for the fixed hero frame. It is shown
 * as a separate bounded surface directly under the hero so the hero itself never changes size.
 */
internal data class HeroIssue(
    val kind: HeroIssueKind,
    val message: String,
    /** Offer "change exit" next to the message; pointless when direct mode ignores the exit. */
    val offersExitChange: Boolean,
)

internal fun heroIssue(state: DashboardState): HeroIssue? = when {
    state.connectionState == ConnectionState.CONNECTED ||
        state.connectionState == ConnectionState.CONNECTING -> null
    !state.coreAvailable -> HeroIssue(
        kind = HeroIssueKind.CORE_UNAVAILABLE,
        message = "原生内核加载失败，已禁止建立 VPN",
        offersExitChange = false,
    )
    state.connectionState == ConnectionState.ERROR -> HeroIssue(
        kind = HeroIssueKind.CONNECTION_FAILED,
        message = state.statusMessage?.takeIf { it.isNotBlank() }
            ?: "连接未能建立。可直接重试，或更换出口后再连接。",
        offersExitChange = exitReadiness(state) != ExitReadiness.NOT_USED,
    )
    else -> null
}
