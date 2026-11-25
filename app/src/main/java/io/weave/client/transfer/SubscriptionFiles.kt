package io.weave.client.transfer

import android.content.Context
import android.net.Uri
import io.weave.client.data.ProfileStorage
import io.weave.client.subscription.PreparedTransfer
import io.weave.client.subscription.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** No selected URI, passphrase, plaintext payload, or pending plan is persisted in UI state. */
internal class SubscriptionFiles(private val context: Context, private val repository: SubscriptionRepository) {
    internal class Preview internal constructor(internal val epoch: String, internal val transfer: PreparedTransfer) {
        val subscriptions get() = transfer.entries.size
        val nodes get() = transfer.entries.sumOf { it.parsed.nodeCount }
    }

    suspend fun export(target: Uri, selection: Set<String>, epoch: String, password: CharArray) = try {
        withContext(Dispatchers.IO) {
            require(target.scheme == "content")
            val items = ProfileStorage.withCurrentEpoch(context, epoch) { repository.exportFileSelection(selection) }
            val bytes = SubscriptionFileCodec.encrypt(items, password)
            currentCoroutineContext().ensureActive()
            requireNotNull(context.contentResolver.openOutputStream(target, "wt")).use { it.write(bytes) }
        }
    } finally { password.fill('\u0000') }

    suspend fun open(source: Uri, password: CharArray): Preview = try {
        withContext(Dispatchers.IO) {
            require(source.scheme == "content")
            val epoch = ProfileStorage.restoreEpoch(context)
            val bytes = requireNotNull(context.contentResolver.openInputStream(source)).use {
                BackupFileReader.read(it, SubscriptionFileCodec.MAX_FILE_BYTES)
            }
            val items = SubscriptionFileCodec.decrypt(bytes, password)
            currentCoroutineContext().ensureActive()
            Preview(epoch, repository.prepareSubscriptionFile(items))
        }
    } finally { password.fill('\u0000') }

    suspend fun add(preview: Preview) = withContext(Dispatchers.IO) {
        val owner = currentCoroutineContext()
        ProfileStorage.withCurrentEpoch(context, preview.epoch) {
            repository.commitTransfer(preview.transfer, owner)
            Unit
        }
    }
}
