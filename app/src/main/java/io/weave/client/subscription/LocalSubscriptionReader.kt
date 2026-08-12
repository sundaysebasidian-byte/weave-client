package io.weave.client.subscription

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Reads a user-selected local subscription without granting the app broad storage access.
 */
class LocalSubscriptionReader(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw SubscriptionImportException("订阅内容超过 ${maxBytes / 1024} KiB 限制")
            }
            output.write(buffer, 0, read)
        }
        val bytes = output.toByteArray()
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrElse {
            throw SubscriptionImportException("订阅内容不是有效 UTF-8")
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024
    }
}
