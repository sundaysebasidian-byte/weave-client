package io.weave.client.subscription

import java.util.UUID

internal data class CmfaDocument(val id: String, val name: String, val mime: String)

/** Metadata paths mirror CMFA's official Picker/FilesProvider; only selected content is opened. */
internal object CmfaProfileCatalogue {
    private const val DIRECTORY = "vnd.android.document/directory"
    fun records(rootId: String, root: CmfaDocument, children: (String) -> List<CmfaDocument>,
        read: (String) -> String): List<ClientSourceRecord> {
        val profiles = if (rootId == "/") children(rootId) else listOf(root)
        require(profiles.size <= 256) { "订阅文件格式无效" }
        return profiles.filter { it.mime == DIRECTORY }.map { profile ->
            val uuid = profile.id.removePrefix("/")
            require(profile.id == "/$uuid" && runCatching { UUID.fromString(uuid).toString() == uuid.lowercase() }.getOrDefault(false)) {
                "请选择 CMFA 的配置目录"
            }
            val configId = profile.id + "/config.yaml"
            val available = runCatching { children(profile.id).any { it.id == configId && it.mime != DIRECTORY } }.getOrDefault(false)
            ClientSourceRecord(ClientSourceEntry(UUID.randomUUID().toString(), profile.name, available,
                if (available) null else "无法读取所选订阅文件"), "local://user-selected-file") {
                require(available) { "无法读取所选订阅文件" }
                CmfaProviderSnapshot.materialize(read(configId)) { relative -> read(profile.id + "/" + relative) }
            }
        }
    }
}
