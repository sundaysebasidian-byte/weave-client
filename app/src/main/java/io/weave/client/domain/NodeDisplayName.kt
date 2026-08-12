package io.weave.client.domain

/**
 * Produces a concise UI label without changing the raw provider node name used by Mihomo.
 */
internal object NodeDisplayName {
    fun core(rawName: String): String {
        val original = rawName.trim()
        if (original.isEmpty()) return "未命名节点"

        var value = original
        var removedDecoration = false
        val escapedPrefix = ESCAPED_UNICODE_PREFIX.find(value)
        if (escapedPrefix != null) {
            value = value.removeRange(escapedPrefix.range)
            removedDecoration = true
        }

        var offset = 0
        while (offset < value.length) {
            val codePoint = Character.codePointAt(value, offset)
            if (!isLeadingDecoration(codePoint)) break
            offset += Character.charCount(codePoint)
            removedDecoration = true
        }
        if (offset > 0) value = value.substring(offset)

        if (removedDecoration) {
            value = value.trimStart { char ->
                char.isWhitespace() || char in LEADING_SEPARATORS
            }
        }
        return value.trim().ifEmpty { original }
    }

    private fun isLeadingDecoration(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2300..0x27FF ||
            codePoint in 0xE0020..0xE007F ||
            codePoint == 0x200D ||
            codePoint == 0xFE0F ||
            codePoint == 0x20E3

    private val ESCAPED_UNICODE_PREFIX =
        Regex("""^(?:(?:\\u[0-9a-fA-F]{4,8}|\\U[0-9a-fA-F]{8})\s*)+""")
    private val LEADING_SEPARATORS = setOf('·', '|', '-', '_', ':', '：')
}
