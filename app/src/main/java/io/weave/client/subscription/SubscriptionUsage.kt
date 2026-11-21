package io.weave.client.subscription

import java.math.BigDecimal

/** Null fields clear obsolete provider headers; a null metadata object preserves them. */
data class SubscriptionRemoteMetadata(val usage: SubscriptionUsage?, val updateIntervalHours: Int?)

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
    val expireAtMillis: Long?
        get() = expireEpochSeconds?.takeIf { it in 1..(Long.MAX_VALUE / 1000) }?.times(1000)

    fun encode(): String = "$uploadBytes,$downloadBytes,$totalBytes,${expireEpochSeconds ?: 0}"

    companion object {
        private const val MAX_HEADER_LENGTH = 512

        fun parse(header: String?): SubscriptionUsage? {
            if (header == null || header.length > MAX_HEADER_LENGTH || header.isBlank()) return null
            var upload = 0L
            var download = 0L
            var total = 0L
            var expire: Long? = null
            var recognized = false
            for (field in header.split(';', ',')) {
                val separator = field.indexOf('=')
                if (separator <= 0) continue
                when (field.substring(0, separator).trim().lowercase()) {
                    "upload" -> upload = parseAmount(field.substring(separator + 1)) ?: continue
                    "download" -> download = parseAmount(field.substring(separator + 1)) ?: continue
                    "total" -> total = parseAmount(field.substring(separator + 1)) ?: continue
                    "expire" -> expire = parseAmount(field.substring(separator + 1)) ?: continue
                    else -> continue
                }
                recognized = true
            }
            return if (recognized) SubscriptionUsage(upload, download, total, expire?.takeIf { it > 0 }) else null
        }

        fun decode(value: String?): SubscriptionUsage? {
            val parts = value?.split(',')?.map { it.toLongOrNull() ?: return null } ?: return null
            if (parts.size != 4 || parts.any { it < 0 }) return null
            return SubscriptionUsage(parts[0], parts[1], parts[2], parts[3].takeIf { it > 0 })
        }

        private fun parseAmount(raw: String): Long? {
            val value = raw.trim()
            // Provider byte counts are usually unsigned ASCII integers; keep the full decimal fallback.
            if (value.isNotEmpty() && value.all { it in '0'..'9' }) {
                value.toLongOrNull()?.let { return it }
            }
            val decimal = value.toBigDecimalOrNull() ?: return null
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
