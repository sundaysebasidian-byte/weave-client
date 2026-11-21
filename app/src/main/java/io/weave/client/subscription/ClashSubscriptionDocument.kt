package io.weave.client.subscription

import java.util.Base64

/** Recognize YAML block/flow and JSON Clash maps at the root, never nested decoy keys. */
internal object ClashSubscriptionDocument {
    private val marker = Regex("(?:^|[\\n\\r,{])[ \\t]*[\"']?(?:proxies|proxy-providers)[\"']?[ \\t]*:")

    fun hasRootKey(text: String, key: String): Boolean = readRootWithKey(text, key) != null

    fun readRootWithKey(
        text: String,
        key: String,
        read: (String) -> Map<String, Any?> = ClashYamlCodec::read,
    ): Map<String, Any?>? {
        if (!marker.containsMatchIn(text)) return null
        return read(text).takeIf { it.containsKey(key) }
    }

    fun unwrap(input: String): String {
        val plain = input.trim().removePrefix("\uFEFF")
        if (marker.containsMatchIn(plain) || plain.contains(':')) return plain
        val compact = plain.filterNot(Char::isWhitespace)
        val decoded = runCatching {
            Base64.getDecoder().decode(compact).toString(Charsets.UTF_8)
        }.recoverCatching {
            Base64.getUrlDecoder().decode(compact).toString(Charsets.UTF_8)
        }.getOrNull()?.trim()?.removePrefix("\uFEFF") ?: return plain
        return decoded.takeIf { marker.containsMatchIn(it) } ?: plain
    }
}
