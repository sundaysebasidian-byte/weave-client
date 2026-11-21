package io.weave.client.ui

import io.weave.client.subscription.ExternalImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MaxExternalImportNameLength = 80

/** The offered source and edit are memory-only, including across Activity recreation. */
class ExternalImportPreview internal constructor(val value: ExternalImport, internal val id: Long) {
    private val mutableName = MutableStateFlow(
        when (value) {
            is ExternalImport.Remote -> value.suggestedName ?: value.host
            is ExternalImport.Document -> value.displayName.orEmpty()
            is ExternalImport.Inline -> ""
        }.take(MaxExternalImportNameLength),
    )
    val name = mutableName.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    internal fun setName(name: String) {
        mutableName.value = name.take(MaxExternalImportNameLength)
    }

    internal fun setError(message: String?) {
        mutableError.value = message
    }
}

/** Main-thread state for the current, user-reviewable offer. Results only affect their own offer. */
internal class ExternalImportPreviewSession {
    private val mutableCurrent = MutableStateFlow<ExternalImportPreview?>(null)
    val current = mutableCurrent.asStateFlow()
    private var nextPreviewId = 0L

    fun show(value: ExternalImport) {
        mutableCurrent.value = ExternalImportPreview(value, ++nextPreviewId)
    }

    fun isCurrent(preview: ExternalImportPreview): Boolean = mutableCurrent.value === preview

    fun dismiss(preview: ExternalImportPreview) {
        mutableCurrent.compareAndSet(preview, null)
    }

    fun updateName(preview: ExternalImportPreview, name: String) {
        if (isCurrent(preview)) preview.setName(name)
    }

    fun clearError(preview: ExternalImportPreview) {
        if (isCurrent(preview)) preview.setError(null)
    }

    fun fail(preview: ExternalImportPreview, message: String) {
        if (isCurrent(preview)) preview.setError(message)
    }
}
