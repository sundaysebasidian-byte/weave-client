package io.weave.client.routing

import android.content.Context
import io.weave.client.data.AppRouteStore
import io.weave.client.data.ProfileStorage
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.AppRoute
import io.weave.client.domain.ProxyNode
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteReferenceSanitizer
import io.weave.client.domain.RouteTarget
import io.weave.client.domain.hasLiveSubscriptionExit
import io.weave.client.domain.Subscription
import io.weave.client.domain.SubscriptionDeletionReconciler
import io.weave.client.subscription.SubscriptionRepository

/** The IO owner for group edits, subscription pruning and cross-process profile observations. */
internal class GroupProfileStore(
    private val context: Context,
    private val subscriptions: SubscriptionRepository,
    private val groups: CustomProxyGroupStore = CustomProxyGroupStore(context),
    private val routes: AppRouteStore = AppRouteStore(context),
    private val settings: RuntimeSettingsStore = RuntimeSettingsStore(context),
) {
    data class Snapshot(
        val epoch: String,
        val groups: List<CustomProxyGroup>,
        val subscriptions: List<Subscription>,
        val nodes: List<ProxyNode>,
        val routes: List<AppRoute>,
        val defaultTarget: RouteTarget?,
    )

    fun read(repairInvalidRoutes: Boolean = false): Snapshot = ProfileStorage.access(context) {
        readLocked(routes.load(), groups.list()).also { if (repairInvalidRoutes) publishRoutes(it) }
    }

    fun change(epoch: String, id: String, previous: CustomProxyGroup?, replacement: CustomProxyGroup?): Snapshot =
        ProfileStorage.editGroupProfile(context, epoch) {
            require(replacement == null || replacement.id == id)
            val current = groups.snapshot()
            check(current.firstOrNull { it.id == id } == previous) { "本机配置已变化，请关闭后重新编辑" }
            val changed = current.filterNot { it.id == id } + listOfNotNull(replacement)
            val nodes = subscriptions.loadNodes()
            if (replacement != null) {
                val available = nodes.mapTo(hashSetOf()) { NodeRef(it.subscriptionId, it.id) }
                require(replacement.members.all { it in available } &&
                    (replacement.entry == null || replacement.entry in available)) { "策略组引用的节点已不存在" }
            }
            if (changed != current) groups.save(changed)
            readLocked().also(::publishRoutes)
        }

    /** Single store commits are atomic already; combine live read and write under the profile lock. */
    fun changeRoutes(epoch: String, edit: (Snapshot) -> List<AppRoute>): Snapshot? =
        ProfileStorage.withCurrentEpoch(context, epoch) {
            val current = readLocked()
            val next = edit(current)
            if (next == current.routes) return@withCurrentEpoch null
            readLocked(next, current.groups).also(::publishRoutes)
        }

    fun changeDefault(epoch: String, target: RouteTarget): Snapshot =
        ProfileStorage.withCurrentEpoch(context, epoch) {
            val current = readLocked()
            require(target.hasLiveSubscriptionExit(current.subscriptions, current.nodes) &&
                (target.kind != RouteKind.GROUP || current.groups.any { it.id == target.groupId })
            ) {
                "本机配置已变化，请关闭后重新编辑"
            }
            val presented = RouteReferenceSanitizer.defaultTarget(
                target, current.subscriptions, current.nodes, current.groups.associate { it.id to it.name },
            )
            val committed = current.copy(defaultTarget = presented)
            settings.setDefaultRouteTarget(target)
            // No unrelated reads after the write. Returning failure here would falsely tell the UI
            // to retry a choice that may already be durable.
            committed
        }

    /** Refresh labels/removed node references from the live profile, not cached UI rows. */
    fun refreshReferences(epoch: String): Snapshot =
        ProfileStorage.withCurrentEpoch(context, epoch) { readLocked().also(::publishRoutes) }

    fun deleteSubscription(epoch: String, id: String) = ProfileStorage.pruneSubscriptionProfile(context, epoch) {
        val deleted = subscriptions.deleteLocal(id)
        val current = groups.snapshot()
        val pruned = current.mapNotNull { group ->
            // Removing a chain's entry must not quietly turn a two-hop exit into a single hop.
            if (group.entry?.subscriptionId == id) return@mapNotNull null
            group.copy(members = group.members.filterNot { it.subscriptionId == id }).takeIf { it.members.isNotEmpty() }
        }
        if (pruned != current) groups.save(pruned)
        val reconciled = SubscriptionDeletionReconciler.reconcile(
            id, routes.snapshot(), settings.defaultRouteTarget(), subscriptions.loadMetadata(),
        )
        if (reconciled.defaultTarget != settings.defaultRouteTarget()) {
            reconciled.defaultTarget?.let(settings::setDefaultRouteTarget) ?: settings.clearDefaultRouteTarget()
        }
        deleted to readLocked(reconciled.routes).also(::publishRoutes)
    }

    private fun readLocked(
        selectedRoutes: List<AppRoute> = routes.snapshot(),
        savedGroups: List<CustomProxyGroup> = groups.snapshot(),
    ): Snapshot {
        val (subscribers, nodes) = subscriptions.loadSnapshot()
        val names = savedGroups.associate { it.id to it.name }
        return Snapshot(
            ProfileStorage.restoreEpoch(context), savedGroups, subscribers, nodes,
            RouteReferenceSanitizer.routes(selectedRoutes, subscribers, nodes, names),
            RouteReferenceSanitizer.defaultTarget(settings.defaultRouteTarget(), subscribers, nodes, names),
        )
    }

    private fun publishRoutes(snapshot: Snapshot) {
        if (snapshot.routes != routes.load()) routes.save(snapshot.routes)
        // The default-target label is projected from its IDs; RuntimeSettingsStore does not
        // persist labels. A deleted group retains its invalid ID, rather than selecting another exit.
    }
}
