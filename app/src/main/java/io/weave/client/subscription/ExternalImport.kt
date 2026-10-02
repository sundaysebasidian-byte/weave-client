package io.weave.client.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * A subscription offered by another app (browser deep link, share sheet, file manager).
 * Nothing is fetched or stored until the user confirms it in the UI.
 */
sealed interface ExternalImport {
    /** A remote subscription; [url] is always HTTPS. */
    data class Remote(val url: String, val host: String, val suggestedName: String?) : ExternalImport

    /** Inline content (node URIs, Base64, YAML/JSON text) shared as text. */
    data class Inline(val text: String) : ExternalImport

    /** A content:// document; read only after confirmation. */
    data class Document(val uri: String, val displayName: String?) : ExternalImport
}

object ExternalImportParser {
    private const val MAX_TEXT_LENGTH = 5 * 1024 * 1024
    private const val MAX_NAME_LENGTH = 80

    /** Client URL schemes whose `install-config`/`import` links carry a subscription URL. */
    val LINK_SCHEMES = setOf(
        "clash", "clashmeta", "clash-verge", "clashmi", "flclash", "karing", "sing-box", "hiddify", "weave",
    )

    fun fromLink(raw: String): ExternalImport? {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return when (val scheme = uri.scheme?.lowercase(Locale.ROOT)) {
            "https" -> remote(value, fragmentName(uri))
            in LINK_SCHEMES -> {
                val query = queryParameters(uri.rawQuery)
                val wrapped = query["url"]
                    ?: pathUrl(uri, scheme)
                    ?: return null
                remote(wrapped, query["name"] ?: fragmentName(uri))
            }
            else -> null
        }
    }

    /** Shared plain text: a link if it is one, otherwise inline subscription content. */
    fun fromSharedText(text: String): ExternalImport? {
        val value = text.trim()
        if (value.isEmpty() || value.length > MAX_TEXT_LENGTH) return null
        if (!value.contains('\n')) fromLink(value)?.let { return it }
        // Share sheets often prepend a title before the link; accept the first bare link line.
        value.lineSequence()
            .map(String::trim)
            .firstOrNull { line -> LINK_SCHEMES.any { line.startsWith("$it://", ignoreCase = true) } }
            ?.let(::fromLink)
            ?.let { return it }
        return ExternalImport.Inline(value)
    }

    private fun remote(url: String, name: String?): ExternalImport.Remote? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) != "https") return null
        val host = uri.host?.takeIf(String::isNotBlank) ?: return null
        return ExternalImport.Remote(url.trim(), host.lowercase(Locale.ROOT), cleanName(name))
    }

    /** hiddify://import/https://… and sing-box style links that put the URL in the path. */
    private fun pathUrl(uri: URI, scheme: String?): String? {
        if (scheme != "hiddify" && scheme != "weave") return null
        val raw = uri.rawSchemeSpecificPart?.removePrefix("//") ?: return null
        val path = raw.substringAfter("import/", missingDelimiterValue = "").substringBefore('#')
        return decode(path).takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    private fun fragmentName(uri: URI): String? = uri.rawFragment?.let(::decode)

    private fun queryParameters(rawQuery: String?): Map<String, String> =
        rawQuery.orEmpty().split('&').mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            decode(field.substring(0, separator)).lowercase(Locale.ROOT) to decode(field.substring(separator + 1))
        }.toMap()

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    private fun cleanName(name: String?): String? = name
        ?.filterNot(Char::isISOControl)
        ?.trim()
        ?.take(MAX_NAME_LENGTH)
        ?.takeIf(String::isNotEmpty)
}
