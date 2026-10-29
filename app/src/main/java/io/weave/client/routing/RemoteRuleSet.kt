package io.weave.client.routing

import android.content.Context
import android.system.Os
import android.system.OsConstants
import androidx.compose.runtime.Immutable
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import io.weave.client.subscription.ClashYamlCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import io.weave.client.subscription.SafeSubscriptionFetcher
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
    val revision: String? = null,
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
        val trimmed = body.trimStart('\uFEFF')
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

/** A locked index transaction publishes an immutable encrypted payload generation. */
class RemoteRuleSetStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
    private val fetcher: SafeSubscriptionFetcher = SafeSubscriptionFetcher(
        resolver = io.weave.client.subscription.SubscriptionDNSResolver.forContext(context)::resolve,
    ),
) {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY)
    private val index = File(directory, INDEX_FILE)
    private class Record(val set: RemoteRuleSet, val generation: String?)

    private fun <T> locked(block: () -> T): T = synchronized(STORE_LOCK) {
        try {
            check(directory.isDirectory || directory.mkdirs())
            RandomAccessFile(File(directory, "store.lock"), "rw").use { file ->
                file.channel.lock().use { block() }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuleSetException) {
            throw error
        } catch (_: Exception) {
            // Neither ciphertext, decrypted URLs nor filesystem paths belong in UI diagnostics.
            throw RuleSetException("规则集无法读取或保存；原数据已保留，请重试或从备份恢复")
        }
    }

    fun list(): List<RemoteRuleSet> = locked { readIndex().map { it.set } }

    /** A damaged enabled set must block config generation, never silently change routing. */
    fun active(): List<Pair<RemoteRuleSet, List<String>>> = locked {
        readIndex().filter { it.set.enabled }.map { it.set to readEntries(it) }
    }

    /** One consistent snapshot for encrypted backup export, including disabled sets. */
    fun snapshot(): List<Pair<RemoteRuleSet, List<String>>> = locked {
        readIndex().map { it.set to readEntries(it) }
    }

    suspend fun refresh(candidate: RemoteRuleSet): RemoteRuleSet {
        val normalized = normalize(candidate)
        val original = locked {
            val records = readIndex()
            val original = records.find { it.set.id == candidate.id }
            if (original?.set?.revision != candidate.revision) throw stale()
            if (original == null && records.size >= RuleSetParser.MAX_SETS) throw tooMany()
            original
        }
        val body = fetcher.fetch(normalized.url, adaptMainSubscription = false).body
        val entries = RuleSetParser.parse(body, normalized.behavior)
        val coroutine = currentCoroutineContext()
        return locked {
            coroutine.ensureActive()
            val records = readIndex()
            if (records.find { it.set.id == candidate.id }?.set?.revision != original?.set?.revision) throw stale()
            commit(records, normalized, entries)
        }
    }

    fun entries(id: String): List<String> = locked {
        readEntries(readIndex().find { it.set.id == id } ?: throw RuleSetException("规则集已不存在"))
    }

    fun restore(candidate: RemoteRuleSet, entries: List<String>): RemoteRuleSet {
        val normalized = normalize(candidate)
        val validated = RuleSetParser.parse(entries.joinToString("\n"), normalized.behavior)
        return locked { commit(readIndex(), normalized, validated) }
    }

    fun setEnabled(id: String, enabled: Boolean) = locked {
        val records = readIndex()
        val current = records.find { it.set.id == id } ?: throw RuleSetException("规则集已不存在")
        if (current.set.enabled == enabled) return@locked
        if (enabled) readEntries(current)
        val next = records.map {
            if (it === current) Record(it.set.copy(enabled = enabled, revision = generation()), it.generation) else it
        }
        validateLimits(next)
        saveIndex(next)
        finishCommit()
    }

    fun delete(id: String) = locked {
        val records = readIndex()
        val current = records.find { it.set.id == id } ?: return@locked
        saveIndex(records.filterNot { it === current })
        finishCommit()
        payloadFile(current).delete()
        Unit
    }

    private fun commit(records: List<Record>, candidate: RemoteRuleSet, entries: List<String>): RemoteRuleSet {
        val stored = candidate.copy(sha256 = RuleSetParser.sha256(entries), entryCount = entries.size,
            updatedAtMillis = System.currentTimeMillis(), revision = generation())
        val record = Record(stored, generation())
        val previous = records.find { it.set.id == stored.id }
        // Updating a set must not change first-match rule precedence.
        val next = if (previous == null) records + record else records.map { if (it === previous) record else it }
        validateLimits(next)
        val payload = payloadFile(record)
        writeAtomic(payload, secretBox.encrypt(entries.joinToString("\n").toByteArray(Charsets.UTF_8), payloadAad(stored.id)))
        try {
            // Persist the payload directory entry before publishing a pointer to it.
            syncDirectory()
            saveIndex(next)
        } catch (error: Exception) {
            payload.delete()
            throw error
        }
        // If durability cannot be confirmed after publication, retain both generations.
        finishCommit()
        previous?.let { payloadFile(it).delete() }
        return stored
    }

    private fun readIndex(): List<Record> {
        if (!index.exists()) {
            // A missing index beside encrypted payloads is recovery state, not a new empty store.
            check(requireNotNull(directory.listFiles()).none { it.extension == "enc" })
            return emptyList()
        }
        val array = JSONArray(secretBox.decrypt(index.readText(), INDEX_AAD).toString(Charsets.UTF_8))
        require(array.length() <= RuleSetParser.MAX_SETS)
        val records = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val legacy = !item.has("generation")
            val generation = if (legacy) null else item.getString("generation").also { require(GENERATION.matches(it)) }
            val revision = if (item.has("revision")) item.getString("revision").also { require(GENERATION.matches(it)) }
                else "legacy:" + RuleSetParser.sha256(listOf(item.toString()))
            val set = RemoteRuleSet(
                id = item.getString("id"), name = item.getString("name"), url = item.getString("url"),
                behavior = RuleSetBehavior.valueOf(item.getString("behavior")),
                action = LocalRuleAction.valueOf(item.getString("action")),
                enabled = if (item.has("enabled")) item.get("enabled").let { require(it is Boolean); it } else true,
                sha256 = item.getString("sha256").also { require(HASH.matches(it)) },
                entryCount = item.getInt("entries").also { require(it in 1..RuleSetParser.MAX_ENTRIES_PER_SET) },
                updatedAtMillis = item.getLong("updated").takeIf { it > 0 }, revision = revision,
            )
            normalize(set)
            Record(set, generation)
        }
        validateLimits(records)
        return records
    }

    private fun readEntries(record: Record): List<String> {
        val set = record.set
        val body = secretBox.decrypt(payloadFile(record).readText(), payloadAad(set.id)).toString(Charsets.UTF_8)
        val entries = RuleSetParser.parse(body, set.behavior)
        require(entries.size == set.entryCount && RuleSetParser.sha256(entries) == set.sha256)
        return entries
    }

    private fun normalize(candidate: RemoteRuleSet): RemoteRuleSet {
        if (!ID.matches(candidate.id)) throw RuleSetException("规则集标识无效")
        val name = candidate.name.trim().take(60).ifEmpty { throw RuleSetException("请填写规则集名称") }
        val url = candidate.url.trim()
        if (!url.startsWith("https://", ignoreCase = true)) throw RuleSetException("规则集地址必须使用 HTTPS")
        return candidate.copy(name = name, url = url)
    }

    private fun validateLimits(records: List<Record>) {
        require(records.map { it.set.id.lowercase() }.toSet().size == records.size)
        if (records.size > RuleSetParser.MAX_SETS) throw tooMany()
        if (records.filter { it.set.enabled }.sumOf { it.set.entryCount } > RuleSetParser.MAX_TOTAL_ENTRIES) {
            throw RuleSetException("已启用规则集合计超过 ${RuleSetParser.MAX_TOTAL_ENTRIES} 条上限")
        }
    }

    private fun saveIndex(records: List<Record>) {
        val array = JSONArray()
        records.forEach { record ->
            val set = record.set
            val item = JSONObject().put("id", set.id).put("name", set.name).put("url", set.url)
                .put("behavior", set.behavior.name).put("action", set.action.name).put("enabled", set.enabled)
                .put("sha256", set.sha256).put("entries", set.entryCount).put("updated", set.updatedAtMillis ?: 0L)
            // Legacy entries retain their old pointer until their own successful update.
            if (record.generation != null) item.put("generation", record.generation)
            if (set.revision?.startsWith("legacy:") != true) item.put("revision", set.revision)
            array.put(item)
        }
        writeAtomic(index, secretBox.encrypt(array.toString().toByteArray(Charsets.UTF_8), INDEX_AAD))
    }

    private fun payloadFile(record: Record): File = File(directory,
        record.generation?.let { "${record.set.id}.$it.enc" } ?: "${record.set.id}.enc")

    private fun payloadAad(id: String) = "weave.rule-set.$id.v1".toByteArray(Charsets.UTF_8)

    private fun writeAtomic(target: File, content: String) {
        val pending = File.createTempFile(target.name, ".pending", directory)
        try {
            FileOutputStream(pending).use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            pending.delete()
        }
    }

    private fun syncDirectory() {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    private fun finishCommit() {
        try { syncDirectory() } catch (_: Exception) {
            throw RuleSetException("规则集写入结果尚未确认，旧数据已保留；请重新加载核验")
        }
    }

    private fun stale() = RuleSetException("规则集已被修改或删除，请重新加载后再试")
    private fun tooMany() = RuleSetException("最多添加 ${RuleSetParser.MAX_SETS} 个规则集")
    private fun generation() = UUID.randomUUID().toString().replace("-", "")

    private companion object {
        val STORE_LOCK = Any()
        const val DIRECTORY = "rule-sets"
        const val INDEX_FILE = "index.enc"
        val INDEX_AAD = "weave.rule-sets.index.v1".toByteArray(Charsets.UTF_8)
        val ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val GENERATION = Regex("[0-9a-f]{32}")
        val HASH = Regex("[0-9a-f]{64}")
    }
}
