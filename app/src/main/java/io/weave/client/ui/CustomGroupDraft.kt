package io.weave.client.ui

import io.weave.client.routing.NodeRef

/** An active chain entry is not also an exit member, but an inactive entry reserves nothing. */
internal fun customGroupDraftMembers(
    members: Set<NodeRef>,
    entry: NodeRef?,
    chained: Boolean,
): Set<NodeRef> =
    if (chained && entry != null && entry in members) members - entry else members
