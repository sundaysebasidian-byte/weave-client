package io.weave.client.policy

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.Immutable
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

enum class PolicyPackIntegrity {
    VERIFIED_HASH,
    VERIFIED_SIGNATURE,
    UNSIGNED_REVIEW,
    INVALID,
}

enum class PolicyRuleType {
    DOMAIN,
    DOMAIN_SUFFIX,
    DOMAIN_KEYWORD,
    IP_CIDR,
    IP_CIDR6,
    PROCESS_NAME,
}

enum class PolicyRuleAction {
    DEFAULT,
    DIRECT,
    REJECT,
}

@Immutable
data class PolicyRule(
    val type: PolicyRuleType,
    val value: String,
    val action: PolicyRuleAction,
)

@Immutable
data class PolicyPack(
    val id: String,
    val name: String,
    val version: Int,
    val description: String,
    val source: String,
    val rules: List<PolicyRule>,
    val sha256: String,
    val integrity: PolicyPackIntegrity,
    val signatureAlgorithm: String? = null,
    val signaturePublicKey: String? = null,
    val signatureValue: String? = null,
    val active: Boolean = false,
) {
    val ruleCount: Int get() = rules.size
}

class PolicyPackException(message: String) : IllegalArgumentException(message)

/**
 * Offline policy pack parser. The hash covers a deterministic canonical payload without the
 * integrity/signature fields. Optional Ed25519 signatures are verified when a pack supplies a
 * valid public key; an unsigned pack is importable but remains visibly review-only.
 */
object PolicyPackCodec {
    private const val FORMAT = "weave-policy/v1"
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_RULES = 4_096
    private const val MAX_NAME = 120
    private const val MAX_DESCRIPTION = 1_024

