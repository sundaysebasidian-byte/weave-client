package io.weave.client.subscription

/** A successfully fetched candidate needs confirmation; it is not a network/parse failure. */
class SubscriptionReviewRequiredException : IllegalStateException(
    "订阅节点有变化，请在详情中预览后更新",
)
