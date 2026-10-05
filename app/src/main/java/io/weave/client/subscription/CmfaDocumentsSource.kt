package io.weave.client.subscription

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.util.UUID

/** The only access is through CMFA's public DocumentsProvider and the user's SAF URI grant. */
internal class CmfaDocumentsSource(private val resolver: ContentResolver, private val reader: LocalSubscriptionReader = LocalSubscriptionReader()) {
    fun catalogue(tree: Uri): List<ClientSourceRecord> {
        require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree) && ClientSourceCapabilities.isCmfaAuthority(tree.authority)) {
            "请选择 CMFA 的配置目录"
        }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = document(tree, rootId)
        require(root.mime == DocumentsContract.Document.MIME_TYPE_DIR) { "请选择 CMFA 的配置目录" }
        return CmfaProfileCatalogue.records(rootId, root, { children(tree, it) }, { read(tree, it) })
    }
    private fun read(tree: Uri, id: String): String = resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))
        ?.use(reader::read) ?: throw SubscriptionImportException("无法读取所选订阅文件")
    private fun document(tree: Uri, id: String): CmfaDocument = query(DocumentsContract.buildDocumentUriUsingTree(tree, id)).single()
    private fun children(tree: Uri, id: String): List<CmfaDocument> = query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id))
    private fun query(uri: Uri): List<CmfaDocument> {
        return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            val result = mutableListOf<CmfaDocument>()
            while (cursor.moveToNext()) {
                require(result.size < 256) { "订阅文件格式无效" }
                result += CmfaDocument(cursor.getString(0), cursor.getString(1), cursor.getString(2))
            }
            result
        } ?: throw SubscriptionImportException("无法读取所选订阅文件")
    }
}

/** Copies only granted provider-cache data, preserving existing override/filter validation. */
internal object CmfaProviderSnapshot {
    fun materialize(input: String, readProvider: (String) -> String): String {
        val plain = ClashSubscriptionDocument.unwrap(input)
        if (!ClashSubscriptionDocument.hasRootKey(plain, "proxy-providers")) return input
        var readBytes = input.toByteArray(Charsets.UTF_8).size.toLong()
        val root = ClashYamlCodec.read(plain).toMutableMap()
        val providers = root["proxy-providers"] as? Map<*, *> ?: throw SubscriptionImportException("订阅节点集合格式无效")
        require(providers.size <= 16) { "订阅引用的节点集合过多" }
        root["proxy-providers"] = providers.mapValues { (_, raw) ->
            val provider = (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }?.toMutableMap()
                ?: throw SubscriptionImportException("订阅节点集合格式无效")
            if (provider["type"]?.toString()?.lowercase() == "file") {
                val path = safeProviderPath(provider["path"] as? String ?: "")
                val childRaw = readProvider(path)
                readBytes += childRaw.toByteArray(Charsets.UTF_8).size
                require(readBytes <= 20L * 1024 * 1024) { "订阅内容超过大小限制" }
                val child = ClashYamlCodec.read(ClashSubscriptionDocument.unwrap(childRaw))
                require(!child.containsKey("proxy-providers")) { "不支持循环或多层节点集合" }
                val nodes = ClashYamlCodec.nodes(child)
                require(nodes.isNotEmpty()) { "订阅中没有可用节点" }
                provider["type"] = "inline"; provider["payload"] = nodes; provider.remove("path")
            }
            provider
        }
        return ClashYamlCodec.write(root)
    }
    fun safeProviderPath(raw: String): String {
        val value = raw.removePrefix("./")
        require(value.startsWith("providers/") && value.length <= 512 && '\\' !in value && ':' !in value && '\u0000' !in value &&
            value.split('/').none { it.isBlank() || it == "." || it == ".." }) { "节点集合文件路径不安全" }
        return value
    }
}
