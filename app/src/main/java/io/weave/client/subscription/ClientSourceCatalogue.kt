package io.weave.client.subscription

import java.util.UUID

/** Safe list metadata only: source document IDs, URLs and node credentials stay out of UI state. */
data class ClientSourceEntry(val id: String, val name: String, val available: Boolean = true, val reason: String? = null)
data class ClientSourceCatalogue(val token: String, val sourceName: String, val entries: List<ClientSourceEntry>)
data class MigrationBatchPreview(val token: String, val entries: List<MigrationPreview>)

internal data class ClientSourceRecord(val entry: ClientSourceEntry, val source: String, val expectedNodes: Int? = null, val read: () -> String)
internal data class PreparedSourceMigration(val name: String, val source: String, val prepared: PreparedSubscription)

/** Only one source catalogue; replacing/resetting it makes old selection handles unusable. */
internal class ClientSourceSession(private val clock: () -> Long = System::nanoTime) {
    private var catalogue: ClientSourceCatalogue? = null
    private var records = emptyMap<String, ClientSourceRecord>()
    private var expiresAt = 0L
    @Synchronized fun prepare(sourceName: String, values: List<ClientSourceRecord>): ClientSourceCatalogue {
        clear()
        require(values.size <= 256 && values.map { it.entry.id }.distinct().size == values.size) { "订阅文件格式无效" }
        records = values.associateBy { it.entry.id }
        return ClientSourceCatalogue(UUID.randomUUID().toString(), sourceName, values.map { it.entry }).also {
            catalogue = it; expiresAt = clock() + 300_000_000_000L
        }
    }
    @Synchronized fun select(token: String, ids: Set<String>): List<ClientSourceRecord> {
        require(catalogue?.token == token && clock() < expiresAt) { "预览已失效，请重新获取" }
        require(ids.isNotEmpty() && ids.size <= 20 && ids.all { records[it]?.entry?.available == true }) { "请选择有效的订阅，最多 20 个" }
        return catalogue!!.entries.filter { it.id in ids }.map { records.getValue(it.id) }
    }
    @Synchronized fun clear() { catalogue = null; records = emptyMap(); expiresAt = 0 }
}

/** Whole selected batch is prepared before any write and consumed only once. */
internal class MigrationBatchSession(private val clock: () -> Long = System::nanoTime) {
    private var preview: MigrationBatchPreview? = null
    private var records = emptyList<PreparedSourceMigration>()
    private var expiresAt = 0L
    @Synchronized fun prepare(values: List<PreparedSourceMigration>): MigrationBatchPreview {
        clear()
        require(values.isNotEmpty() && values.size <= 20) { "请选择有效的订阅，最多 20 个" }
        require(values.all { it.prepared.second.nodeCount > 0 }) { "订阅中没有可用节点" }
        require(values.sumOf { it.prepared.first.toByteArray(Charsets.UTF_8).size.toLong() } <= 20L * 1024 * 1024) { "订阅内容超过大小限制" }
        records = values.toList()
        val entries = values.map { value -> MigrationPreview(UUID.randomUUID().toString(), value.name,
            value.prepared.inputFormat, value.prepared.second.protocols, value.prepared.counts,
            value.source.startsWith("https://", true)) }
        return MigrationBatchPreview(UUID.randomUUID().toString(), entries).also {
            preview = it; expiresAt = clock() + 300_000_000_000L
        }
    }
    @Synchronized fun consume(token: String): List<PreparedSourceMigration> {
        require(preview?.token == token && clock() < expiresAt) { "预览已失效，请重新获取" }
        return records.also { clear() }
    }
    @Synchronized fun clear() { preview = null; records = emptyList(); expiresAt = 0 }
}

object ClientSourceCapabilities {
    private val cmfaPackages = setOf("com.github.metacubex.clash", "com.github.metacubex.clash.meta", "com.github.metacubex.clash.alpha")
    fun cmfaAuthority(packageName: String): String? = packageName.takeIf { it in cmfaPackages }?.plus(".files")
    fun isCmfaAuthority(authority: String?): Boolean = cmfaPackages.any { "$it.files" == authority }
    fun isKaring(packageName: String): Boolean = packageName == "com.nebula.karing"
}
