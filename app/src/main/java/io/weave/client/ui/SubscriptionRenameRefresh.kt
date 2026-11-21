package io.weave.client.ui

import io.weave.client.domain.EditableSubscription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Returns true only after both the rename and the editor refresh succeeded. */
internal suspend fun saveSubscriptionRenameAndRefreshEditor(
    editorState: MutableStateFlow<SubscriptionEditorState>,
    active: SubscriptionEditorState,
    saveName: suspend () -> String,
    refresh: suspend (String) -> EditableSubscription,
): Boolean {
    var savedName: String? = null
    var finished = active.copy(running = false)
    try {
        val committedName = saveName()
        savedName = committedName
        finished = finished.copy(editor = active.editor?.copy(name = committedName))
        val refreshedEditor = refresh(committedName)
        finished = SubscriptionEditorState(
            subscriptionId = active.subscriptionId,
            editor = refreshedEditor,
            revision = active.revision + 1,
        )
        return true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        finished = if (savedName == null) {
            finished.copy(error = "订阅名称保存失败")
        } else {
            finished.copy(error = "名称已保存，刷新失败，请关闭并重新打开订阅")
        }
        return false
    } finally {
        editorState.update { current -> if (current === active) finished else current }
    }
}
