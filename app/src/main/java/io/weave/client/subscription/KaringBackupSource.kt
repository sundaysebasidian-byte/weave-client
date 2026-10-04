package io.weave.client.subscription

import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Reads only the owner's official backup. No archive extraction or source-app private paths. */
internal class KaringBackupSource {
    fun catalogue(input: InputStream): List<ClientSourceRecord> {
        var compressed = 0L
        val bounded = object : FilterInputStream(input) {
            override fun read(): Int = super.read().also { if (it >= 0) checkSize(1) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also { if (it > 0) checkSize(it) }
            fun checkSize(count: Int) { compressed += count; require(compressed <= MAX_BYTES) { "订阅内容超过大小限制" } }
        }
        var index: String? = null
        var total = 0L
        val names = mutableSetOf<String>()
        ZipInputStream(bounded).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(names.size < 128 && names.add(name) && safeEntryName(name)) { "订阅文件格式无效" }
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var size = 0L
                while (true) {
                    val count = zip.read(buffer); if (count < 0) break
                    total += count; size += count
                    require(total <= MAX_BYTES && (name != INDEX || size <= 5L * 1024 * 1024)) { "订阅内容超过大小限制" }
                    if (name == INDEX) output.write(buffer, 0, count)
                }
                if (name == INDEX && !entry.isDirectory) index = LocalSubscriptionReader().read(output.toByteArray().inputStream())
                zip.closeEntry()
            }
        }
        val root = ClashYamlCodec.read(requireNotNull(index) { "请选择 Karing 官方备份文件" })
        val items = root["items"] as? List<*> ?: throw SubscriptionImportException("订阅文件格式无效")
        require(items.size <= 256) { "订阅文件格式无效" }
        val groupIds = mutableSetOf<String>()
        return items.map { value ->
            val group = value as? Map<*, *> ?: throw SubscriptionImportException("订阅文件格式无效")
            val groupId = group["groupid"] as? String ?: throw SubscriptionImportException("订阅文件格式无效")
            require(groupId.isNotBlank() && groupIds.add(groupId)) { "订阅文件格式无效" }
            val name = (group["remark"] as? String)?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: "Imported subscription"
            val servers = group["servers"] as? List<*> ?: throw SubscriptionImportException("订阅文件格式无效")
            require(servers.size <= 5000 && servers.all { it is Map<*, *> }) { "订阅文件格式无效" }
            val supported = servers.isNotEmpty() && servers.all { safeNode(it as Map<*, *>) }
            val reason = if (servers.isEmpty()) "订阅中没有可用节点" else if (!supported) "Karing 节点含未支持选项，请导出兼容配置" else null
            ClientSourceRecord(ClientSourceEntry(UUID.randomUUID().toString(), name, supported, reason), "local://user-selected-file", expectedNodes = servers.size, read = {
                require(supported) { reason ?: "订阅文件格式无效" }
                KaringDataJson.encode(mapOf("outbounds" to servers.map { raw ->
                    (raw as Map<*, *>).filterKeys { it !in METADATA }
                }))
            })
        }
    }
    private fun safeNode(node: Map<*, *>): Boolean {
        if (node.keys.any { it !in NODE_FIELDS && it !in METADATA }) return false
        if (NODE_FIELDS.minus(setOf("server_port", "tls", "transport")).any { node[it] != null && node[it] !is String }) return false
        val port = node["server_port"]
        if (port !is Int && port !is Long || (port as Number).toLong() !in 1..65535) return false
        if (node["type"] !in setOf("socks", "http", "shadowsocks", "vmess", "vless", "trojan", "hysteria2", "tuic")) return false
        val tls = node["tls"] as? Map<*, *>
        if (node["tls"] != null && tls == null) return false
        if (tls != null && tls.keys.any { it !in setOf("enabled", "server_name", "insecure", "reality") }) return false
        if (tls != null && (listOf("enabled", "insecure").any { tls[it] != null && tls[it] !is Boolean } ||
            tls["server_name"] != null && tls["server_name"] !is String)) return false
        val reality = tls?.get("reality") as? Map<*, *>
        if (tls?.get("reality") != null && reality == null) return false
        if (reality != null && reality["enabled"] != true) return false
        if (reality != null && reality.keys.any { it !in setOf("enabled", "public_key", "short_id") }) return false
        if (reality != null && listOf("public_key", "short_id").any { reality[it] != null && reality[it] !is String }) return false
        val transport = node["transport"] as? Map<*, *>
        if (node["transport"] != null && transport == null) return false
        if (transport != null && (transport["type"] !in setOf("ws", "grpc") || transport.keys.any {
            it !in setOf("type", "path", "headers", "service_name") })) return false
        if (transport != null && listOf("path", "service_name").any { transport[it] != null && transport[it] !is String }) return false
        val headers = transport?.get("headers") as? Map<*, *>
        if (transport?.get("headers") != null && headers == null) return false
        if (headers != null && (headers.keys.any { it != "Host" } || headers.values.any { it !is String })) return false
        return true
    }
    private fun safeEntryName(name: String): Boolean = name.isNotBlank() && name.length <= 512 &&
        !name.startsWith('/') && '\\' !in name && ':' !in name && '\u0000' !in name &&
        name.removeSuffix("/").split('/').none { it.isBlank() || it == "." || it == ".." }
    private companion object {
        const val INDEX = "karing_subscribe.json"
        const val MAX_BYTES = 20L * 1024 * 1024
        val METADATA = setOf("groupid", "attach", "latency", "outlet_ip", "outlet_region")
        val NODE_FIELDS = setOf("type", "tag", "server", "server_port", "uuid", "password", "username", "method", "tls", "transport")
    }
}

/** SafeConstructor's maps/lists/scalars are emitted as data-only JSON for the existing parser. */
internal object KaringDataJson {
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"'); value.forEach { c -> when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\")
                '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            } }; append('"')
        }
        is Boolean -> value.toString()
        is Number -> value.toString().also { require(it.toDoubleOrNull()?.isFinite() == true) { "订阅文件格式无效" } }
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { require(it.key is String); encode(it.key) + ":" + encode(it.value) }
        is List<*> -> value.joinToString(",", "[", "]", transform = ::encode)
        else -> throw SubscriptionImportException("订阅文件格式无效")
    }
}
