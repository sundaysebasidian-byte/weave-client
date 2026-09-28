package io.weave.client.routing

import android.content.Context
import androidx.compose.runtime.Immutable
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import io.weave.client.subscription.ClashYamlCodec
import io.weave.client.subscription.SafeSubscriptionFetcher
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class RuleSetBehavior(val label: String, val mihomo: String) {
    DOMAIN("域名列表", "domain"),
    IPCIDR("IP 网段列表", "ipcidr"),
    CLASSICAL("经典规则", "classical"),
}

@Immutable
data class RemoteRuleSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** HTTPS source. Kept encrypted: rule-set URLs can embed access tokens. */
    val url: String,
    val behavior: RuleSetBehavior,
    val action: LocalRuleAction,
    val enabled: Boolean = true,
    val sha256: String? = null,
    val entryCount: Int = 0,
    val updatedAtMillis: Long? = null,
)

class RuleSetException(message: String) : IllegalArgumentException(message)

/**
 * Parses text (`one entry per line`) or YAML (`payload:`) rule lists and validates every entry.
 * Anything Weave cannot validate is rejected instead of being passed to the core.
 */
object RuleSetParser {
    const val MAX_ENTRIES_PER_SET = 100_000
    const val MAX_TOTAL_ENTRIES = 200_000
    const val MAX_SETS = 32
    private const val MAX_ENTRY_LENGTH = 300

    private val DOMAIN = Regex("""(?i)(?:\+\.|\*\.|\.)?(?:[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?\.)*[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?""")
    private val CLASSICAL_TYPES = setOf(
        "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "IP-CIDR", "IP-CIDR6", "DST-PORT",
        "PROCESS-NAME", "GEOSITE", "GEOIP", "IP-ASN",
    )
    private val PORT = Regex("""\d{1,5}(?:-\d{1,5})?""")
    private val TOKEN = Regex("""[A-Za-z0-9_.@!+-]{1,128}""")

    fun parse(body: String, behavior: RuleSetBehavior): List<String> {
        val trimmed = body.trimStart('﻿')
        val raw = if (trimmed.lineSequence().any { it.trimStart().startsWith("payload:") }) {
            val root = runCatching { ClashYamlCodec.read(trimmed) }
                .getOrElse { throw RuleSetException("规则集 YAML 格式无效") }
            (root["payload"] as? List<*>)?.map { it?.toString().orEmpty() }
                ?: throw RuleSetException("规则集缺少 payload 列表")
        } else {
            trimmed.lineSequence().toList()
        }
        val entries = raw.asSequence()
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
            .map { normalize(it, behavior) }
            .distinct()
            .toList()
        if (entries.isEmpty()) throw RuleSetException("规则集没有有效条目")
        if (entries.size > MAX_ENTRIES_PER_SET) throw RuleSetException("规则集条目超过 $MAX_ENTRIES_PER_SET 条上限")
        return entries
    }

    private fun normalize(entry: String, behavior: RuleSetBehavior): String {
        if (entry.length > MAX_ENTRY_LENGTH || entry.any { it.isISOControl() }) throw invalid()
        return when (behavior) {
            RuleSetBehavior.DOMAIN -> entry.lowercase().also { if (!DOMAIN.matches(it)) throw invalid() }
            RuleSetBehavior.IPCIDR -> entry.also { if (!validCidr(it)) throw invalid() }
            RuleSetBehavior.CLASSICAL -> classical(entry)
        }
    }

    private fun classical(entry: String): String {
        val parts = entry.split(',').map(String::trim)
        val type = parts.first().uppercase()
        if (type !in CLASSICAL_TYPES || parts.size !in 2..3) throw invalid()
        val value = parts[1]
        val option = parts.getOrNull(2)
        if (option != null && (option != "no-resolve" || type !in setOf("IP-CIDR", "IP-CIDR6", "GEOIP", "IP-ASN"))) {
            throw invalid()
        }
        val valid = when (type) {
            "DOMAIN", "DOMAIN-SUFFIX" -> DOMAIN.matches(value.removePrefix("."))
            "IP-CIDR", "IP-CIDR6" -> validCidr(value)
            "DST-PORT" -> PORT.matches(value) && value.split('-').all { (it.toIntOrNull() ?: -1) in 0..65535 }
            "IP-ASN" -> value.toLongOrNull()?.let { it in 0..4_294_967_295 } == true
            else -> TOKEN.matches(value)
        }
        if (!valid) throw invalid()
        return listOfNotNull(type, value, option).joinToString(",")
    }

