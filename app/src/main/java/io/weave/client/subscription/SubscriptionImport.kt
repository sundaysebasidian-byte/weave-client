package io.weave.client.subscription

import java.net.URI
import java.util.Base64

enum class SubscriptionFormat {
    URI_LIST,
    CLASH_YAML,
    SING_BOX_JSON,
}

data class ParsedSubscription(
    val format: SubscriptionFormat,
    val nodeCount: Int,
    val protocols: Set<String>,
    val nodes: List<ParsedNode>,
)

data class ParsedNode(
    val name: String,
    val protocol: String,
)

class SubscriptionImportException(message: String) : IllegalArgumentException(message)

/**
 * Parses only enough untrusted input to classify it and build safe metadata.
 *
 * Full node conversion remains an engine/compiler responsibility. This parser never returns node
 * credentials, which keeps subscription secrets out of UI state and diagnostic messages.
 */
class SubscriptionPayloadParser {
    fun parse(input: String): ParsedSubscription {
        val payload = input.trim().removePrefix("\uFEFF")
        if (payload.isEmpty()) {
            throw SubscriptionImportException("订阅内容为空")
        }
        if (looksLikeHtml(payload)) {
            throw SubscriptionImportException(
                "订阅地址返回的是网页，不是节点配置；请复制完整的 Clash 订阅链接",
            )
        }

        detectStructured(payload)?.let { return it }

        val uriPayload = if (payload.lineSequence().any(::looksLikeProxyUri)) {
            payload
        } else {
            decodeBase64(payload)
        }

        val nodes = uriPayload.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { value ->
                val scheme = proxyScheme(value) ?: return@mapNotNull null
                ParsedNode(
                    name = runCatching { URI(value).rawFragment }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "${scheme.uppercase()} 节点",
                    protocol = scheme,
                )
            }
            .toList()

        if (nodes.isEmpty()) {
            throw SubscriptionImportException("未识别到受支持的节点或配置")
        }

        return ParsedSubscription(
            format = SubscriptionFormat.URI_LIST,
            nodeCount = nodes.size,
            protocols = nodes.mapTo(sortedSetOf()) { it.protocol },
            nodes = nodes,
        )
    }

    private fun detectStructured(payload: String): ParsedSubscription? {
        if (CLASH_MARKER.containsMatchIn(payload)) {
            val nodes = parseClashNodes(payload)
            val protocols = nodes.mapTo(sortedSetOf()) { it.protocol }
            return ParsedSubscription(
                format = SubscriptionFormat.CLASH_YAML,
                nodeCount = nodes.size,
                protocols = protocols,
                nodes = nodes,
            )
        }

        if (payload.startsWith("{") && SING_BOX_MARKER.containsMatchIn(payload)) {
            val outboundArray = extractJsonArray(payload, "outbounds")
                ?: throw SubscriptionImportException("sing-box outbounds 结构无效")
            val protocols = JSON_TYPE.findAll(outboundArray)
                .map { it.groupValues[1].lowercase() }
                .filter { it in SUPPORTED_STRUCTURED_TYPES }
                .toList()
            return ParsedSubscription(
                format = SubscriptionFormat.SING_BOX_JSON,
                nodeCount = protocols.size,
                protocols = protocols.toSortedSet(),
                nodes = protocols.mapIndexed { index, protocol ->
                    ParsedNode("出站 ${index + 1}", protocol)
                },
            )
        }

        return null
    }

