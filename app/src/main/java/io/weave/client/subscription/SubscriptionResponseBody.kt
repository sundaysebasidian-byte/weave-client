package io.weave.client.subscription

import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** Bound decompressed bytes as well as wire bytes; compression must not bypass import limits. */
internal object SubscriptionResponseBody {
    fun read(input: InputStream, encoding: String?, maxBytes: Int): String {
        val decoded = when (encoding?.lowercase()?.trim()) {
            null, "", "identity" -> input
            "gzip", "x-gzip" -> GZIPInputStream(input)
            "deflate" -> InflaterInputStream(input)
            else -> throw SubscriptionImportException("订阅压缩格式不受支持")
        }
        val content = decoded.use { stream ->
            val output = LocalSubscriptionBuffer(minOf(maxBytes, 32 * 1024))
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val length = stream.read(buffer)
                if (length < 0) break
                if (length > maxBytes - total) {
                    throw SubscriptionImportException("订阅内容超过 ${maxBytes / 1024} KiB 限制")
                }
                total += length
                output.write(buffer, 0, length)
            }
            output
        }
        return try {
            content.decodeUtf8()
        } catch (_: CharacterCodingException) {
            throw SubscriptionImportException("订阅内容不是有效 UTF-8")
        }
    }
}
