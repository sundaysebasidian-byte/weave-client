package io.weave.client.subscription

import java.math.BigDecimal

/**
 * Parsed `subscription-userinfo` response header, e.g.
 * `upload=455727941; download=6174315083; total=1073741824000; expire=1671815872`.
 * Providers send integers, decimals or exponent notation; anything unparsable is ignored.
 */
data class SubscriptionUsage(
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val expireEpochSeconds: Long?,
) {
    val usedBytes: Long get() = saturatingAdd(uploadBytes, downloadBytes)

    fun encode(): String = "$uploadBytes,$downloadBytes,$totalBytes,${expireEpochSeconds ?: 0}"

    companion object {
        private const val MAX_HEADER_LENGTH = 512

        fun parse(header: String?): SubscriptionUsage? {
            if (header.isNullOrBlank() || header.length > MAX_HEADER_LENGTH) return null
            val fields = header.split(';', ',')
                .mapNotNull { field ->
                    val separator = field.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    val key = field.substring(0, separator).trim().lowercase()
                    val value = parseAmount(field.substring(separator + 1)) ?: return@mapNotNull null
                    key to value
                }
                .toMap()
            if (fields.keys.none { it in setOf("upload", "download", "total", "expire") }) return null
            return SubscriptionUsage(
                uploadBytes = fields["upload"] ?: 0,
                downloadBytes = fields["download"] ?: 0,
                totalBytes = fields["total"] ?: 0,
                expireEpochSeconds = fields["expire"]?.takeIf { it > 0 },
            )
        }

        fun decode(value: String?): SubscriptionUsage? {
            val parts = value?.split(',')?.map { it.toLongOrNull() ?: return null } ?: return null
            if (parts.size != 4 || parts.any { it < 0 }) return null
            return SubscriptionUsage(parts[0], parts[1], parts[2], parts[3].takeIf { it > 0 })
        }

        private fun parseAmount(raw: String): Long? {
            val decimal = raw.trim().toBigDecimalOrNull() ?: return null
            if (decimal.signum() < 0) return null
            return if (decimal > BigDecimal.valueOf(Long.MAX_VALUE)) Long.MAX_VALUE else decimal.toLong()
        }

        private fun saturatingAdd(a: Long, b: Long): Long =
            if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
    }
}

/** `profile-update-interval` is a whole number of hours; clamp to a sane window. */
object ProfileUpdateInterval {
    fun parse(header: String?): Int? = header?.trim()?.toIntOrNull()?.takeIf { it in 1..24 * 30 }
}