    private fun parseClashNodes(payload: String): List<ParsedNode> {
        val marker = CLASH_MARKER.find(payload) ?: return emptyList()
        val tail = payload.substring(marker.range.last + 1)
        val sectionEnd = TOP_LEVEL_KEY.find(tail)?.range?.first ?: tail.length
        val section = tail.substring(0, sectionEnd)
        parseFlowSequence(section)?.let { nodes ->
            if (nodes.isNotEmpty()) return nodes
        }
        val lines = section.lineSequence().toList()
        val anchorTypes = parseTypeAnchors(payload)
        val entryIndent = lines.mapNotNull { line ->
            PROXY_ENTRY.matchEntire(line)?.groupValues?.get(1)?.length
        }.minOrNull() ?: return emptyList()
        val nodes = mutableListOf<ParsedNode>()

        var insideEntry = false
        var pendingName: String? = null
        var pendingType: String? = null
        fun flush() {
            val name = pendingName
            val protocol = pendingType
            if (
                name != null &&
                protocol != null &&
                protocol in SUPPORTED_STRUCTURED_TYPES
            ) {
                nodes += ParsedNode(name.take(MAX_NODE_NAME_LENGTH), protocol)
            }
            pendingName = null
            pendingType = null
        }

        lines.forEach { line ->
            val entryMatch = PROXY_ENTRY.matchEntire(line)
            if (entryMatch != null && entryMatch.groupValues[1].length == entryIndent) {
                flush()
                insideEntry = true
                val entry = entryMatch.groupValues[2]
                parseFlowProxy(entry)?.let { node ->
                    pendingName = node.name
                    pendingType = node.protocol
                } ?: run {
                    parseProxyProperty(entry)?.let { (key, value) ->
                        when (key.lowercase()) {
                            "name" -> pendingName = parseYamlScalar(value)
                            "type" -> pendingType = parseProtocol(value)
                        }
                    }
                }
                MERGE_ANCHOR.matchEntire(entry)?.let {
                    pendingType = anchorTypes[it.groupValues[1]]
                }
                return@forEach
            }

            if (!insideEntry) return@forEach
            val propertyMatch = PROXY_PROPERTY.matchEntire(line)
            if (
                propertyMatch != null &&
                propertyMatch.groupValues[1].length == entryIndent + PROPERTY_INDENT
            ) {
                when (propertyMatch.groupValues[2].lowercase()) {
                    "name" -> pendingName = parseYamlScalar(propertyMatch.groupValues[3])
                    "type" -> pendingType = parseProtocol(propertyMatch.groupValues[3])
                }
            }
            val mergeMatch = PROXY_MERGE_PROPERTY.matchEntire(line)
            if (
                mergeMatch != null &&
                mergeMatch.groupValues[1].length == entryIndent + PROPERTY_INDENT
            ) {
                pendingType = anchorTypes[mergeMatch.groupValues[2]]
            }
        }
        flush()
        return nodes
    }

    private fun parseFlowSequence(section: String): List<ParsedNode>? {
        val value = section.trimStart()
        if (!value.startsWith("[")) return null
        return extractTopLevelMaps(value).mapNotNull(::parseFlowProxy)
    }

