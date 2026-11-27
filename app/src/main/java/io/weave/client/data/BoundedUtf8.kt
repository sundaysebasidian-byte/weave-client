package io.weave.client.data

/** Counts a well-formed UTF-8 field without creating a second copy of it. */
internal fun utf8SizeUpTo(value: String, limit: Int): Int? {
    if (value.length > limit) return null
    var index = 0
    var bytes = 0
    while (index < value.length) {
        val char = value[index++]
        val width = when {
            char < '\u0080' -> 1
            char < '\u0800' -> 2
            char.isHighSurrogate() -> {
                if (index == value.length || !value[index++].isLowSurrogate()) return null
                4
            }
            char.isLowSurrogate() -> return null
            else -> 3
        }
        if (width > limit - bytes) return null
        bytes += width
    }
    return bytes
}
