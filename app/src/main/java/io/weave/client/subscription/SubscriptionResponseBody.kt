package io.weave.client.subscription

import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** Bound decompressed bytes as well as wire bytes; compression must not bypass import limits. */
internal object SubscriptionResponseBody {
    fun read(input: InputStream, encoding: String?, maxBytes: Int): String {
        val wire = BoundedWireInputStream(input, maxBytes)
        val decoded = when (encoding?.lowercase()?.trim()) {
            null, "", "identity" -> wire
            "gzip", "x-gzip" -> GZIPInputStream(wire)
            "deflate" -> InflaterInputStream(wire)
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

    /** Caps compressed wire bytes even on chunked gzip headers or empty deflate blocks. */
    private class BoundedWireInputStream(
        input: InputStream,
        private val maxBytes: Int,
    ) : FilterInputStream(input) {
        private var seen = 0L

        private fun account(count: Long) {
            if (count <= 0L) return
            seen += count
            if (seen > maxBytes.toLong()) {
                throw SubscriptionImportException("订阅内容超过 ${maxBytes / 1024} KiB 限制")
            }
        }

        override fun read(): Int {
            val value = `in`.read()
            if (value >= 0) account(1L)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val allowed = (maxBytes.toLong() - seen + 1L).coerceAtMost(len.toLong()).toInt()
            val count = `in`.read(b, off, allowed.coerceAtLeast(1))
            if (count > 0) account(count.toLong())
            return count
        }

        override fun skip(n: Long): Long {
            if (n <= 0L) return 0L
            val allowed = (maxBytes.toLong() - seen + 1L).coerceAtMost(n)
            val skipped = `in`.skip(allowed.coerceAtLeast(1L))
            if (skipped > 0L) account(skipped)
            return skipped
        }
    }
}
