package io.weave.client.ui

import io.weave.client.domain.EditableSubscription
import io.weave.client.domain.SubscriptionSourceKind
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubscriptionRenameRefreshTest {
    private fun localEditor(name: String) = EditableSubscription(
        id = "first",
        name = name,
        sourceKind = SubscriptionSourceKind.LOCAL_FILE,
        sourceUrl = "",
    )

    @Test fun `saved rename stays saved after refresh failure and releases editor`() = runBlocking {
        val active = SubscriptionEditorState(
            subscriptionId = "first",
            editor = localEditor("Old"),
            running = true,
            revision = 4,
        )
        val editor = MutableStateFlow(active)
        var storedName = "Old"
        val refreshed = saveSubscriptionRenameAndRefreshEditor(
            editorState = editor,
            active = active,
            saveName = { "New".also { storedName = it } },
            refresh = { committedName ->
                assertEquals(storedName, committedName)
                assertEquals("New", committedName)
                throw IOException("fixture refresh failed")
            },
        )

        assertEquals("New", storedName)
        assertFalse(refreshed)
        assertFalse(editor.value.running)
        assertEquals(4L, editor.value.revision)
        assertEquals("New", editor.value.editor?.name)
        assertEquals("名称已保存，刷新失败，请关闭并重新打开订阅", editor.value.error)
    }

    @Test fun `successful refresh permits runtime reload and advances editor revision`() = runBlocking {
        val active = SubscriptionEditorState(subscriptionId = "first", running = true, revision = 4)
        val editor = MutableStateFlow(active)
        val loaded = localEditor("New")

        val refreshed = saveSubscriptionRenameAndRefreshEditor(
            editorState = editor,
            active = active,
            saveName = { "New" },
            refresh = { loaded },
        )

        assertTrue(refreshed)
        assertFalse(editor.value.running)
        assertEquals(5L, editor.value.revision)
        assertSame(loaded, editor.value.editor)
    }

    @Test fun `older refresh failure does not replace reopened editor even for same subscription`() = runBlocking {
        val active = SubscriptionEditorState(subscriptionId = "first", running = true, revision = 4)
        val editor = MutableStateFlow(active)
        val release = CompletableDeferred<Unit>()
        val rename = async(start = CoroutineStart.UNDISPATCHED) {
            saveSubscriptionRenameAndRefreshEditor(
                editorState = editor,
                active = active,
                saveName = { "Saved" },
                refresh = {
                    release.await()
                    throw IOException("fixture refresh failed")
                },
            )
        }
        val reopened = SubscriptionEditorState(subscriptionId = "first", loading = true)
        editor.value = reopened
        release.complete(Unit)

        assertFalse(rename.await())
        assertSame(reopened, editor.value)
    }

    @Test fun `failed save retains original name and never refreshes the editor`() = runBlocking {
        val oldEditor = localEditor("Old")
        val active = SubscriptionEditorState(
            subscriptionId = "first",
            editor = oldEditor,
            running = true,
            revision = 4,
        )
        val editor = MutableStateFlow(active)
        var refreshCalled = false

        val refreshed = saveSubscriptionRenameAndRefreshEditor(
            editorState = editor,
            active = active,
            saveName = { throw IOException("fixture save failed") },
            refresh = {
                refreshCalled = true
                localEditor("Unexpected")
            },
        )

        assertFalse(refreshed)
        assertFalse(refreshCalled)
        assertFalse(editor.value.running)
        assertEquals(4L, editor.value.revision)
        assertSame(oldEditor, editor.value.editor)
        assertEquals("订阅名称保存失败", editor.value.error)
    }

    @Test fun `cancelling a refresh propagates cancellation and retains the committed name`() = runBlocking {
        val active = SubscriptionEditorState(
            subscriptionId = "first",
            editor = localEditor("Old"),
            running = true,
            revision = 4,
        )
        val editor = MutableStateFlow(active)
        var storedName = "Old"
        val refreshStarted = CompletableDeferred<Unit>()
        var propagatedFromHelper = false
        val rename = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                saveSubscriptionRenameAndRefreshEditor(
                    editorState = editor,
                    active = active,
                    saveName = { "New".also { storedName = it } },
                    refresh = { committedName ->
                        assertEquals("New", committedName)
                        refreshStarted.complete(Unit)
                        awaitCancellation()
                    },
                )
            } catch (cancelled: CancellationException) {
                propagatedFromHelper = true
                throw cancelled
            }
        }
        assertTrue(refreshStarted.isCompleted)
        assertTrue(editor.value.running)
        rename.cancel(CancellationException("fixture cancelled refresh"))
        try {
            rename.await()
            fail("expected the refresh cancellation to propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("fixture cancelled refresh", cancelled.message)
        }

        assertTrue(propagatedFromHelper)
        assertEquals("New", storedName)
        assertEquals("New", editor.value.editor?.name)
        assertFalse(editor.value.running)
        assertEquals(4L, editor.value.revision)
        assertNull(editor.value.error)
    }
}