    private fun extractTopLevelMaps(value: String): List<String> {
        val maps = mutableListOf<String>()
        var squareDepth = 0
        var curlyDepth = 0
        var mapStart = -1
        var quote: Char? = null
        var escaped = false

        value.forEachIndexed { index, char ->
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' && quote == '"' -> escaped = true
                    char == quote -> quote = null
                }
                return@forEachIndexed
            }
            when (char) {
                '\'', '"' -> quote = char
                '[' -> squareDepth++
                ']' -> {
                    squareDepth--
                    if (squareDepth == 0) return maps
                }
                '{' -> {
                    if (squareDepth == 1 && curlyDepth == 0) mapStart = index
                    curlyDepth++
                }
                '}' -> {
                    curlyDepth--
                    if (squareDepth == 1 && curlyDepth == 0 && mapStart >= 0) {
                        maps += value.substring(mapStart, index + 1)
                        mapStart = -1
                    }
                }
            }
        }
        return maps
    }

    private fun parseFlowProxy(value: String): ParsedNode? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        var name: String? = null
        var type: String? = null
        splitFlowFields(trimmed.substring(1, trimmed.length - 1)).forEach { field ->
            val separator = topLevelColon(field)
            if (separator <= 0) return@forEach
            val key = field.substring(0, separator).trim().trim('"', '\'').lowercase()
            val rawValue = field.substring(separator + 1).trim()
            when (key) {
                "name" -> name = parseYamlScalar(rawValue)
                "type" -> type = parseProtocol(rawValue)
            }
        }
        val parsedName = name ?: return null
        val parsedType = type?.takeIf { it in SUPPORTED_STRUCTURED_TYPES } ?: return null
        return ParsedNode(parsedName.take(MAX_NODE_NAME_LENGTH), parsedType)
    }

    private fun splitFlowFields(value: String): List<String> {
        val fields = mutableListOf<String>()
        var start = 0
        var nestedDepth = 0
        var quote: Char? = null
        var escaped = false
        value.forEachIndexed { index, char ->
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' && quote == '"' -> escaped = true
                    char == quote -> quote = null
                }
                return@forEachIndexed
            }
            when (char) {
                '\'', '"' -> quote = char
                '[', '{' -> nestedDepth++
                ']', '}' -> nestedDepth--
                ',' -> if (nestedDepth == 0) {
                    fields += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        fields += value.substring(start)
        return fields
    }

    private fun topLevelColon(value: String): Int {
        var nestedDepth = 0
        var quote: Char? = null
        var escaped = false
        value.forEachIndexed { index, char ->
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' && quote == '"' -> escaped = true
                    char == quote -> quote = null
                }
                return@forEachIndexed
            }
            when (char) {
                '\'', '"' -> quote = char
                '[', '{' -> nestedDepth++
                ']', '}' -> nestedDepth--
                ':' -> if (nestedDepth == 0) return index
            }
        }
        return -1
    }

    private fun parseTypeAnchors(payload: String): Map<String, String> = buildMap {
        TYPE_ANCHOR.findAll(payload).forEach { anchor ->
            val blockStart = anchor.range.last + 1
            val blockEnd = TOP_LEVEL_KEY.find(payload, blockStart)?.range?.first ?: payload.length
            val block = payload.substring(blockStart, blockEnd)
            val protocol = ANCHOR_TYPE.find(block)?.groupValues?.get(1)?.lowercase()
            if (protocol in SUPPORTED_STRUCTURED_TYPES) {
                put(anchor.groupValues[1], requireNotNull(protocol))
            }
        }
    }

    private fun parseProxyProperty(value: String): Pair<String, String>? {
        val match = INLINE_PROXY_PROPERTY.matchEntire(value) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun parseProtocol(value: String): String =
        value.trim().trim('"', '\'').substringBefore(" #").trim().lowercase()

    private fun parseYamlScalar(raw: String): String {
        val value = raw.trim()
        return when {
            value.length >= 2 && value.startsWith("'") && value.endsWith("'") ->
                value.substring(1, value.length - 1).replace("''", "'")
            value.length >= 2 && value.startsWith("\"") && value.endsWith("\"") ->
                value.substring(1, value.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            else -> value.substringBefore(" #").trim()
        }.ifBlank { "未命名节点" }
    }

    private fun extractJsonArray(payload: String, key: String): String? {
        val keyMatch = Regex(""""${Regex.escape(key)}"\s*:""").find(payload) ?: return null
        val start = payload.indexOf('[', keyMatch.range.last + 1)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until payload.length) {
            val char = payload[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return payload.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun decodeBase64(payload: String): String {
        val compact = payload.filterNot(Char::isWhitespace)
        val decoded = runCatching {
            Base64.getDecoder().decode(padBase64(compact))
        }.recoverCatching {
            Base64.getUrlDecoder().decode(padBase64(compact))
        }.getOrElse {
            throw SubscriptionImportException("订阅既不是配置，也不是有效的 Base64 节点列表")
        }

        return decoded.toString(Charsets.UTF_8).also {
            if (it.toByteArray(Charsets.UTF_8).size != decoded.size) {
                throw SubscriptionImportException("订阅文本不是有效 UTF-8")
            }
        }
    }

    private fun looksLikeProxyUri(line: String): Boolean = proxyScheme(line.trim()) != null

    private fun looksLikeHtml(payload: String): Boolean {
        val prefix = payload.take(512).lowercase()
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            "<html" in prefix
    }

    private fun proxyScheme(value: String): String? {
        val scheme = runCatching { URI(value).scheme?.lowercase() }.getOrNull() ?: return null
        return scheme.takeIf { it in SUPPORTED_URI_SCHEMES }
    }

    private fun padBase64(value: String): String =
        value + "=".repeat((4 - value.length % 4) % 4)

    private companion object {
        val SUPPORTED_URI_SCHEMES = setOf(
            "ss",
            "ssr",
            "vmess",
            "vless",
            "trojan",
            "hysteria",
            "hysteria2",
            "hy2",
            "tuic",
            "wireguard",
        )
        val SUPPORTED_STRUCTURED_TYPES = SUPPORTED_URI_SCHEMES + setOf(
            "socks5",
            "http",
            "anytls",
            "openvpn",
        )
        val CLASH_MARKER = Regex("""(?m)^\s*proxies\s*:""", RegexOption.IGNORE_CASE)
        val SING_BOX_MARKER = Regex(""""outbounds"\s*:""", RegexOption.IGNORE_CASE)
        val TOP_LEVEL_KEY = Regex("""(?m)^[A-Za-z0-9_-]+\s*:""")
        val PROXY_ENTRY = Regex("""^(\s*)-\s*(.*?)\s*$""")
        val INLINE_PROXY_PROPERTY =
            Regex("""^([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.+?)\s*$""")
        val PROXY_PROPERTY =
            Regex("""^(\s*)([A-Za-z][A-Za-z0-9_-]*)\s*:\s*(.+?)\s*$""")
        val MERGE_ANCHOR = Regex("""^<<\s*:\s*\*([A-Za-z0-9_-]+)\s*$""")
        val PROXY_MERGE_PROPERTY =
            Regex("""^(\s*)<<\s*:\s*\*([A-Za-z0-9_-]+)\s*$""")
        val TYPE_ANCHOR = Regex(
            """(?m)^[A-Za-z0-9_-]+\s*:\s*&([A-Za-z0-9_-]+)\s*(?:#.*)?$""",
        )
        val ANCHOR_TYPE = Regex(
            """(?m)^\s+type\s*:\s*["']?([a-zA-Z0-9_-]+)["']?\s*(?:#.*)?$""",
            RegexOption.IGNORE_CASE,
        )
        val JSON_TYPE = Regex(""""type"\s*:\s*"([a-zA-Z0-9_-]+)"""")
        const val PROPERTY_INDENT = 2
        const val MAX_NODE_NAME_LENGTH = 200
    }
}
