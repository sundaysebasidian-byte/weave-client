package io.weave.client.routing

import android.content.Context
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class LocalRuleType(val label: String) {
    DOMAIN("完整域名"),
    DOMAIN_SUFFIX("域名后缀"),
    DOMAIN_KEYWORD("域名关键词"),
    IP_CIDR("IPv4 网段"),
    IP_CIDR6("IPv6 网段"),
}

enum class LocalRuleAction(val label: String) {
    DEFAULT("跟随默认出口"),
    DIRECT("直连"),
    REJECT("阻止"),
}

data class LocalRouteRule(
    val id: String = UUID.randomUUID().toString(),
    val type: LocalRuleType,
    val value: String,
    val action: LocalRuleAction,
    val enabled: Boolean = true,
)

class LocalRouteRuleException(message: String) : IllegalArgumentException(message)

/** Strict, offline validation shared by the editor, matcher and compiler. */
object LocalRouteRuleValidator {
    const val MAX_RULES = 256
    const val MAX_VALUE_LENGTH = 253

    fun normalize(rule: LocalRouteRule): LocalRouteRule {
        val id = rule.id.trim()
        require(UUID_REGEX.matches(id)) { "规则 ID 无效" }
        val value = rule.value.trim().lowercase()
        validate(rule.type, value)
        return rule.copy(id = id, value = value)
    }

    fun validate(type: LocalRuleType, value: String) {
        require(value.isNotBlank() && value.length <= MAX_VALUE_LENGTH) { "规则值长度无效" }
        require(!value.any { it == '\n' || it == '\r' || it == ',' }) {
            "规则值不能包含换行或逗号"
        }
        when (type) {
            LocalRuleType.DOMAIN,
            LocalRuleType.DOMAIN_SUFFIX,
            -> require(DOMAIN.matches(value.removePrefix("."))) { "域名无效" }
            LocalRuleType.DOMAIN_KEYWORD -> require(KEYWORD.matches(value)) { "域名关键词无效" }
            LocalRuleType.IP_CIDR -> require(parseIpv4Cidr(value) != null) { "IPv4 网段无效" }
            LocalRuleType.IP_CIDR6 -> require(parseIpv6Cidr(value) != null) { "IPv6 网段无效" }
        }
    }

    internal fun parseIpv4Cidr(value: String): Ipv4Cidr? {
        val parts = value.split('/')
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val octets = parts[0].split('.')
        if (octets.size != 4) return null
        val numbers = octets.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it !in 0..255 }) return null
        val address = numbers.fold(0) { result, octet -> (result shl 8) or octet }
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        return Ipv4Cidr(address and mask, mask)
    }

    internal fun parseIpv6Cidr(value: String): Ipv6Cidr? {
        val slash = value.lastIndexOf('/')
        if (slash <= 0 || slash == value.lastIndex) return null
        val prefix = value.substring(slash + 1).toIntOrNull() ?: return null
        if (prefix !in 0..128 || value.substring(0, slash).any { it.isLetter() && it !in 'a'..'f' }) {
            return null
        }
        val address = runCatching { InetAddress.getByName(value.substring(0, slash)) }.getOrNull()
            ?: return null
        if (address.address.size != 16) return null
        val bytes = address.address.copyOf()
        val mask = ByteArray(16) { index ->
            val remaining = prefix - index * 8
            when {
                remaining >= 8 -> 0xff.toByte()
                remaining <= 0 -> 0
                else -> (0xff shl (8 - remaining)).toByte()
            }
        }
        for (index in bytes.indices) bytes[index] = (bytes[index].toInt() and mask[index].toInt()).toByte()
        return Ipv6Cidr(bytes, mask)
    }

    private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    private val DOMAIN = Regex("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
    private val KEYWORD = Regex("(?i)[a-z0-9][a-z0-9._-]{0,63}")
}

internal data class Ipv4Cidr(val network: Int, val mask: Int)
internal data class Ipv6Cidr(val network: ByteArray, val mask: ByteArray)

object LocalRuleMatcher {
    fun firstMatch(host: String, ip: String?, rules: List<LocalRouteRule>): LocalRouteRule? {
        val normalizedHost = host.trim().trimEnd('.').lowercase()
        val normalizedIp = ip?.trim().orEmpty()
        return rules.asSequence()
            .filter { it.enabled }
            .mapNotNull { rule ->
                val matches = when (rule.type) {
                    LocalRuleType.DOMAIN -> normalizedHost == rule.value
                    LocalRuleType.DOMAIN_SUFFIX -> normalizedHost == rule.value ||
                        normalizedHost.endsWith(".${rule.value}")
                    LocalRuleType.DOMAIN_KEYWORD -> normalizedHost.contains(rule.value)
                    LocalRuleType.IP_CIDR -> normalizedIpMatches(normalizedIp, rule.value)
                    LocalRuleType.IP_CIDR6 -> normalizedIpv6Matches(normalizedIp, rule.value)
                }
                rule.takeIf { matches }
            }
            .firstOrNull()
    }

