package io.weave.client.domain

/**
 * Produces a concise UI label without changing the raw provider node name used by Mihomo.
 */
internal object NodeDisplayName {
    fun core(rawName: String): String {
        val original = rawName.trim()
        if (original.isEmpty()) return "未命名节点"

        var offset = if (original[0] == '\\') {
            ESCAPED_UNICODE_PREFIX.find(original)?.range?.last?.plus(1) ?: 0
        } else {
            0
        }
        while (offset < original.length) {
            val codePoint = Character.codePointAt(original, offset)
            if (!isLeadingDecoration(codePoint)) break
            offset += Character.charCount(codePoint)
        }
        if (offset == 0) return original

        // original is already right-trimmed; only the prefix newly exposed by the decoration can change.
        while (offset < original.length) {
            val char = original[offset]
            if (!char.isWhitespace() && char !in LEADING_SEPARATORS) break
            offset++
        }
        return if (offset == original.length) original else original.substring(offset)
    }

    private fun isLeadingDecoration(codePoint: Int): Boolean =
        codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2300..0x27FF ||
            codePoint in 0xE0020..0xE007F ||
            codePoint == 0x200D ||
            codePoint == 0xFE0F ||
            codePoint == 0x20E3

    // Preserve valid longer legacy scalars, e.g. \u27080. Two extra hex digits after
    // any known four-digit decoration below give a scalar above U+10FFFF, so keep the suffix.
    private val ESCAPED_UNICODE_PREFIX =
        Regex("""^(?:(?:\\u[dD][89aAbB][0-9a-fA-F]{2}\\u[dD][c-fC-F][0-9a-fA-F]{2}|\\u(?:2[3-7][0-9a-fA-F]{2}|200[dD]|[fF][eE]0[fF]|20[eE]3)(?=[0-9a-fA-F]{2})|\\u[0-9a-fA-F]{4,8}|\\U[0-9a-fA-F]{8})\s*)+""")
    private val LEADING_SEPARATORS = setOf('·', '|', '-', '_', ':', '：')
}
