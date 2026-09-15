package io.weave.client.subscription

/** Compatibility identity for the pinned CMFA Meta build, not the discontinued CFA core.
 * The flavor is significant: panels use it to decide which proxy protocols to return.
 */
internal object SubscriptionClientIdentity {
    const val USER_AGENT = "ClashMetaForAndroid/2.11.32.Meta"
}
