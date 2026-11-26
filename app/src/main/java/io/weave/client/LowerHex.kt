package io.weave.client

internal fun ByteArray.lowerHex(count: Int = size): String {
    val digits = "0123456789abcdef"
    return String(CharArray(count * 2).also { result ->
        for (index in 0 until count) {
            val byte = this[index].toInt() and 0xff
            result[index * 2] = digits[byte ushr 4]
            result[index * 2 + 1] = digits[byte and 0x0f]
        }
    })
}
