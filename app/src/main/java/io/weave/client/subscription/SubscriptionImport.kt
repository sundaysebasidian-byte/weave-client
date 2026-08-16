package io.weave.client.subscription

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class SubscriptionFormat {
    URI_LIST,
    CLASH_YAML,
    SING_BOX_JSON,
    V2RAY_JSON,
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
 * The parser returns only safe metadata. Runtime conversion is kept in this class and emits a
 * temporary Clash provider without copying credentials into UI state or diagnostic messages.
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

    /**
     * Converts URI lists, sing-box JSON and basic V2Ray JSON into a minimal Clash/Mihomo provider.
     *
     * This deliberately supports only fields with a direct, unambiguous mapping. A URI or
     * sing-box object with an unsupported transport is rejected instead of being silently
     * flattened into a different connection. Clash YAML is reduced to its node provider section;
     * user-controlled runtime and control-plane sections never reach Mihomo.
     */
    fun normalizeForMihomo(input: String, parsed: ParsedSubscription = parse(input)): String =
        when (parsed.format) {
            SubscriptionFormat.CLASH_YAML -> sanitizeClashProvider(input)
            SubscriptionFormat.URI_LIST -> renderClash(uriSpecs(uriPayload(input)))
            SubscriptionFormat.SING_BOX_JSON -> renderClash(singBoxSpecs(input))
            SubscriptionFormat.V2RAY_JSON -> renderClash(v2RaySpecs(input))
        }

    /**
     * A user-supplied Clash document is a provider, not a control-plane configuration. Strip
     * control/listener sections before it reaches Mihomo; otherwise an imported subscription
     * could turn on an external controller, LAN proxy port, script or remote rule provider.
     */
    private fun sanitizeClashProvider(input: String): String {
        val lines = input.trim().lineSequence().toList()
        // A provider only needs `proxies:`. The one exception is a YAML merge anchor used by
        // some OpenVPN/Clash exports (`x-common: &common` + `<<: *common` inside proxies). Keep
        // only anchor definitions that are actually referenced by a proxy; every other root key
        // (including future control-plane keys we do not know today) is discarded by default.
        val referencedAnchors = Regex("\\*([A-Za-z0-9_.-]+)")
            .findAll(input)
            .map { it.groupValues[1] }
            .toSet()
        val anchorRootKeys = Regex("(?m)^([A-Za-z0-9_.-]+)\\s*:\\s*&([A-Za-z0-9_.-]+)")
            .findAll(input)
            .filter { it.groupValues[2] in referencedAnchors }
            .map { it.groupValues[1].lowercase() }
            .toSet()
        val allowedRootKeys = setOf("proxies") + anchorRootKeys
        val retained = buildString {
            var skipIndent: Int? = null
            lines.forEach { line ->
                val trimmed = line.trimStart()
                val key = trimmed.substringBefore(':', missingDelimiterValue = "")
                    .lowercase()
                val indent = line.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0
                if (skipIndent != null) {
                    if (trimmed.isBlank() || indent > skipIndent) return@forEach
                    skipIndent = null
                }
                // Only a root key is control-plane input. A nested `server`, `path` or `tls`
                // property inside a proxy is ordinary node data and must be retained.
                if (indent == 0 && key.isNotEmpty() && key !in allowedRootKeys) {
                    skipIndent = indent
                    return@forEach
                }
                appendLine(line)
            }
        }.trim()
        require(retained.lineSequence().any { it.trimStart().startsWith("proxies:") }) {
            "Clash 订阅缺少 proxies 节点列表"
        }
        return retained
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
            val looksLikeSingBox = extractJsonObjects(outboundArray)
                .firstOrNull()
                ?.let { jsonString(it, "type") != null }
                ?: false
            if (looksLikeSingBox) {
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
        }

        if (payload.startsWith("{") && V2RAY_MARKER.containsMatchIn(payload)) {
            val outboundArray = extractJsonArray(payload, "outbounds")
                ?: throw SubscriptionImportException("V2Ray outbounds 结构无效")
            val protocols = V2RAY_PROTOCOL.findAll(outboundArray)
                .map { it.groupValues[1].lowercase() }
                .filter { it in V2RAY_SUPPORTED_TYPES }
                .toList()
            return ParsedSubscription(
                format = SubscriptionFormat.V2RAY_JSON,
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

    /** Small JSON accessors used here so subscription parsing remains JVM-testable without Android org.json. */
    private fun jsonString(objectJson: String, key: String): String? {
        val pattern = Regex(
            "\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
        )
        val value = pattern.find(objectJson)?.groupValues?.getOrNull(1) ?: return null
        return value.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun jsonInt(objectJson: String, key: String): Int? = Regex(
        "\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*(-?\\d+)",
    ).find(objectJson)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun jsonBoolean(objectJson: String, key: String): Boolean? = Regex(
        "\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE,
    ).find(objectJson)?.groupValues?.getOrNull(1)?.toBoolean()

    private fun extractJsonObject(payload: String, key: String): String? {
        val keyMatch = Regex(""""${Regex.escape(key)}"\s*:\s*\{""").find(payload) ?: return null
        val start = payload.indexOf('{', keyMatch.range.first)
        return extractBalanced(payload, start, '{', '}')
    }

    private fun extractJsonObjects(array: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        array.forEachIndexed { index, char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                return@forEachIndexed
            }
            when (char) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0 && start >= 0) {
                        objects += array.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractBalanced(payload: String, start: Int, open: Char, close: Char): String? {
        if (start !in payload.indices || payload[start] != open) return null
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
                open -> depth++
                close -> {
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

    private fun uriPayload(payload: String): String =
        if (payload.lineSequence().any(::looksLikeProxyUri)) payload.trim() else decodeBase64(payload)

    private data class YamlScalar(val value: String, val quoted: Boolean = true)

    private data class ProxySpec(
        val name: String,
        val type: String,
        val fields: LinkedHashMap<String, YamlScalar> = linkedMapOf(),
        val nested: LinkedHashMap<String, LinkedHashMap<String, YamlScalar>> = linkedMapOf(),
    )

    private fun uriSpecs(payload: String): List<ProxySpec> = payload.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { value ->
            val scheme = proxyScheme(value)
                ?: throw SubscriptionImportException("发现未支持的节点格式：${value.take(24)}")
            parseUriSpec(value, scheme)
        }
        .toList()
        .also { if (it.isEmpty()) throw SubscriptionImportException("未识别到可转换的节点协议") }

    private fun parseUriSpec(value: String, scheme: String): ProxySpec {
        if (scheme == "vmess") return parseVmess(value)
        if (scheme == "ssr") return parseShadowsocksR(value)
        val uri = runCatching { URI(value) }.getOrElse {
            throw SubscriptionImportException("$scheme 节点地址格式无效")
        }
        val name = decodePart(uri.rawFragment).ifBlank { "$scheme 节点" }
        if (scheme == "ss") return parseShadowsocks(value, name)
        val host = uri.host ?: uri.rawAuthority.substringAfterLast('@').substringBefore(':')
        val port = uri.port.takeIf { it > 0 }
            ?: throw SubscriptionImportException("$name 缺少端口")
        val query = queryMap(uri.rawQuery)
        return when (scheme) {
            "vless" -> ProxySpec(name, "vless").apply {
                fields["server"] = YamlScalar(host)
                fields["port"] = YamlScalar(port.toString(), quoted = false)
                val uuid = decodePart(uri.rawUserInfo).substringBefore(':')
                if (uuid.isBlank()) throw SubscriptionImportException("$name 缺少 UUID")
                fields["uuid"] = YamlScalar(uuid)
                addCommonTls(query)
                addTransport(query)
                query["flow"]?.let { fields["flow"] = YamlScalar(it) }
            }
            "trojan" -> ProxySpec(name, "trojan").apply {
                fields["server"] = YamlScalar(host)
                fields["port"] = YamlScalar(port.toString(), quoted = false)
                val password = decodePart(uri.rawUserInfo)
                if (password.isBlank()) throw SubscriptionImportException("$name 缺少密码")
                fields["password"] = YamlScalar(password)
                addCommonTls(query)
                addTransport(query)
            }
            "hysteria", "hysteria2", "hy2" -> ProxySpec(name, if (scheme == "hysteria") "hysteria" else "hysteria2").apply {
                fields["server"] = YamlScalar(host)
                fields["port"] = YamlScalar(port.toString(), quoted = false)
                fields["password"] = YamlScalar(decodePart(uri.rawUserInfo).substringAfter(':', decodePart(uri.rawUserInfo)))
                query["sni"]?.let { fields["sni"] = YamlScalar(it) }
                query["insecure"]?.let {
                    fields["skip-cert-verify"] = YamlScalar(
                        (it == "1" || it.equals("true", true)).toString(),
                        quoted = false,
                    )
                }
                query["obfs"]?.let { fields["obfs"] = YamlScalar(it) }
                query["obfs-password"]?.let { fields["obfs-password"] = YamlScalar(it) }
            }
            "tuic" -> ProxySpec(name, "tuic").apply {
                fields["server"] = YamlScalar(host)
                fields["port"] = YamlScalar(port.toString(), quoted = false)
                val credentials = decodePart(uri.rawUserInfo).split(':', limit = 2)
                fields["uuid"] = YamlScalar(credentials.firstOrNull().orEmpty())
                fields["password"] = YamlScalar(credentials.getOrNull(1).orEmpty())
                query["sni"]?.let { fields["sni"] = YamlScalar(it) }
                query["congestion_control"]?.let { fields["congestion-controller"] = YamlScalar(it) }
                query["udp_relay_mode"]?.let { fields["udp-relay-mode"] = YamlScalar(it) }
                query["insecure"]?.let {
                    fields["skip-cert-verify"] = YamlScalar(
                        (it == "1" || it.equals("true", true)).toString(),
                        quoted = false,
                    )
                }
            }
            "socks", "socks5", "http" -> ProxySpec(name, if (scheme == "http") "http" else "socks5").apply {
                fields["server"] = YamlScalar(host)
                fields["port"] = YamlScalar(port.toString(), quoted = false)
                val credentials = decodePart(uri.rawUserInfo).split(':', limit = 2)
                credentials.firstOrNull()?.takeIf(String::isNotBlank)?.let { fields["username"] = YamlScalar(it) }
                credentials.getOrNull(1)?.takeIf(String::isNotBlank)?.let { fields["password"] = YamlScalar(it) }
            }
            "anytls" -> ProxySpec(name, "anytls").apply {
                fields["server"] = YamlScalar(host)
                fields["port"] = YamlScalar(port.toString(), quoted = false)
                fields["password"] = YamlScalar(decodePart(uri.rawUserInfo))
                addCommonTls(query)
            }
            "wireguard" -> throw SubscriptionImportException("WireGuard URI 需要私钥与地址字段，当前请导入 Clash YAML")
            else -> throw SubscriptionImportException("暂不支持将 $scheme URI 转换为 Mihomo")
        }
    }

    private fun parseVmess(value: String): ProxySpec {
        val encoded = value.substringAfter("vmess://").substringBefore('#')
        val json = runCatching {
            Base64.getDecoder().decode(padBase64(encoded)).toString(Charsets.UTF_8)
        }.recoverCatching {
            Base64.getUrlDecoder().decode(padBase64(encoded)).toString(Charsets.UTF_8)
        }.getOrElse { throw SubscriptionImportException("VMess 节点参数不是有效 Base64 JSON") }
        val name = decodePart(value.substringAfter('#', "")).ifBlank {
            jsonString(json, "ps").orEmpty().ifBlank { "vmess 节点" }
        }
        val host = jsonString(json, "add")?.takeIf(String::isNotBlank)
            ?: throw SubscriptionImportException("$name 缺少服务器地址")
        val port = jsonInt(json, "port")?.takeIf { it > 0 }
            ?: throw SubscriptionImportException("$name 缺少端口")
        return ProxySpec(name, "vmess").apply {
            fields["server"] = YamlScalar(host)
            fields["port"] = YamlScalar(port.toString(), quoted = false)
            val uuid = jsonString(json, "id").orEmpty()
            if (uuid.isBlank()) throw SubscriptionImportException("$name 缺少 UUID")
            fields["uuid"] = YamlScalar(uuid)
            fields["alterId"] = YamlScalar((jsonInt(json, "aid") ?: 0).toString(), quoted = false)
            jsonString(json, "scy")?.takeIf(String::isNotBlank)?.let { fields["cipher"] = YamlScalar(it) }
            jsonString(json, "tls")?.takeIf(String::isNotBlank)?.let {
                fields["tls"] = YamlScalar(it.equals("tls", true).toString(), quoted = false)
            }
            jsonString(json, "sni")?.takeIf(String::isNotBlank)?.let { fields["servername"] = YamlScalar(it) }
            jsonString(json, "net")?.takeIf(String::isNotBlank)?.let { network ->
                fields["network"] = YamlScalar(network)
                if (network == "ws") {
                    val ws = linkedMapOf<String, YamlScalar>()
                    jsonString(json, "path")?.takeIf(String::isNotBlank)?.let { ws["path"] = YamlScalar(it) }
                    jsonString(json, "host")?.let(::yamlHostHeader)?.let { ws["headers"] = YamlScalar(it, quoted = false) }
                    if (ws.isNotEmpty()) nested["ws-opts"] = ws
                }
            }
        }
    }

    private fun parseShadowsocks(value: String, name: String): ProxySpec {
        val body = value.substringAfter("ss://").substringBefore('#').substringBefore('?')
        val decoded = runCatching {
            Base64.getDecoder().decode(padBase64(body)).toString(Charsets.UTF_8)
        }.recoverCatching {
            Base64.getUrlDecoder().decode(padBase64(body)).toString(Charsets.UTF_8)
        }.getOrNull() ?: body
        val authority = decoded.substringAfter('@', decoded)
        val credentials = decoded.substringBefore('@')
        val hostPort = authority.substringBeforeLast(':') to authority.substringAfterLast(':')
        val host = hostPort.first.takeIf(String::isNotBlank)
            ?: throw SubscriptionImportException("$name 缺少服务器地址")
        val port = hostPort.second.toIntOrNull()?.takeIf { it > 0 }
            ?: throw SubscriptionImportException("$name 缺少端口")
        val separator = credentials.indexOf(':')
        if (separator <= 0) throw SubscriptionImportException("$name 缺少加密方式或密码")
        if (credentials.substringAfter(':').isBlank()) throw SubscriptionImportException("$name 缺少密码")
        return ProxySpec(name, "ss").apply {
            fields["server"] = YamlScalar(host)
            fields["port"] = YamlScalar(port.toString(), quoted = false)
            fields["cipher"] = YamlScalar(credentials.substringBefore(':'))
            fields["password"] = YamlScalar(credentials.substringAfter(':'))
        }
    }

    private fun parseShadowsocksR(value: String): ProxySpec {
        val encoded = value.substringAfter("ssr://").substringBefore('#')
        val decoded = runCatching {
            Base64.getUrlDecoder().decode(padBase64(encoded)).toString(Charsets.UTF_8)
        }.recoverCatching {
            Base64.getDecoder().decode(padBase64(encoded)).toString(Charsets.UTF_8)
        }.getOrElse { throw SubscriptionImportException("SSR 节点参数不是有效 Base64") }
        val main = decoded.substringBefore("/?")
        val parts = main.split(':', limit = 6)
        if (parts.size != 6) throw SubscriptionImportException("SSR 节点字段不完整")
        val host = parts[0].takeIf(String::isNotBlank)
            ?: throw SubscriptionImportException("SSR 节点缺少服务器地址")
        val port = parts[1].toIntOrNull()?.takeIf { it > 0 }
            ?: throw SubscriptionImportException("SSR 节点缺少端口")
        val query = decoded.substringAfter("/?", "")
        val name = queryMap(query)["remarks"] ?: "ssr 节点"
        return ProxySpec(name, "ssr").apply {
            fields["server"] = YamlScalar(host)
            fields["port"] = YamlScalar(port.toString(), quoted = false)
            fields["protocol"] = YamlScalar(parts[2])
            fields["cipher"] = YamlScalar(parts[3])
            fields["obfs"] = YamlScalar(parts[4])
            fields["password"] = YamlScalar(
                runCatching {
                    Base64.getUrlDecoder().decode(padBase64(parts[5])).toString(Charsets.UTF_8)
                }.getOrDefault(parts[5]),
            )
            queryMap(query)["protoparam"]?.let { fields["protocol-param"] = YamlScalar(it) }
            queryMap(query)["obfsparam"]?.let { fields["obfs-param"] = YamlScalar(it) }
        }
    }

    private fun singBoxSpecs(payload: String): List<ProxySpec> {
        val outbounds = extractJsonArray(payload, "outbounds")
            ?: throw SubscriptionImportException("sing-box outbounds 结构无效")
        val specs = buildList {
            extractJsonObjects(outbounds).forEachIndexed { index, outbound ->
                val type = jsonString(outbound, "type").orEmpty().lowercase()
                val name = jsonString(outbound, "tag").orEmpty().ifBlank { "$type 节点 ${index + 1}" }
                if (type in setOf("direct", "block", "dns", "selector", "urltest", "fallback", "group")) return@forEachIndexed
                add(parseSingBoxOutbound(outbound, type, name))
            }
        }
        if (specs.isEmpty()) throw SubscriptionImportException("sing-box 中没有可转换的代理出站")
        return specs
    }

    private fun v2RaySpecs(payload: String): List<ProxySpec> {
        val outbounds = extractJsonArray(payload, "outbounds")
            ?: throw SubscriptionImportException("V2Ray outbounds 结构无效")
        val specs = buildList {
            extractJsonObjects(outbounds).forEachIndexed { index, outbound ->
                val protocol = jsonString(outbound, "protocol").orEmpty().lowercase()
                if (protocol in setOf("freedom", "blackhole", "dns", "loopback")) return@forEachIndexed
                val name = jsonString(outbound, "tag").orEmpty().ifBlank { "$protocol 节点 ${index + 1}" }
                add(parseV2RayOutbound(outbound, protocol, name))
            }
        }
        if (specs.isEmpty()) throw SubscriptionImportException("V2Ray 中没有可转换的代理出站")
        return specs
    }

    private fun parseV2RayOutbound(outbound: String, protocol: String, name: String): ProxySpec {
        val mappedType = when (protocol) {
            "shadowsocks" -> "ss"
            "socks" -> "socks5"
            "vmess", "vless", "trojan", "http" -> protocol
            else -> throw SubscriptionImportException("V2Ray $protocol 出站暂不支持安全转换")
        }
        val settings = extractJsonObject(outbound, "settings")
            ?: throw SubscriptionImportException("$name 缺少 V2Ray settings")
        val serverObject = when (protocol) {
            "vmess", "vless" -> extractJsonArray(settings, "vnext")?.let(::extractJsonObjects)?.firstOrNull()
            "trojan", "shadowsocks", "socks", "http" -> extractJsonArray(settings, "servers")?.let(::extractJsonObjects)?.firstOrNull()
            else -> null
        } ?: throw SubscriptionImportException("$name 缺少服务器列表")
        val host = jsonString(serverObject, "address")?.takeIf(String::isNotBlank)
            ?: throw SubscriptionImportException("$name 缺少服务器地址")
        val port = jsonInt(serverObject, "port")?.takeIf { it > 0 }
            ?: throw SubscriptionImportException("$name 缺少端口")
        return ProxySpec(name, mappedType).apply {
            fields["server"] = YamlScalar(host)
            fields["port"] = YamlScalar(port.toString(), quoted = false)
            val user = extractJsonArray(serverObject, "users")?.let(::extractJsonObjects)?.firstOrNull()
            val passwordUser = extractJsonArray(serverObject, "users")?.let(::extractJsonObjects)?.firstOrNull()
            when (mappedType) {
            "vmess", "vless" -> {
                    val uuid = jsonString(user.orEmpty(), "id").orEmpty()
                    if (uuid.isBlank()) throw SubscriptionImportException("$name 缺少 UUID")
                    fields["uuid"] = YamlScalar(uuid)
                    if (mappedType == "vmess") {
                        fields["alterId"] = YamlScalar((jsonInt(user.orEmpty(), "alterId") ?: 0).toString(), quoted = false)
                        jsonString(user.orEmpty(), "security")?.let { fields["cipher"] = YamlScalar(it) }
                    } else {
                        jsonString(user.orEmpty(), "encryption")?.let { fields["encryption"] = YamlScalar(it) }
                        jsonString(user.orEmpty(), "flow")?.let { fields["flow"] = YamlScalar(it) }
                    }
                }
                "trojan" -> {
                    val password = jsonString(passwordUser.orEmpty(), "password").orEmpty()
                    if (password.isBlank()) throw SubscriptionImportException("$name 缺少密码")
                    fields["password"] = YamlScalar(password)
                }
                "ss" -> {
                    val cipher = jsonString(serverObject, "method").orEmpty()
                    val password = jsonString(serverObject, "password").orEmpty()
                    if (cipher.isBlank() || password.isBlank()) throw SubscriptionImportException("$name 缺少加密方式或密码")
                    fields["cipher"] = YamlScalar(cipher)
                    fields["password"] = YamlScalar(password)
                }
                "socks5", "http" -> {
                    jsonString(passwordUser.orEmpty(), "user")?.let { fields["username"] = YamlScalar(it) }
                    jsonString(passwordUser.orEmpty(), "pass")?.let { fields["password"] = YamlScalar(it) }
                }
            }
            val stream = extractJsonObject(outbound, "streamSettings")
            val network = jsonString(stream.orEmpty(), "network")
            network?.let { fields["network"] = YamlScalar(it) }
            val security = jsonString(stream.orEmpty(), "security")
            if (security == "tls" || security == "reality") {
                fields["tls"] = YamlScalar("true", quoted = false)
                val tls = extractJsonObject(stream.orEmpty(), "tlsSettings")
                jsonString(tls.orEmpty(), "serverName")?.let { fields["servername"] = YamlScalar(it) }
                if (jsonBoolean(tls.orEmpty(), "allowInsecure") == true) {
                    fields["skip-cert-verify"] = YamlScalar("true", quoted = false)
                }
                if (security == "reality") {
                    val reality = extractJsonObject(stream.orEmpty(), "realitySettings")
                    val realityOpts = nested.getOrPut("reality-opts") { linkedMapOf() }
                    jsonString(reality.orEmpty(), "publicKey")?.let { realityOpts["public-key"] = YamlScalar(it) }
                    jsonString(reality.orEmpty(), "shortId")?.let { realityOpts["short-id"] = YamlScalar(it) }
                }
            }
            if (network == "ws") {
                val ws = extractJsonObject(stream.orEmpty(), "wsSettings")
                val wsOpts = nested.getOrPut("ws-opts") { linkedMapOf() }
                jsonString(ws.orEmpty(), "path")?.let { wsOpts["path"] = YamlScalar(it) }
                extractJsonObject(ws.orEmpty(), "headers")?.let { headers ->
                    jsonString(headers, "Host")?.let(::yamlHostHeader)?.let { wsOpts["headers"] = YamlScalar(it, quoted = false) }
                }
            } else if (network == "grpc") {
                val grpc = extractJsonObject(stream.orEmpty(), "grpcSettings")
                jsonString(grpc.orEmpty(), "serviceName")?.let {
                    nested.getOrPut("grpc-opts") { linkedMapOf() }["grpc-service-name"] = YamlScalar(it)
                }
            }
        }
    }

    private fun parseSingBoxOutbound(outbound: String, type: String, name: String): ProxySpec {
        val mappedType = when (type) {
            "shadowsocks" -> "ss"
            "hysteria2" -> "hysteria2"
            "vless", "vmess", "trojan", "tuic", "socks", "http" -> if (type == "socks") "socks5" else type
            else -> throw SubscriptionImportException("sing-box $type 出站暂不支持安全转换")
        }
        return ProxySpec(name, mappedType).apply {
            val host = jsonString(outbound, "server")?.takeIf(String::isNotBlank)
                ?: throw SubscriptionImportException("$name 缺少服务器地址")
            val port = jsonInt(outbound, "server_port")?.takeIf { it > 0 }
                ?: throw SubscriptionImportException("$name 缺少端口")
            fields["server"] = YamlScalar(host)
            fields["port"] = YamlScalar(port.toString(), quoted = false)
            when (mappedType) {
                "vless", "vmess" -> fields["uuid"] = YamlScalar(jsonString(outbound, "uuid").orEmpty())
                "trojan" -> fields["password"] = YamlScalar(jsonString(outbound, "password").orEmpty())
                "tuic" -> {
                    fields["uuid"] = YamlScalar(jsonString(outbound, "uuid").orEmpty())
                    fields["password"] = YamlScalar(jsonString(outbound, "password").orEmpty())
                }
                "ss" -> {
                    fields["cipher"] = YamlScalar(jsonString(outbound, "method").orEmpty())
                    fields["password"] = YamlScalar(jsonString(outbound, "password").orEmpty())
                }
                "hysteria2" -> fields["password"] = YamlScalar(jsonString(outbound, "password").orEmpty())
                "socks", "http" -> {
                    jsonString(outbound, "username")?.takeIf(String::isNotBlank)?.let { fields["username"] = YamlScalar(it) }
                    jsonString(outbound, "password")?.takeIf(String::isNotBlank)?.let { fields["password"] = YamlScalar(it) }
                }
            }
            val tls = extractJsonObject(outbound, "tls")
            if (tls?.let { jsonBoolean(it, "enabled") } == true) {
                fields["tls"] = YamlScalar("true", quoted = false)
                jsonString(tls, "server_name")?.takeIf(String::isNotBlank)?.let { fields["servername"] = YamlScalar(it) }
                if (jsonBoolean(tls, "insecure") == true) fields["skip-cert-verify"] = YamlScalar("true", quoted = false)
                extractJsonObject(tls, "reality")?.let { reality ->
                    val realityOpts = nested.getOrPut("reality-opts") { linkedMapOf() }
                    jsonString(reality, "public_key")?.takeIf(String::isNotBlank)?.let {
                        realityOpts["public-key"] = YamlScalar(it)
                    }
                    jsonString(reality, "short_id")?.takeIf(String::isNotBlank)?.let {
                        realityOpts["short-id"] = YamlScalar(it)
                    }
                }
            }
            val transport = extractJsonObject(outbound, "transport")
            if (transport?.let { jsonString(it, "type") } == "ws") {
                fields["network"] = YamlScalar("ws")
                val ws = linkedMapOf<String, YamlScalar>()
                jsonString(transport, "path")?.takeIf(String::isNotBlank)?.let { ws["path"] = YamlScalar(it) }
                extractJsonObject(transport, "headers")?.let { headers ->
                    jsonString(headers, "Host")?.let(::yamlHostHeader)?.let { ws["headers"] = YamlScalar(it, quoted = false) }
                }
                if (ws.isNotEmpty()) nested["ws-opts"] = ws
            } else if (transport?.let { jsonString(it, "type") } == "grpc") {
                fields["network"] = YamlScalar("grpc")
                jsonString(transport, "service_name")?.let {
                    nested.getOrPut("grpc-opts") { linkedMapOf() }["grpc-service-name"] = YamlScalar(it)
                }
            }
        }
    }

    private fun ProxySpec.addCommonTls(query: Map<String, String>) {
        query["security"]?.takeIf { it == "tls" || it == "reality" }?.let {
            fields["tls"] = YamlScalar("true", quoted = false)
        }
        query["sni"]?.let { fields["servername"] = YamlScalar(it) }
        query["fp"]?.let { fields["client-fingerprint"] = YamlScalar(it) }
        query["pbk"]?.let { nested.getOrPut("reality-opts") { linkedMapOf() }["public-key"] = YamlScalar(it) }
        query["sid"]?.let { nested.getOrPut("reality-opts") { linkedMapOf() }["short-id"] = YamlScalar(it) }
    }

    private fun ProxySpec.addTransport(query: Map<String, String>) {
        val network = query["type"] ?: query["network"] ?: return
        fields["network"] = YamlScalar(network)
        when (network) {
            "ws" -> {
                val ws = nested.getOrPut("ws-opts") { linkedMapOf() }
                query["path"]?.let { ws["path"] = YamlScalar(it) }
                query["host"]?.let(::yamlHostHeader)?.let { ws["headers"] = YamlScalar(it, quoted = false) }
            }
            "grpc" -> query["serviceName"]?.let {
                nested.getOrPut("grpc-opts") { linkedMapOf() }["grpc-service-name"] = YamlScalar(it)
            }
        }
    }

    private fun renderClash(specs: List<ProxySpec>): String = buildString {
        appendLine("proxies:")
        specs.forEach { spec ->
            appendLine("  - name: ${yamlQuote(spec.name)}")
            appendLine("    type: ${spec.type}")
            spec.fields.forEach { (key, value) ->
                appendLine("    $key: ${renderScalar(value)}")
            }
            spec.nested.forEach { (key, values) ->
                appendLine("    $key:")
                values.forEach { (nestedKey, value) ->
                    appendLine("      $nestedKey: ${renderScalar(value)}")
                }
            }
        }
    }

    private fun renderScalar(value: YamlScalar): String =
        if (value.quoted) yamlQuote(value.value) else value.value

    private fun yamlHostHeader(value: String): String? {
        if (value.isBlank() || value.any { it == '\r' || it == '\n' || it == '{' || it == '}' }) return null
        return "{Host: ${value.replace("'", "")}}"
    }

    private fun yamlQuote(value: String): String {
        val safe = value
            .filter { it == '\t' || it >= ' ' && it != '\u007f' }
            .replace('\r', ' ')
            .replace('\n', ' ')
        return "'${safe.replace("'", "''")}'"
    }

    private fun queryMap(rawQuery: String?): Map<String, String> = rawQuery.orEmpty()
        .split('&')
        .mapNotNull { field ->
            val index = field.indexOf('=')
            if (index <= 0) null else decodePart(field.substring(0, index)) to decodePart(field.substring(index + 1))
        }
        .toMap()

    private fun decodePart(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

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
            "socks",
            "socks5",
            "http",
            "anytls",
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
        val V2RAY_MARKER = SING_BOX_MARKER
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
        val V2RAY_PROTOCOL = Regex(""""protocol"\s*:\s*"([a-zA-Z0-9_-]+)"""")
        val V2RAY_SUPPORTED_TYPES = setOf("vmess", "vless", "trojan", "shadowsocks", "socks", "http")
        const val PROPERTY_INDENT = 2
        const val MAX_NODE_NAME_LENGTH = 200
    }
}
