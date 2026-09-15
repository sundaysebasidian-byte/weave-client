package io.weave.client.transfer

/** Export is an explicit allow-list. Empty or stale selections must never broaden the scope. */
object SubscriptionShareSelection {
    fun validate(available: Set<String>, selected: Set<String>): Set<String> {
        require(selected.isNotEmpty()) { "请先选择要分享的订阅" }
        require(available.containsAll(selected)) { "所选订阅已变化，请重新选择" }
        return selected.toSet()
    }
}