    private fun normalizedIpMatches(ip: String, cidr: String): Boolean {
        val address = ip.substringBefore('/').takeIf { it.matches(Regex("[0-9.]+")) } ?: return false
        val parsed = LocalRouteRuleValidator.parseIpv4Cidr(cidr) ?: return false
        val host = LocalRouteRuleValidator.parseIpv4Cidr("$address/32")?.network ?: return false
        return host and parsed.mask == parsed.network
    }

    private fun normalizedIpv6Matches(ip: String, cidr: String): Boolean {
        val address = ip.substringBefore('/').takeIf { it.contains(':') } ?: return false
        val parsed = LocalRouteRuleValidator.parseIpv6Cidr(cidr) ?: return false
        val host = LocalRouteRuleValidator.parseIpv6Cidr("$address/128") ?: return false
        return host.network.indices.all { index ->
            (host.network[index].toInt() and parsed.mask[index].toInt()) ==
                (parsed.network[index].toInt() and parsed.mask[index].toInt())
        }
    }
}

object LocalRuleCompiler {
    fun compile(rules: List<LocalRouteRule>): List<String> = rules
        .filter(LocalRouteRule::enabled)
        .map(LocalRouteRuleValidator::normalize)
        .distinctBy { it.id }
        .map { rule ->
            val type = when (rule.type) {
                LocalRuleType.DOMAIN -> "DOMAIN"
                LocalRuleType.DOMAIN_SUFFIX -> "DOMAIN-SUFFIX"
                LocalRuleType.DOMAIN_KEYWORD -> "DOMAIN-KEYWORD"
                LocalRuleType.IP_CIDR -> "IP-CIDR"
                LocalRuleType.IP_CIDR6 -> "IP-CIDR6"
            }
            val action = when (rule.action) {
                LocalRuleAction.DEFAULT -> "DEFAULT"
                LocalRuleAction.DIRECT -> "DIRECT"
                LocalRuleAction.REJECT -> "REJECT"
            }
            val suffix = if (rule.type == LocalRuleType.IP_CIDR || rule.type == LocalRuleType.IP_CIDR6) {
                ",no-resolve"
            } else {
                ""
            }
            "$type,${escape(rule.value)},$action$suffix"
        }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace(",", "\\,")
}

class LocalRouteRuleStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)
    private val associatedData = "weave.local-route-rules.v1".toByteArray(Charsets.UTF_8)

    @Synchronized
    fun list(): List<LocalRouteRule> = if (!file.isFile) {
        emptyList()
    } else {
        runCatching {
            val raw = secretBox.decrypt(file.readText(Charsets.UTF_8), associatedData)
                .toString(Charsets.UTF_8)
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LocalRouteRule(
                    id = item.getString("id"),
                    type = LocalRuleType.valueOf(item.getString("type")),
                    value = item.getString("value"),
                    action = LocalRuleAction.valueOf(item.getString("action")),
                    enabled = item.optBoolean("enabled", true),
                ).let(LocalRouteRuleValidator::normalize)
            }.take(LocalRouteRuleValidator.MAX_RULES)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(rules: List<LocalRouteRule>) {
        require(rules.size <= LocalRouteRuleValidator.MAX_RULES) {
            "最多保存 ${LocalRouteRuleValidator.MAX_RULES} 条本地规则"
        }
        val normalized = rules.map(LocalRouteRuleValidator::normalize)
        require(normalized.map(LocalRouteRule::id).distinct().size == normalized.size) { "规则 ID 重复" }
        val array = JSONArray().apply {
            normalized.forEach { rule ->
                put(
                    JSONObject()
                        .put("id", rule.id)
                        .put("type", rule.type.name)
                        .put("value", rule.value)
                        .put("action", rule.action.name)
                        .put("enabled", rule.enabled),
                )
            }
        }
        val encrypted = secretBox.encrypt(array.toString().toByteArray(Charsets.UTF_8), associatedData)
        val pending = File(file.parentFile, "${file.name}.pending")
        pending.writeText(encrypted, Charsets.UTF_8)
        runCatching {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            require(pending.renameTo(file)) { "无法保存本地路由规则" }
        }
    }

    private companion object {
        const val FILE_NAME = "local-route-rules.enc"
    }
}
