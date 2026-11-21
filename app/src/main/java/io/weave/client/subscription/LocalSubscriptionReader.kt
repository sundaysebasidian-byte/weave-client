package io.weave.client.subscription

import java.io.InputStream

/**
 * Reads a user-selected local subscription without granting the app broad storage access.
 */
class LocalSubscriptionReader(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    fun read(input: InputStream): String {
        val output = LocalSubscriptionBuffer(minOf(maxBytes, 32 * 1024))
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
        return runCatching {
            output.decodeUtf8().removePrefix("\uFEFF")
        }.getOrElse {
            throw SubscriptionImportException("订阅内容不是有效 UTF-8")
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024
    }
}

internal fun importedSubscriptionName(requested: String, displayName: String?): String {
    requested.trim().takeIf(String::isNotEmpty)?.let { return it }
    return displayName
        ?.trim()
        ?.replace(LOCAL_SUBSCRIPTION_EXTENSION, "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let(LocalSubscriptionNameLimit::truncate)
        ?: "Imported subscription"
}

private val LOCAL_SUBSCRIPTION_EXTENSION = Regex(
    "(?i)\\.(yaml|yml|json|txt|conf|config)$",
)
