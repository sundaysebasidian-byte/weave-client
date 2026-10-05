package io.weave.client.subscription

import java.net.URI

sealed interface QrSubscriptionInput {
    data class RemoteUrl(val url: String) : QrSubscriptionInput
    data class InlinePayload(val payload: String) : QrSubscriptionInput
}

/**
 * Converts common subscription QR payloads without logging or retaining their contents.
 */
class QrSubscriptionDecoder {
    fun isRemoteLink(value: String): Boolean = runCatching {
        URI(value.trim()).scheme?.lowercase(java.util.Locale.ROOT).let {
            it == "https" || it == "http" || it in WRAPPER_SCHEMES
        }
    }.getOrDefault(false)

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
            val remote = ExternalImportParser.fromLink(value) as? ExternalImport.Remote
                ?: throw SubscriptionImportException("客户端链接未包含有效 HTTPS 订阅地址")
            return QrSubscriptionInput.RemoteUrl(remote.url)
        }

        return QrSubscriptionInput.InlinePayload(value)
    }

    private companion object {
        const val MAX_QR_PAYLOAD_LENGTH = 32 * 1024
        val WRAPPER_SCHEMES = ExternalImportParser.LINK_SCHEMES
    }
}
