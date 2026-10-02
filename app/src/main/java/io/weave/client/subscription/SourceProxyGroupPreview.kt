package io.weave.client.subscription

/** An inert, bounded view of groups from the source file. Never fed into Mihomo. */
data class SourceProxyGroupPreview(
    val name: String,
    val type: String,
    val explicitMembers: List<String>,
    val providerReferences: List<String>,
    val includesAll: Boolean,
    val hasFilter: Boolean,
    val hasSourceIcon: Boolean,
    val membersTruncated: Boolean,
)

internal object SourceProxyGroupPreviewParser {
    const val MAX_GROUPS = 48
    private const val MAX_MEMBERS = 40
    private const val MAX_PROVIDERS = 16
    private const val MAX_LABEL = 100
    private val controlCharacters = Regex("[\\p{Cntrl}\\u2028\\u2029]")

    fun parse(root: Map<String, Any?>): List<SourceProxyGroupPreview> =
        (root["proxy-groups"] as? List<*>)
            .orEmpty()
            .take(MAX_GROUPS)
            .mapNotNull { raw ->
                val group = raw as? Map<*, *> ?: return@mapNotNull null
                val name = label(group["name"]) ?: return@mapNotNull null
                val type = label(group["type"]) ?: return@mapNotNull null
                val allMembers = group["proxies"] as? List<*> ?: emptyList<Any>()
                val allProviders = group["use"] as? List<*> ?: emptyList<Any>()
                SourceProxyGroupPreview(
                    name = name,
                    type = type,
                    explicitMembers = allMembers.take(MAX_MEMBERS).mapNotNull(::label),
                    providerReferences = allProviders.take(MAX_PROVIDERS).mapNotNull(::label),
                    includesAll = listOf("include-all", "include-all-proxies", "include-all-providers")
                        .any { group[it] == true },
                    hasFilter = listOf("filter", "exclude-filter", "exclude-type")
                        .any { !group[it]?.toString().isNullOrBlank() },
                    // Icon URLs may contain query tokens. Do not store or fetch the URL merely
                    // because a subscription was imported; only record that an icon existed.
                    hasSourceIcon = !group["icon"]?.toString().isNullOrBlank(),
                    membersTruncated = allMembers.size > MAX_MEMBERS || allProviders.size > MAX_PROVIDERS,
                )
            }

    private fun label(value: Any?): String? = (value as? String)
        ?.replace(controlCharacters, " ")
        ?.trim()
        ?.take(MAX_LABEL)
        ?.takeIf(String::isNotEmpty)
}
