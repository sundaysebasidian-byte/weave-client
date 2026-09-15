package io.weave.client.subscription

import java.net.URI
import java.util.Base64
import java.util.Collections
import java.util.IdentityHashMap
import java.util.regex.Pattern
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver

/**
 * Materializes explicit subscription providers at import/update time, not at VPN startup.
 * A failed child aborts the transaction; callers keep the previous encrypted subscription.
 * Download callbacks must enforce HTTPS, redirect/address policy and response-size limits.
 */
internal class ClashProviderResolver(
    private val fetch: (String) -> SubscriptionFetchResult,
    private val urlPolicy: SubscriptionUrlPolicy = SubscriptionUrlPolicy(),
) {
    data class Resolution(val payload: String, val rootNodes: Int?, val providerNodes: Int, val collections: Int)

    fun resolve(input: String, source: String): String = resolveDetailed(input, source).payload

    fun resolveDetailed(input: String, source: String): Resolution {
        val document = documentWithProviders(input) ?: return Resolution(input, null, 0, 0)
        val root = ClashYamlCodec.read(document)
        val providers = root["proxy-providers"] as? Map<*, *>
            ?: throw SubscriptionImportException("订阅节点集合格式无效")
        if (providers.isEmpty()) return Resolution(input, null, 0, 0)
        requireImport(providers.size <= MAX_PROVIDERS, "订阅引用的节点集合过多")
        val output = ClashYamlCodec.nodes(root).toMutableList()
        val rootCount = output.size
        var providerCount = 0
        val downloaded = mutableMapOf<URI, List<Map<String, Any?>>>()
        var totalBytes = document.toByteArray(Charsets.UTF_8).size

        providers.values.forEach { value ->
            val provider = value as? Map<*, *>
                ?: throw SubscriptionImportException("订阅节点集合格式无效")
            // These need native regexp/override semantics. Do not silently alter the selected
            // node set or copy arbitrary control-plane options into individual proxies.
            requireImport(
                listOf("filter", "exclude-filter", "exclude-type").none {
                    provider[it]?.toString()?.isNotBlank() == true
                },
                "节点集合带有导入过滤条件，请使用不含过滤条件的节点订阅",
            )
            requireImport((provider["header"] as? Map<*, *>)?.isNotEmpty() != true,
                "节点集合需要自定义请求头，请导入完整节点文件")
            val nodes = when (provider["type"]?.toString()?.lowercase()) {
                "http" -> {
                    val url = provider["url"] as? String
                        ?: throw SubscriptionImportException("节点集合缺少 HTTPS 地址")
                    val resolved = runCatching { URI(source).resolve(url).toString() }
                        .getOrElse { throw SubscriptionImportException("节点集合地址格式无效") }
                    val uri = urlPolicy.validate(resolved)
                    requireImport(uri.toString() != source, "节点集合不能引用订阅自身")
                    downloaded.getOrPut(uri) {
                        val response = fetch(uri.toString())
                        totalBytes += response.body.toByteArray(Charsets.UTF_8).size
                        requireImport(totalBytes <= MAX_TOTAL_BYTES, "节点集合总大小超过限制")
                        // Providers may wrap the YAML/JSON node file in Base64, just like main
                        // subscriptions. Resolve that envelope before reading the data map.
                        val child = ClashYamlCodec.read(ClashSubscriptionDocument.unwrap(response.body))
                        requireImport(!child.containsKey("proxy-providers"), "不支持循环或多层节点集合")
                        ClashYamlCodec.nodes(child).also {
                            requireImport(it.isNotEmpty(), "节点集合未返回可用节点，原订阅已保留")
                        }
                    }
                }
                "inline" -> ClashYamlCodec.nodeList(provider["payload"])
                else -> throw SubscriptionImportException("仅支持 HTTPS 或内嵌节点集合，请导入完整节点文件")
            }
            val overrides = provider["override"] as? Map<*, *> ?: emptyMap<Any, Any>()
            providerCount += nodes.size
            val allowed = setOf("additional-prefix", "additional-suffix", "udp", "skip-cert-verify", "ip-version")
            requireImport(overrides.keys.all { it in allowed }, "节点集合包含暂不支持的覆盖字段，请导入完整节点文件")
            nodes.forEach { original ->
                val node = original.toMutableMap()
                val name = node["name"]?.toString()
                    ?: throw SubscriptionImportException("节点集合中有节点缺少名称")
                node["name"] = overrides["additional-prefix"]?.toString().orEmpty() + name +
                    overrides["additional-suffix"]?.toString().orEmpty()
                for (key in listOf("udp", "skip-cert-verify", "ip-version")) {
                    if (overrides.containsKey(key)) node[key] = overrides[key]
                }
                output += node
            }
            requireImport(output.size <= MAX_NODES, "订阅节点数量超过限制")
        }
        requireImport(output.isNotEmpty(), "订阅中没有可用节点")
        // Same child URL may be used by several identical provider declarations. Deduplicate
        // exact objects only; preserve distinct nodes instead of merging by display name.
        return Resolution(ClashYamlCodec.write(mapOf("proxies" to output.distinct())), rootCount, providerCount, providers.size)
    }

    private fun documentWithProviders(input: String): String? {
        val plain = ClashSubscriptionDocument.unwrap(input)
        return plain.takeIf { ClashSubscriptionDocument.hasRootKey(it, "proxy-providers") }
    }

    private fun requireImport(value: Boolean, message: String) {
        if (!value) throw SubscriptionImportException(message)
    }

    private companion object {
        const val MAX_PROVIDERS = 16
        const val MAX_NODES = 10_000
        const val MAX_TOTAL_BYTES = 5 * 1024 * 1024
    }
}

