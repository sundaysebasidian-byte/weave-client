package io.weave.client.ui

import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.ProxyNode
import io.weave.client.routing.CustomProxyGroup

internal enum class QuickStartStep { IMPORT, NODE, CONNECT }
internal enum class QuickStartAction { NONE, IMPORT, NODE, CONNECT }

/** UI-only progress; opening or advancing a step never requests a permission or starts a VPN. */
internal data class QuickStartState(
    val visible: Boolean = false,
    val step: QuickStartStep = QuickStartStep.IMPORT,
    val action: QuickStartAction = QuickStartAction.NONE,
) {
    fun open() = QuickStartState(visible = true)
    fun dismiss() = copy(visible = false, action = QuickStartAction.NONE)
    fun back(): QuickStartState = when (step) {
        QuickStartStep.IMPORT -> dismiss()
        QuickStartStep.NODE -> copy(step = QuickStartStep.IMPORT, action = QuickStartAction.NONE)
        QuickStartStep.CONNECT -> copy(step = QuickStartStep.NODE, action = QuickStartAction.NONE)
    }
    fun next(hasNodes: Boolean, hasProxyTarget: Boolean): QuickStartState {
        if (!visible || action != QuickStartAction.NONE) return this
        return when (step) {
            QuickStartStep.IMPORT -> if (hasNodes) copy(step = QuickStartStep.NODE) else this
            QuickStartStep.NODE -> if (hasNodes && hasProxyTarget) copy(step = QuickStartStep.CONNECT) else this
            QuickStartStep.CONNECT -> this
        }
    }
    fun beginAction(requested: QuickStartAction): QuickStartState {
        if (!visible || action != QuickStartAction.NONE || requested == QuickStartAction.NONE) return this
        val expected = when (step) {
            QuickStartStep.IMPORT -> QuickStartAction.IMPORT
            QuickStartStep.NODE -> QuickStartAction.NODE
            QuickStartStep.CONNECT -> QuickStartAction.CONNECT
        }
        return if (requested == expected) copy(action = requested) else this
    }
    fun cancelAction() = copy(action = QuickStartAction.NONE)
    fun imported(hasNodes: Boolean): QuickStartState =
        if (visible && action == QuickStartAction.IMPORT) copy(
            step = if (hasNodes) QuickStartStep.NODE else QuickStartStep.IMPORT,
            action = QuickStartAction.NONE,
        ) else this
    fun selected(hasProxyTarget: Boolean): QuickStartState =
        if (visible && action == QuickStartAction.NODE) copy(
            step = if (hasProxyTarget) QuickStartStep.CONNECT else QuickStartStep.NODE,
            action = QuickStartAction.NONE,
        ) else this

    /** Child dialogs aren't saveable in the shell: return to the same guide step on recreation. */
    fun restored() = copy(action = QuickStartAction.NONE)
}

internal object QuickStartPolicy {
    fun shouldOfferAutomatically(
        seen: Boolean, dataLoaded: Boolean, upgradedInstall: Boolean, hasSubscriptions: Boolean,
        hasRoutes: Boolean, hasSavedTarget: Boolean, disclosureAccepted: Boolean, externalLaunch: Boolean,
    ) = !seen && dataLoaded && !upgradedInstall && !hasSubscriptions && !hasRoutes &&
        !hasSavedTarget && !disclosureAccepted && !externalLaunch

    /** Direct and reject choices are valid routes, but cannot complete a proxy-node guide step. */
    fun hasProxyTarget(target: RouteTarget?, nodes: List<ProxyNode>, groups: List<CustomProxyGroup> = emptyList()): Boolean {
        if (nodes.isEmpty() || target == null) return false
        return when (target.kind) {
            RouteKind.AUTO -> target.subscriptionId == null || nodes.any { it.subscriptionId == target.subscriptionId }
            RouteKind.FIXED -> nodes.any { it.id == target.nodeId && it.subscriptionId == target.subscriptionId }
            RouteKind.GROUP -> groups.firstOrNull { it.id == target.groupId }?.let { group ->
                fun present(subscriptionId: String, nodeId: String) = nodes.any {
                    it.subscriptionId == subscriptionId && it.id == nodeId
                }
                group.members.any { present(it.subscriptionId, it.nodeId) } &&
                    (group.entry == null || present(group.entry.subscriptionId, group.entry.nodeId))
            } ?: false
            RouteKind.DIRECT, RouteKind.BLOCK -> false
        }
    }
}
