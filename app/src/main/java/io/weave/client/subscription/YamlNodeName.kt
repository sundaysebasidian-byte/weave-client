package io.weave.client.subscription

/** Compatibility decoder for the pre-alpha70 node index; new YAML uses ClashYamlCodec. */
internal object YamlNodeName {

    // Used only to match a previously stored, undecoded label to its newly parsed equivalent.
    fun legacyEquivalent(value: String): String = runCatching { decodeEscapes(value) }.getOrDefault(value)

    private fun decodeEscapes(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char != '\\') { append(char); continue }
            val escape = value.getOrNull(index++) ?: throw SubscriptionImportException("节点名称转义不完整")
            when (escape) {
                'x', 'u', 'U' -> {
                    val digits = when (escape) { 'x' -> 2; 'u' -> 4; else -> 8 }
                    val end = index + digits
                    val code = value.substring(index, end.coerceAtMost(value.length)).toLongOrNull(16)
                    if (end > value.length || code == null || code !in 0..0x10ffff) {
                        throw SubscriptionImportException("节点名称 Unicode 转义无效")
                    }
                    append(String(Character.toChars(code.toInt())))
                    index = end
                }
                '0' -> append('\u0000')
                'a' -> append('\u0007')
                'b' -> append('\b')
                't', '\t' -> append('\t')
                'n' -> append('\n')
                'v' -> append('\u000b')
                'f' -> append('\u000c')
                'r' -> append('\r')
                'e' -> append('\u001b')
                'N' -> append('\u0085')
                '_' -> append('\u00a0')
                'L' -> append('\u2028')
                'P' -> append('\u2029')
                ' ', '"', '/', '\\' -> append(escape)
                else -> throw SubscriptionImportException("节点名称转义无效")
            }
        }
    }
}
