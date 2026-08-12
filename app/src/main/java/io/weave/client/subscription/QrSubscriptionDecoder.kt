package io.weave.client.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface QrSubscriptionInput {
    data class RemoteUrl(val url: String) : QrSubscriptionInput
    data class InlinePayload(val payload: String) : QrSubscriptionInput
}

/**
 * Converts common subscription QR payloads without logging or retaining their contents.
 */
class QrSubscriptionDecoder {
    fun decode(rawValue: String): QrSubscriptionInput {
        val value = rawValue.trim()
        require(value.isNotEmpty()) { "二维码内容为空" }
        require(value.length <= MAX_QR_PAYLOAD_LENGTH) { "二维码内容过大" }

        val uri = runCatching { URI(value) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (scheme == "http") {
            throw SubscriptionImportException("二维码订阅地址必须使用 HTTPS")
        }
        if (scheme == "https") {
            return QrSubscriptionInput.RemoteUrl(value)
        }

        if (scheme in WRAPPER_SCHEMES) {
            val wrappedUrl = uri?.rawQuery
                ?.split('&')
                ?.asSequence()
                ?.mapNotNull { field ->
                    val separator = field.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    val key = URLDecoder.decode(
                        field.substring(0, separator),
                        StandardCharsets.UTF_8.name(),
                    )
                    if (key != "url") return@mapNotNull null
                    URLDecoder.decode(
                        field.substring(separator + 1),
                        StandardCharsets.UTF_8.name(),
                    )
                }
                ?.firstOrNull()
                ?: throw SubscriptionImportException("二维码未包含订阅地址")
            if (!wrappedUrl.startsWith("https://", ignoreCase = true)) {
                throw SubscriptionImportException("二维码订阅地址必须使用 HTTPS")
            }
            return QrSubscriptionInput.RemoteUrl(wrappedUrl)
        }

        return QrSubscriptionInput.InlinePayload(value)
    }

    private companion object {
        const val MAX_QR_PAYLOAD_LENGTH = 32 * 1024
        val WRAPPER_SCHEMES = setOf("clash", "karing", "sing-box", "hiddify")
    }
}
