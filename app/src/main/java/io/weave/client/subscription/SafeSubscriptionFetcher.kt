package io.weave.client.subscription

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection

data class SubscriptionFetchResult(
    val body: String,
    val finalUri: URI,
    val contentType: String?,
)

/**
 * Small HTTPS-only fetcher with bounded redirects and response size.
 *
 * Redirects are handled manually so every hop is revalidated and an HTTPS subscription can never
 * silently downgrade to cleartext or pivot to a loopback/private literal.
 */
class SafeSubscriptionFetcher(
    private val urlPolicy: SubscriptionUrlPolicy = SubscriptionUrlPolicy(),
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val maxRedirects: Int = DEFAULT_MAX_REDIRECTS,
) {
    fun fetch(rawUrl: String): SubscriptionFetchResult {
        var current = urlPolicy.validate(rawUrl)

        repeat(maxRedirects + 1) { hop ->
            val connection = current.toURL().openConnection() as? HttpsURLConnection
                ?: throw SubscriptionImportException("订阅连接不是 HTTPS")
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "Accept",
                    "application/yaml, text/yaml, text/plain, application/json, */*",
                )
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Cache-Control", "no-transform")

                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val declared = connection.contentLengthLong
                        if (declared > maxBytes) {
                            throw SubscriptionImportException("订阅内容超过 ${maxBytes / 1024} KiB 限制")
                        }
                        val bytes = connection.inputStream.use(::readBounded)
                        return SubscriptionFetchResult(
                            body = decodeUtf8(bytes),
                            finalUri = current,
                            contentType = connection.contentType,
                        )
                    }

                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308,
                    -> {
                        if (hop == maxRedirects) {
                            throw SubscriptionImportException("订阅重定向次数过多")
                        }
                        val location = connection.getHeaderField("Location")
                            ?: throw SubscriptionImportException("订阅重定向缺少目标地址")
                        current = urlPolicy.validate(current.resolve(location).toString())
                    }

                    else -> throw SubscriptionImportException("订阅服务器返回 HTTP $status")
                }
            } finally {
                connection.disconnect()
            }
        }

        throw SubscriptionImportException("订阅重定向次数过多")
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
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
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse {
        throw SubscriptionImportException("订阅内容不是有效 UTF-8")
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024
        const val DEFAULT_MAX_REDIRECTS = 3
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        // A number of subscription panels choose their output format from this header. Keep
        // "ClashMetaForAndroid" present because Weave currently compiles remote subscriptions
        // through the CMFA/Mihomo data path.
        const val USER_AGENT = "ClashMetaForAndroid/Weave-0.3"
    }
}