/** Data-only YAML with bounded aliases/depth/expansion and no reflective deserialization. */
internal object ClashYamlCodec {
    private fun yaml(): Yaml {
        val loader = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            // A large legitimate subscription can merge the same defaults into hundreds of
            // nodes. Expanded value/depth limits below still reject alias bombs and cycles.
            maxAliasesForCollections = 1000
            nestingDepthLimit = 40
            codePointLimit = 5 * 1024 * 1024
        }
        val dumper = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isAllowUnicode = true
        }
        val resolver = object : Resolver() {
            override fun addImplicitResolvers() {
                // Mihomo treats plain yes/no/on/off as strings, not YAML 1.1 booleans.
                addImplicitResolver(Tag.BOOL, Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$"), "tTfF")
                addImplicitResolver(Tag.INT, INT, "-+0123456789")
                addImplicitResolver(Tag.FLOAT, FLOAT, "-+0123456789.")
                addImplicitResolver(Tag.MERGE, MERGE, "<")
                addImplicitResolver(Tag.NULL, NULL, "~nN\u0000")
                addImplicitResolver(Tag.NULL, EMPTY, null)
            }
        }
        return Yaml(SafeConstructor(loader), Representer(dumper), dumper, loader, resolver).apply {
            // Android has no java.beans package. This codec handles maps/lists/scalars only;
            // force the Android-compatible accessor path even in an obfuscated build.
            setBeanAccess(BeanAccess.FIELD)
        }
    }

    fun read(document: String): Map<String, Any?> {
        try {
            val raw = yaml().load<Any?>(document)
            val visiting = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
            var remaining = 150_000
            fun copy(value: Any?, depth: Int): Any? {
                check(depth <= 40 && --remaining >= 0)
                return when (value) {
                    null, is String, is Number, is Boolean -> value
                    is Map<*, *> -> {
                        check(visiting.add(value))
                        val result = value.entries.associate { (key, child) ->
                            check(key is String)
                            key to copy(child, depth + 1)
                        }
                        visiting.remove(value)
                        result
                    }
                    is List<*> -> {
                        check(visiting.add(value))
                        val result = value.map { copy(it, depth + 1) }
                        visiting.remove(value)
                        result
                    }
                    else -> error("Unsupported YAML value")
                }
            }
            @Suppress("UNCHECKED_CAST")
            return copy(raw, 0) as? Map<String, Any?> ?: error("Mapping required")
        } catch (_: Exception) {
            // Parser exceptions can quote whole lines, including passwords and subscription URLs.
            throw SubscriptionImportException("Clash YAML 格式无效或超过安全解析限制")
        }
    }

    fun nodes(root: Map<String, Any?>): List<Map<String, Any?>> {
        val value = root["proxies"]
        val list = value as? List<*> ?: return nodeList(value)
        val tail = list.indexOfFirst { it !is Map<*, *> }
        if (tail <= 0) return nodeList(value)
        // Pre-alpha70's line-based sanitizer removed an unindented `rules:` heading but
        // retained its `- DOMAIN,...` lines. YAML then attached thousands of rule strings to
        // the preceding proxies sequence. Old metadata skipped them; strict startup rejected
        // the whole subscription, including unrelated app routes/default exits.
        // Recover only that precise shape: complete node maps followed exclusively by known
        // rule strings. Never execute the tail, drop arbitrary malformed nodes, or modify the
        // encrypted source. Current normalization writes only these actual proxy objects.
        val nodes = list.take(tail)
        if (nodes.all { item ->
                val node = item as Map<*, *>
                return@all !node["name"]?.toString().isNullOrBlank() && !node["type"]?.toString().isNullOrBlank()
            } && list.drop(tail).all(::isLegacyRuleLine)
        ) return nodeList(nodes)
        return nodeList(value)
    }

    private fun isLegacyRuleLine(value: Any?): Boolean {
        if (value !is String || value.any { it == '\n' || it == '\r' }) return false
        val parts = value.split(',')
        val type = parts.firstOrNull() ?: return false
        if (type !in LEGACY_RULE_TYPES) return false
        val minimum = if (type == "MATCH" || type == "FINAL") 2 else 3
        return parts.size >= minimum && parts.all { it.isNotBlank() }
    }

    private val LEGACY_RULE_TYPES = setOf(
        "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-REGEX", "GEOSITE", "GEOIP",
        "IP-CIDR", "IP-CIDR6", "IP-SUFFIX", "IP-ASN", "SRC-IP-CIDR", "SRC-IP-SUFFIX",
        "SRC-GEOIP", "SRC-IP-ASN", "DST-PORT", "SRC-PORT", "IN-PORT", "IN-TYPE", "IN-USER",
        "PROCESS-NAME", "PROCESS-PATH", "PROCESS-NAME-REGEX", "PROCESS-PATH-REGEX",
        "RULE-SET", "NETWORK", "UID", "MATCH", "FINAL",
    )

    fun nodeList(value: Any?): List<Map<String, Any?>> {
        if (value == null) return emptyList()
        val list = value as? List<*> ?: throw SubscriptionImportException("节点列表格式无效")
        @Suppress("UNCHECKED_CAST")
        return list.map { it as? Map<String, Any?> ?: throw SubscriptionImportException("节点字段格式无效") }
    }

    fun write(value: Map<String, Any?>): String = yaml().dump(value)
}
