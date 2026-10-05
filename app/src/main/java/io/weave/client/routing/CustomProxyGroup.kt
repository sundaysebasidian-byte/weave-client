package io.weave.client.routing

import android.content.Context
import androidx.compose.runtime.Immutable
import io.weave.client.security.AndroidKeystoreSecretBox
import io.weave.client.security.SecretBox
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class CustomGroupStrategy(val label: String, val mihomoType: String) {
    LOWEST_LATENCY("最低延迟", "url-test"),
    FAILOVER("故障切换", "fallback"),
    LOAD_BALANCE("负载均衡", "load-balance"),
}

@Immutable
data class NodeRef(val subscriptionId: String, val nodeId: String)

/**
 * A user-defined exit made of nodes from any subscriptions. With [entry] set, every member is
 * dialed through that node first (a two-hop chain: device → entry → member → destination).
 */
@Immutable
data class CustomProxyGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val strategy: CustomGroupStrategy = CustomGroupStrategy.LOWEST_LATENCY,
    val members: List<NodeRef>,
    val entry: NodeRef? = null,
) {
    val subscriptionIds: Set<String>
        get() = members.mapTo(linkedSetOf()) { it.subscriptionId }.apply { entry?.let { add(it.subscriptionId) } }
}

object CustomProxyGroupValidator {
    const val MAX_GROUPS = 32
    const val MAX_MEMBERS = 128
    private val UUID_REGEX = Regex("[0-9a-fA-F-]{36}")

    fun normalize(group: CustomProxyGroup): CustomProxyGroup {
        require(UUID_REGEX.matches(group.id)) { "策略组 ID 无效" }
        val name = group.name.filterNot(Char::isISOControl).trim().take(40)
        require(name.isNotEmpty()) { "请填写策略组名称" }
        val members = group.members.distinct()
        require(members.isNotEmpty()) { "请至少选择一个节点" }
        require(members.size <= MAX_MEMBERS) { "每个策略组最多 $MAX_MEMBERS 个节点" }
        require(group.entry == null || group.entry !in members) { "入口节点不能同时作为出口成员" }
        return group.copy(name = name, members = members)
    }

    fun groupName(id: String) = "group.$id"
}

/** Encrypted local store, replaced atomically so the VPN process can read it at any time. */
class CustomProxyGroupStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    @Synchronized
    fun list(): List<CustomProxyGroup> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(secretBox.decrypt(file.readText(), AAD).toString(Charsets.UTF_8))
            (0 until array.length()).map { index -> decode(array.getJSONObject(index)) }
                .map(CustomProxyGroupValidator::normalize)
                .take(CustomProxyGroupValidator.MAX_GROUPS)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(groups: List<CustomProxyGroup>) {
        require(groups.size <= CustomProxyGroupValidator.MAX_GROUPS) {
            "最多保存 ${CustomProxyGroupValidator.MAX_GROUPS} 个策略组"
        }
        val normalized = groups.map(CustomProxyGroupValidator::normalize)
        require(normalized.map { it.id }.distinct().size == normalized.size) { "策略组 ID 重复" }
        val array = JSONArray().apply { normalized.forEach { put(encode(it)) } }
        val pending = File(file.parentFile, "${file.name}.pending")
        pending.writeText(secretBox.encrypt(array.toString().toByteArray(Charsets.UTF_8), AAD))
        runCatching {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.recoverCatching {
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            require(pending.renameTo(file)) { "无法保存策略组" }
        }
    }

    private fun encode(group: CustomProxyGroup) = JSONObject()
        .put("id", group.id)
        .put("name", group.name)
        .put("strategy", group.strategy.name)
        .put("members", JSONArray().apply { group.members.forEach { put(ref(it)) } })
        .put("entry", group.entry?.let(::ref) ?: JSONObject.NULL)

    private fun decode(item: JSONObject): CustomProxyGroup {
        val members = item.getJSONArray("members")
        return CustomProxyGroup(
            id = item.getString("id"),
            name = item.getString("name"),
            strategy = CustomGroupStrategy.valueOf(item.getString("strategy")),
            members = (0 until members.length()).map { nodeRef(members.getJSONObject(it)) },
            entry = item.optJSONObject("entry")?.let(::nodeRef),
        )
    }

    private fun ref(value: NodeRef) = JSONObject().put("s", value.subscriptionId).put("n", value.nodeId)

    private fun nodeRef(item: JSONObject) = NodeRef(item.getString("s"), item.getString("n"))

    private companion object {
        const val FILE_NAME = "custom-proxy-groups.enc"
        val AAD = "weave.custom-proxy-groups.v1".toByteArray(Charsets.UTF_8)
    }
}
