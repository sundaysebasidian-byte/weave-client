package io.weave.client.routing

import android.content.Context
import androidx.compose.runtime.Immutable
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

@Immutable
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
        val input = rule.value.trim().lowercase()
        // Older versions accepted signs, leading zeroes and Unicode decimal digits. Keep the
        // same numerical meaning for saved profiles but never pass those spellings to the core.
        val value = if (rule.type == LocalRuleType.IP_CIDR && parseIpv4Cidr(input) == null) {
            canonicalLegacyIpv4(input) ?: input
        } else input
        validate(rule.type, value)
        val canonical = when (rule.type) {
            LocalRuleType.DOMAIN, LocalRuleType.DOMAIN_SUFFIX -> value.removePrefix(".")
            else -> value
        }
        return rule.copy(id = id, value = canonical)
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

    private fun canonicalLegacyIpv4(text: String): String? {
        val parts = text.split('/')
        if (parts.size != 2) return null
        val prefix = parts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return null
        val octets = parts[0].split('.')
        if (octets.size != 4) return null
        return octets.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
            .joinToString(".", postfix = "/$prefix")
    }

    internal fun parseIpv4Cidr(value: String): Ipv4Cidr? {
        val slash = value.indexOf('/')
        if (slash < 0) return null
        val prefix = decimal(value, slash + 1, value.length, 32) ?: return null
        val address = parseIpv4Address(value, slash) ?: return null
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        return Ipv4Cidr(address and mask, mask)
    }

    // Accept only portable dotted decimal, without signs, Unicode digits or octal ambiguity.
    internal fun parseIpv4Address(text: String, end: Int): Int? {
        var address = 0
        var start = 0
        repeat(4) { index ->
            var next = start
            while (next < end && text[next] != '.') next++
            if ((index == 3) != (next == end)) return null
            val byte = decimal(text, start, next, 255) ?: return null
            address = (address shl 8) or byte
            start = next + 1
        }
        return address
    }

    private fun decimal(text: String, start: Int, end: Int, max: Int): Int? {
        if (start >= end || (end - start > 1 && text[start] == '0')) return null
        var result = 0
        for (index in start until end) {
            val digit = text[index] - '0'
            if (digit !in 0..9) return null
            result = result * 10 + digit
            if (result > max) return null
        }
        return result
    }

    internal fun parseIpv6Cidr(value: String): Ipv6Cidr? {
        val slash = value.lastIndexOf('/')
        if (slash <= 0 || slash == value.lastIndex) return null
        val prefix = value.substring(slash + 1).toIntOrNull() ?: return null
        if (prefix !in 0..128) return null
        val bytes = parseIpv6Address(value.substring(0, slash)) ?: return null
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

    internal fun parseIpv6Address(literal: String, lookup: (String) -> InetAddress = InetAddress::getByName): ByteArray? {
        // A hex-looking hostname without a colon would otherwise reach the system DNS resolver.
        if (':' !in literal || literal.any { it.isLetter() && it.lowercaseChar() !in 'a'..'f' }) return null
        return runCatching { lookup(literal).address }.getOrNull()?.takeIf { it.size == 16 }
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
        var ipv4Checked = false
        var ipv4: Int? = null
        var ipv6Checked = false
        var ipv6: ByteArray? = null
        for (rule in rules) {
            if (!rule.enabled) continue
            val matches = when (rule.type) {
                LocalRuleType.DOMAIN -> normalizedHost == rule.value
                LocalRuleType.DOMAIN_SUFFIX -> domainSuffixMatches(normalizedHost, rule.value)
                LocalRuleType.DOMAIN_KEYWORD -> normalizedHost.contains(rule.value)
                LocalRuleType.IP_CIDR -> {
                    if (!ipv4Checked) {
                        ipv4Checked = true
                        val end = normalizedIp.indexOf('/').let { if (it < 0) normalizedIp.length else it }
                        ipv4 = LocalRouteRuleValidator.parseIpv4Address(normalizedIp, end)
                    }
                    val address = ipv4
                    val cidr = if (address != null) LocalRouteRuleValidator.parseIpv4Cidr(rule.value) else null
                    address != null && cidr != null && address and cidr.mask == cidr.network
                }
                LocalRuleType.IP_CIDR6 -> {
                    if (!ipv6Checked) {
                        ipv6Checked = true
                        ipv6 = LocalRouteRuleValidator.parseIpv6Address(normalizedIp.substringBefore('/'))
                    }
                    ipv6?.let { ipv6Matches(it, rule.value) } == true
                }
            }
            if (matches) return rule
        }
        return null
    }

    private fun domainSuffixMatches(host: String, suffix: String): Boolean {
        if (host == suffix) return true
        val boundary = host.length - suffix.length - 1
        return boundary >= 0 && host[boundary] == '.' && host.endsWith(suffix)
    }

    private fun ipv6Matches(address: ByteArray, cidr: String): Boolean {
        val parsed = LocalRouteRuleValidator.parseIpv6Cidr(cidr) ?: return false
        return address.indices.all { index ->
            (address[index].toInt() and parsed.mask[index].toInt()) == parsed.network[index].toInt()
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
    private val appContext = context.applicationContext
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)
    private val associatedData = "weave.local-route-rules.v1".toByteArray(Charsets.UTF_8)

    internal fun snapshot(): List<LocalRouteRule> = io.weave.client.data.ProfileStorage.access(appContext) {
        if (!file.exists()) return@access emptyList()
        val array = JSONArray(secretBox.decrypt(file.readText(), associatedData).toString(Charsets.UTF_8))
        require(array.length() <= LocalRouteRuleValidator.MAX_RULES)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            LocalRouteRuleValidator.normalize(LocalRouteRule(item.getString("id"), LocalRuleType.valueOf(item.getString("type")),
                item.getString("value"), LocalRuleAction.valueOf(item.getString("action")), item.getBoolean("enabled")))
        }.also { require(it.map { rule -> rule.id }.toSet().size == it.size) }
    }

    fun list(): List<LocalRouteRule> = io.weave.client.data.ProfileStorage.access(appContext) {
        runCatching { snapshot() }.getOrDefault(emptyList())
    }

    internal fun edit(transform: (List<LocalRouteRule>) -> List<LocalRouteRule>): List<LocalRouteRule> =
        io.weave.client.data.ProfileStorage.access(appContext) {
            val current = try {
                snapshot()
            } catch (error: Exception) {
                throw IllegalStateException("规则更新失败", error)
            }
            transform(current).also(::save)
        }

    fun save(rules: List<LocalRouteRule>): Unit = io.weave.client.data.ProfileStorage.access(appContext) {
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
