package io.weave.client.ui

import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupValidator
import io.weave.client.routing.NodeRef

/** An active chain entry is not also an exit member, but an inactive entry reserves nothing. */
internal fun customGroupDraftMembers(
    members: Set<NodeRef>,
    entry: NodeRef?,
    chained: Boolean,
): Set<NodeRef> =
    if (chained && entry != null && entry in members) members - entry else members

/** The result reports immediate validation only; persistence retains its separate error owner. */
internal fun submitCustomGroupDraft(
    draft: CustomProxyGroup,
    accepted: (CustomProxyGroup) -> Unit,
): Result<Unit> {
    val normalized = runCatching { CustomProxyGroupValidator.normalize(draft) }
        .getOrElse { return Result.failure(it) }
    accepted(normalized)
    return Result.success(Unit)
}
