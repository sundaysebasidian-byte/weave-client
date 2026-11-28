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
 * dialed through an available entry and every intermediate hop in order.
 */
@Immutable
data class CustomProxyGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val strategy: CustomGroupStrategy = CustomGroupStrategy.LOWEST_LATENCY,
    val members: List<NodeRef>,
    val entry: NodeRef? = null,
    val via: List<NodeRef> = emptyList(),
    val entryFallbacks: List<NodeRef> = emptyList(),
) {
    val entryCandidates: List<NodeRef> get() = listOfNotNull(entry) + entryFallbacks
    val forwardingHops: List<NodeRef> get() = entryCandidates + via
    val nodeRefs: List<NodeRef> get() = forwardingHops + members

    val subscriptionIds: Set<String>
        get() = nodeRefs.mapTo(linkedSetOf(), NodeRef::subscriptionId)
}

object CustomProxyGroupValidator {
    const val MAX_GROUPS = 32
    const val MAX_MEMBERS = 128
    const val MAX_VIA = 4
    const val MAX_ENTRY_FALLBACKS = 3
    private val UUID_REGEX = Regex("[0-9a-fA-F-]{36}")

    fun normalize(group: CustomProxyGroup): CustomProxyGroup {
        require(UUID_REGEX.matches(group.id)) { "策略组 ID 无效" }
        val clipped = group.name.filterNot(Char::isISOControl).trim().take(40)
        // The editor and imported backups can both land exactly on a UTF-16 boundary.
        val name = if (clipped.lastOrNull()?.isHighSurrogate() == true) clipped.dropLast(1) else clipped
        require(name.isNotEmpty()) { "请填写策略组名称" }
        val members = group.members.distinct()
        require(members.isNotEmpty()) { "请至少选择一个节点" }
        require(members.size <= MAX_MEMBERS) { "每个策略组最多 $MAX_MEMBERS 个节点" }
        require(group.entry == null || group.entry !in members) { "入口节点不能同时作为出口成员" }
        require(group.entry != null || group.via.isEmpty() && group.entryFallbacks.isEmpty()) {
            "选择入口节点"
        }
        require(group.via.size <= MAX_VIA && group.entryFallbacks.size <= MAX_ENTRY_FALLBACKS) {
            "策略组引用的节点已不存在"
        }
        require(group.forwardingHops.distinct().size == group.forwardingHops.size &&
            group.forwardingHops.none { it in members }) { "链路中不能重复使用节点" }
        return group.copy(name = name, members = members)
    }

    fun groupName(id: String) = "group.$id"
}

/** Encrypted local store, replaced atomically so the VPN process can read it at any time. */
class CustomProxyGroupStore(
    context: Context,
    private val secretBox: SecretBox = AndroidKeystoreSecretBox(),
) {
    private val appContext = context.applicationContext
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    internal fun snapshot(): List<CustomProxyGroup> = io.weave.client.data.ProfileStorage.access(appContext) {
        if (!file.exists()) return@access emptyList()
        val array = JSONArray(secretBox.decrypt(file.readText(), AAD).toString(Charsets.UTF_8))
        require(array.length() <= CustomProxyGroupValidator.MAX_GROUPS)
        (0 until array.length()).map { CustomProxyGroupValidator.normalize(readCustomProxyGroup(array.getJSONObject(it))) }.also {
            require(it.map { group -> group.id }.toSet().size == it.size)
        }
    }

    fun list(): List<CustomProxyGroup> = io.weave.client.data.ProfileStorage.access(appContext) {
        runCatching { snapshot() }.getOrDefault(emptyList())
    }

    fun save(groups: List<CustomProxyGroup>): Unit = io.weave.client.data.ProfileStorage.access(appContext) {
        require(groups.size <= CustomProxyGroupValidator.MAX_GROUPS) {
            "最多保存 ${CustomProxyGroupValidator.MAX_GROUPS} 个策略组"
        }
        val normalized = groups.map(CustomProxyGroupValidator::normalize)
        require(normalized.map { it.id }.distinct().size == normalized.size) { "策略组 ID 重复" }
        val array = JSONArray().apply { normalized.forEach { put(it.toJson()) } }
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

    private companion object {
        const val FILE_NAME = "custom-proxy-groups.enc"
        val AAD = "weave.custom-proxy-groups.v1".toByteArray(Charsets.UTF_8)
    }
}

internal fun CustomProxyGroup.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("strategy", strategy.name)
    .put("members", refsJson(members))
    .put("entry", entry?.let(::refJson) ?: JSONObject.NULL)
    .apply {
        if (via.isNotEmpty()) put("via", refsJson(via))
        if (entryFallbacks.isNotEmpty()) put("entryFallbacks", refsJson(entryFallbacks))
    }

internal fun readCustomProxyGroup(item: JSONObject): CustomProxyGroup {
    fun refs(key: String, cap: Int): List<NodeRef> {
        if (key != "members" && !item.has(key)) return emptyList()
        val array = item.getJSONArray(key)
        require(array.length() <= cap) { "链路节点数量超出限制" }
        return List(array.length()) { readNodeRef(array.getJSONObject(it)) }
    }
    return CustomProxyGroup(
        id = item.getString("id"),
        name = item.getString("name"),
        strategy = CustomGroupStrategy.valueOf(item.getString("strategy")),
        members = refs("members", CustomProxyGroupValidator.MAX_MEMBERS),
        entry = if (item.isNull("entry")) null else readNodeRef(item.getJSONObject("entry")),
        via = refs("via", CustomProxyGroupValidator.MAX_VIA),
        entryFallbacks = refs("entryFallbacks", CustomProxyGroupValidator.MAX_ENTRY_FALLBACKS),
    )
}

private fun refsJson(refs: List<NodeRef>): JSONArray = JSONArray().apply { refs.forEach { put(refJson(it)) } }
private fun refJson(ref: NodeRef): JSONObject = JSONObject().put("s", ref.subscriptionId).put("n", ref.nodeId)
private fun readNodeRef(item: JSONObject): NodeRef = NodeRef(item.getString("s"), item.getString("n"))
