package io.weave.client.core.engine

/** Pure check for Weave-generated provider groups; never mistakes a fallback for a real node. */
internal object RuntimeProxyGroupReadiness {
    private val syntheticMembers = setOf(
        "",
        "COMPATIBLE",
        "DIRECT",
        "GLOBAL",
        "PASS",
        "REJECT",
    )

    fun isReady(
        selectedName: String?,
        memberNames: Set<String>,
        groupType: String? = null,
    ): Boolean {
        // `additional-prefix` is supported by the pinned core, but accepting a plain provider
        // name here keeps the gate compatible with older/repacked CMFA builds that omit the
        // override in the query response. The fallback names above are the only values Mihomo
        // can expose when a provider is empty; every other non-blank member is a real candidate.
        val realMembers = memberNames.filterNot(::isSynthetic)
        val hasRealMember = realMembers.isNotEmpty()
        // CMFA's automatic groups intentionally do not have a stable `now` during their first
        // load: URLTest/Fallback choose after their first probe, and LoadBalance chooses per
        // connection. They still need at least one real provider member before TUN is exposed.
        // The native bridge has emitted both its Go enum spelling (URLTest/LoadBalance) and the
        // YAML spelling (url-test/load-balance) across CMFA releases. Normalize punctuation so a
        // valid automatic group is not rejected solely because the bridge changed presentation.
        when (groupType?.filter(Char::isLetterOrDigit)?.lowercase()) {
            "loadbalance", "urltest", "fallback" -> return hasRealMember
        }
        return selectedName != null &&
            !isSynthetic(selectedName) && selectedName in memberNames
    }

    private fun isSynthetic(name: String): Boolean = name.trim().uppercase() in syntheticMembers
}