    private fun validCidr(value: String): Boolean =
        LocalRouteRuleValidator.parseIpv4Cidr(value) != null ||
            LocalRouteRuleValidator.parseIpv6Cidr(value) != null

    private fun invalid() = RuleSetException("规则集包含无法校验的条目，已拒绝导入")

    fun sha256(entries: List<String>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(entries.joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

object RuleSetCompiler {
    fun providerName(set: RemoteRuleSet) = "weave_rs_${set.id.filter(Char::isLetterOrDigit).take(32)}"

    /** `rule-providers:` block with inline payloads, or empty when nothing is enabled. */
    fun providers(sets: List<Pair<RemoteRuleSet, List<String>>>, quote: (String) -> String): String = buildString {
        val active = sets.filter { it.first.enabled && it.second.isNotEmpty() }
        if (active.isEmpty()) return@buildString
        appendLine("rule-providers:")
        active.forEach { (set, entries) ->
            appendLine("  ${quote(providerName(set))}:")
            appendLine("    type: inline")
            appendLine("    behavior: ${set.behavior.mihomo}")
            appendLine("    payload:")
            entries.forEach { appendLine("      - ${quote(it)}") }
        }
    }

    fun rules(sets: List<Pair<RemoteRuleSet, List<String>>>): List<String> = sets
        .filter { it.first.enabled && it.second.isNotEmpty() }
        .map { (set, _) ->
            val action = when (set.action) {
                LocalRuleAction.DEFAULT -> "DEFAULT"
                LocalRuleAction.DIRECT -> "DIRECT"
                LocalRuleAction.REJECT -> "REJECT"
            }
            // IP lists match the connection's IP only; never trigger a DNS lookup for them.
            val suffix = if (set.behavior == RuleSetBehavior.IPCIDR) ",no-resolve" else ""
            "RULE-SET,${providerName(set)},$action$suffix"
        }
}

/**
 * Encrypted index (names, URLs, hashes) plus one encrypted payload file per set. Files are
 * replaced atomically, so the VPN process can read while the UI updates a set.
 */
class RemoteRuleSetStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
    private val fetcher: SafeSubscriptionFetcher = SafeSubscriptionFetcher(),
) {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY)
    private val index = File(directory, INDEX_FILE)

    @Synchronized
    fun list(): List<RemoteRuleSet> {
        if (!index.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(secretBox.decrypt(index.readText(), INDEX_AAD).toString(Charsets.UTF_8))
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                RemoteRuleSet(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    url = item.getString("url"),
                    behavior = RuleSetBehavior.valueOf(item.getString("behavior")),
                    action = LocalRuleAction.valueOf(item.getString("action")),
                    enabled = item.optBoolean("enabled", true),
                    sha256 = item.optString("sha256").ifBlank { null },
                    entryCount = item.optInt("entries"),
                    updatedAtMillis = item.optLong("updated").takeIf { it > 0 },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Enabled sets with their validated entries, for the config assembler. */
    fun active(): List<Pair<RemoteRuleSet, List<String>>> {
        var total = 0
        return list().filter(RemoteRuleSet::enabled).mapNotNull { set ->
            val entries = runCatching { readEntries(set.id) }.getOrNull() ?: return@mapNotNull null
            if (set.sha256 != null && RuleSetParser.sha256(entries) != set.sha256) return@mapNotNull null
            total += entries.size
            if (total > RuleSetParser.MAX_TOTAL_ENTRIES) return@mapNotNull null
            set to entries
        }
    }

    /** Downloads, validates and stores a set. Blocking; call on an IO dispatcher. */
    fun refresh(candidate: RemoteRuleSet): RemoteRuleSet {
        val name = candidate.name.trim().take(60).ifEmpty { throw RuleSetException("请填写规则集名称") }
        if (!candidate.url.trim().startsWith("https://", ignoreCase = true)) {
            throw RuleSetException("规则集地址必须使用 HTTPS")
        }
        val existing = list()
        if (existing.none { it.id == candidate.id } && existing.size >= RuleSetParser.MAX_SETS) {
            throw RuleSetException("最多添加 ${RuleSetParser.MAX_SETS} 个规则集")
        }
        val body = fetcher.fetch(candidate.url.trim(), adaptMainSubscription = false).body
        val entries = RuleSetParser.parse(body, candidate.behavior)
        val otherEntries = existing.filter { it.id != candidate.id && it.enabled }.sumOf { it.entryCount }
        if (otherEntries + entries.size > RuleSetParser.MAX_TOTAL_ENTRIES) {
            throw RuleSetException("所有规则集合计超过 ${RuleSetParser.MAX_TOTAL_ENTRIES} 条上限")
        }
        val stored = candidate.copy(
            name = name,
            url = candidate.url.trim(),
            sha256 = RuleSetParser.sha256(entries),
            entryCount = entries.size,
            updatedAtMillis = System.currentTimeMillis(),
        )
        synchronized(this) {
            directory.mkdirs()
            writeAtomic(payloadFile(stored.id), secretBox.encrypt(entries.joinToString("\n").toByteArray(Charsets.UTF_8), payloadAad(stored.id)))
            saveIndex(list().filterNot { it.id == stored.id } + stored)
        }
        return stored
    }

    /** Validated entries of one stored set (for backups). */
    fun entries(id: String): List<String> = readEntries(id)

    /** Stores a set restored from a backup after re-validating every entry. */
    fun restore(candidate: RemoteRuleSet, entries: List<String>): RemoteRuleSet {
        val validated = RuleSetParser.parse(entries.joinToString("\n"), candidate.behavior)
        val stored = candidate.copy(
            sha256 = RuleSetParser.sha256(validated),
            entryCount = validated.size,
            updatedAtMillis = System.currentTimeMillis(),
        )
        synchronized(this) {
            directory.mkdirs()
            writeAtomic(payloadFile(stored.id), secretBox.encrypt(validated.joinToString("\n").toByteArray(Charsets.UTF_8), payloadAad(stored.id)))
            saveIndex(list().filterNot { it.id == stored.id } + stored)
        }
        return stored
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) {
        saveIndex(list().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun delete(id: String) {
        saveIndex(list().filterNot { it.id == id })
        payloadFile(id).delete()
    }

    private fun readEntries(id: String): List<String> =
        secretBox.decrypt(payloadFile(id).readText(), payloadAad(id)).toString(Charsets.UTF_8)
            .split('\n')
            .filter(String::isNotEmpty)

    private fun saveIndex(sets: List<RemoteRuleSet>) {
        directory.mkdirs()
        val array = JSONArray()
        sets.forEach { set ->
            array.put(
                JSONObject()
                    .put("id", set.id)
                    .put("name", set.name)
                    .put("url", set.url)
                    .put("behavior", set.behavior.name)
                    .put("action", set.action.name)
                    .put("enabled", set.enabled)
                    .put("sha256", set.sha256.orEmpty())
                    .put("entries", set.entryCount)
                    .put("updated", set.updatedAtMillis ?: 0L),
            )
        }
        writeAtomic(index, secretBox.encrypt(array.toString().toByteArray(Charsets.UTF_8), INDEX_AAD))
    }

    private fun payloadFile(id: String): File {
        require(ID.matches(id)) { "invalid rule-set id" }
        return File(directory, "$id.enc")
    }

    private fun payloadAad(id: String) = "weave.rule-set.$id.v1".toByteArray(Charsets.UTF_8)

    private fun writeAtomic(target: File, content: String) {
        val pending = File(target.parentFile, "${target.name}.pending")
        pending.writeText(content)
        runCatching {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            check(pending.renameTo(target)) { "无法保存规则集" }
        }
    }

    private companion object {
        const val DIRECTORY = "rule-sets"
        const val INDEX_FILE = "index.enc"
        val INDEX_AAD = "weave.rule-sets.index.v1".toByteArray(Charsets.UTF_8)
        val ID = Regex("[0-9a-fA-F-]{36}")
    }
}