    fun decode(raw: String, source: String = "local://policy-file"): PolicyPack {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_BYTES) { "策略包必须为 1–512 KiB" }
        val json = runCatching { JSONObject(raw) }
            .getOrElse { throw PolicyPackException("策略包不是有效 JSON") }
        if (json.optString("format") != FORMAT) {
            throw PolicyPackException("不支持的策略包格式")
        }
        val id = json.optString("id").trim()
        require(ID.matches(id)) { "策略包 id 无效" }
        val name = json.optString("name").trim()
        require(name.isNotBlank() && name.length <= MAX_NAME) { "策略包名称无效" }
        val version = json.optInt("version", 0)
        require(version in 1..Int.MAX_VALUE) { "策略包版本无效" }
        val description = json.optString("description", "").trim()
        require(description.length <= MAX_DESCRIPTION) { "策略包说明过长" }
        val rulesJson = json.optJSONArray("rules")
            ?: throw PolicyPackException("策略包缺少 rules")
        require(rulesJson.length() in 1..MAX_RULES) { "策略包规则数量必须为 1–$MAX_RULES" }
        val rules = (0 until rulesJson.length()).map { index ->
            val rule = rulesJson.optJSONObject(index)
                ?: throw PolicyPackException("第 ${index + 1} 条规则不是对象")
            val type = runCatching {
                PolicyRuleType.valueOf(rule.optString("type").trim().uppercase())
            }.getOrElse { throw PolicyPackException("第 ${index + 1} 条规则类型不支持") }
            val action = runCatching {
                PolicyRuleAction.valueOf(rule.optString("action").trim().uppercase())
            }.getOrElse { throw PolicyPackException("第 ${index + 1} 条规则动作不支持") }
            val value = rule.optString("value").trim()
            validateValue(type, value, index)
            PolicyRule(type, value, action)
        }
        val canonical = canonicalPayload(id, name, version, description, rules)
        val expectedHash = sha256(canonical.toByteArray(Charsets.UTF_8))
        val declaredHash = json.optString("sha256").trim().lowercase()
        require(HEX_64.matches(declaredHash)) { "策略包缺少有效 SHA-256" }
        if (declaredHash != expectedHash) {
            throw PolicyPackException("策略包 SHA-256 校验失败")
        }
        val signature = json.optJSONObject("signature")
        val signatureAlgorithm = signature?.optString("algorithm")?.trim()?.takeIf(String::isNotBlank)
        val integrity = when {
            signature == null -> PolicyPackIntegrity.UNSIGNED_REVIEW
            verifySignature(signature, canonical.toByteArray(Charsets.UTF_8)) ->
                PolicyPackIntegrity.VERIFIED_SIGNATURE
            else -> throw PolicyPackException("策略包签名校验失败")
        }
        return PolicyPack(
            id = id,
            name = name,
            version = version,
            description = description,
            source = source.take(256),
            rules = rules,
            sha256 = expectedHash,
            integrity = integrity,
            signatureAlgorithm = signatureAlgorithm,
            signaturePublicKey = signature?.optString("publicKey")?.takeIf(String::isNotBlank),
            signatureValue = signature?.optString("value")?.takeIf(String::isNotBlank),
        )
    }

    fun encode(pack: PolicyPack): String {
        val canonical = canonicalPayload(
            pack.id,
            pack.name,
            pack.version,
            pack.description,
            pack.rules,
        )
        val json = JSONObject(canonical)
        json.put("sha256", sha256(canonical.toByteArray(Charsets.UTF_8)))
        if (
            pack.signatureAlgorithm != null &&
            pack.signaturePublicKey != null &&
            pack.signatureValue != null
        ) {
            json.put(
                "signature",
                JSONObject()
                    .put("algorithm", pack.signatureAlgorithm)
                    .put("publicKey", pack.signaturePublicKey)
                    .put("value", pack.signatureValue),
            )
        }
        return json.toString()
    }

    fun canonicalPayload(
        id: String,
        name: String,
        version: Int,
        description: String,
        rules: List<PolicyRule>,
    ): String {
        val json = JSONObject()
            .put("description", description)
            .put("format", FORMAT)
            .put("id", id)
            .put("name", name)
            .put("rules", JSONArray().also { array ->
                rules.forEach { rule ->
                    array.put(
                        JSONObject()
                            .put("action", rule.action.name.lowercase())
                            .put("type", rule.type.name.lowercase())
                            .put("value", rule.value),
                    )
                }
            })
            .put("version", version)
        return json.toString()
    }

    private fun verifySignature(signature: JSONObject, payload: ByteArray): Boolean = runCatching {
        require(signature.optString("algorithm") == "Ed25519")
        val publicKey = Base64.getDecoder().decode(signature.optString("publicKey"))
        val signatureBytes = Base64.getDecoder().decode(signature.optString("value"))
        val key: PublicKey = java.security.KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(publicKey))
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(payload)
            verify(signatureBytes)
        }
    }.getOrDefault(false)

    private fun validateValue(type: PolicyRuleType, value: String, index: Int) {
        require(value.isNotBlank() && value.length <= 253) { "第 ${index + 1} 条规则值无效" }
        require(!value.contains('\n') && !value.contains('\r') && !value.contains(',')) {
            "第 ${index + 1} 条规则值包含非法分隔符"
        }
        when (type) {
            PolicyRuleType.DOMAIN,
            PolicyRuleType.DOMAIN_SUFFIX,
            PolicyRuleType.DOMAIN_KEYWORD,
            -> require(DOMAIN.matches(value.removePrefix("."))) { "第 ${index + 1} 条域名无效" }
            PolicyRuleType.IP_CIDR,
            PolicyRuleType.IP_CIDR6,
            -> require(CIDR.matches(value)) { "第 ${index + 1} 条 CIDR 无效" }
            PolicyRuleType.PROCESS_NAME -> require(PROCESS.matches(value)) { "第 ${index + 1} 条进程名无效" }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private val ID = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")
    private val HEX_64 = Regex("[0-9a-f]{64}")
    private val DOMAIN = Regex("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
    private val CIDR = Regex("(?i)[0-9a-f:.]+/[0-9]{1,3}")
    private val PROCESS = Regex("[A-Za-z0-9_.$-]{1,160}")
}

class PolicyPackStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val directory = File(appContext.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    @Synchronized
    fun list(): List<PolicyPack> = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()
        .mapNotNull { id ->
            runCatching { read(id) }.getOrNull()
        }
        .sortedWith(compareBy<PolicyPack> { !it.active }.thenBy { it.name.lowercase() })

    @Synchronized
    fun save(pack: PolicyPack): PolicyPack {
        val activeIds = activeIds().toMutableSet()
        val file = File(directory, "${pack.id}.enc")
        val encrypted = secretBox.encrypt(
            PolicyPackCodec.encode(pack).toByteArray(Charsets.UTF_8),
            pack.id.toByteArray(Charsets.UTF_8),
        )
        val pending = File(directory, "${pack.id}.pending")
        pending.writeText(encrypted, Charsets.UTF_8)
        runCatching {
            Files.move(
                pending.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                pending.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            // Some Android filesystems do not expose ATOMIC_MOVE. A replace operation is still
            // safe here because the encrypted candidate has already been completely written.
            require(pending.renameTo(file)) { "无法保存策略包" }
        }
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids += pack.id
        preferences.edit {
            putStringSet(KEY_IDS, ids)
            putBoolean(key(pack.id, ACTIVE), pack.active)
        }
        if (pack.active) activeIds += pack.id else activeIds -= pack.id
        return pack.copy(active = pack.id in activeIds)
    }

    @Synchronized
    fun read(id: String): PolicyPack {
        require(ID.matches(id)) { "策略包 id 无效" }
        val encrypted = File(directory, "${id}.enc").takeIf(File::isFile)
            ?: throw PolicyPackException("策略包不存在")
        val raw = secretBox.decrypt(
            encrypted.readText(Charsets.UTF_8),
            id.toByteArray(Charsets.UTF_8),
        ).toString(Charsets.UTF_8)
        return PolicyPackCodec.decode(raw, "local://stored-policy").copy(
            active = preferences.getBoolean(key(id, ACTIVE), false),
        )
    }

    @Synchronized
    fun setActive(id: String, active: Boolean): PolicyPack {
        val pack = read(id)
        preferences.edit { putBoolean(key(id, ACTIVE), active) }
        return pack.copy(active = active)
    }

    @Synchronized
    fun delete(id: String) {
        read(id)
        File(directory, "${id}.enc").delete()
        val ids = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        ids -= id
        preferences.edit {
            putStringSet(KEY_IDS, ids)
            remove(key(id, ACTIVE))
        }
    }

    fun active(): List<PolicyPack> = list().filter(PolicyPack::active)

    private fun activeIds(): Set<String> = preferences.getStringSet(KEY_IDS, emptySet()).orEmpty()
        .filterTo(mutableSetOf()) { preferences.getBoolean(key(it, ACTIVE), false) }

    private fun key(id: String, field: String) = "policy.$id.$field"

    private companion object {
        const val PREFERENCES_NAME = "offline_policy_packs_v1"
        const val DIRECTORY = "offline-policies"
        const val KEY_IDS = "policy.ids"
        const val ACTIVE = "active"
        val ID = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}")
    }
}

object PolicyPackCompiler {
    fun compile(packs: List<PolicyPack>): List<String> = packs
        .filter { it.active }
        .sortedBy { it.id }
        .flatMap { pack ->
            pack.rules.map { rule ->
                "${rule.type.mihomoName()},${escape(rule.value)},${rule.action.mihomoName()}"
            }
        }
        .distinct()

    private fun PolicyRuleType.mihomoName(): String = when (this) {
        PolicyRuleType.DOMAIN -> "DOMAIN"
        PolicyRuleType.DOMAIN_SUFFIX -> "DOMAIN-SUFFIX"
        PolicyRuleType.DOMAIN_KEYWORD -> "DOMAIN-KEYWORD"
        PolicyRuleType.IP_CIDR -> "IP-CIDR"
        PolicyRuleType.IP_CIDR6 -> "IP-CIDR6"
        PolicyRuleType.PROCESS_NAME -> "PROCESS-NAME"
    }

    private fun PolicyRuleAction.mihomoName(): String = when (this) {
        PolicyRuleAction.DEFAULT -> "DEFAULT"
        PolicyRuleAction.DIRECT -> "DIRECT"
        PolicyRuleAction.REJECT -> "REJECT"
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace(",", "\\,")
}
