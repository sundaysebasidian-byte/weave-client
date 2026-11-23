package io.weave.client.ui

import io.weave.client.domain.WeaveLanguage
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupRestoreUiTest {
    @Test fun fullyRefreshedRestoreCompletesOnceAfterTheRefresh() = runBlocking {
        val steps = mutableListOf<String>()
        val outcomes = mutableListOf<BackupState>()
        finishCommittedBackupUi(refresh = { steps += "reread" }, stillCurrent = { true }) { state ->
            steps += "complete"
            outcomes += state
        }
        assertEquals(listOf("reread", "complete"), steps)
        val state = outcomes.single()
        assertFalse(state.running)
        assertNull(state.error)
        assertNotNull(state.message)
    }

    @Test fun failedRereadOfACommittedRestoreSettlesWithTheKnownSuccessAndTheWarning() = runBlocking {
        val outcomes = mutableListOf<BackupState>()
        finishCommittedBackupUi(
            refresh = { throw IOException("synthetic reread failure") },
            stillCurrent = { true },
            complete = { outcomes += it },
        )
        val state = outcomes.single()
        assertFalse(state.running)
        assertNull(state.preview)
        assertEquals("已从备份恢复；设备相关设置（系统代理、局域网共享）需重新开启", state.message)
        assertEquals(BACKUP_RESTORE_REFRESH_WARNING, state.error)
    }

    @Test fun aCanceledRefreshDoesNotCompleteTheOldOwner() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var completions = 0
        val job = launch {
            finishCommittedBackupUi(
                refresh = { entered.complete(Unit); awaitCancellation() },
                stillCurrent = { true },
                complete = { completions++ },
            )
        }
        entered.await()
        job.cancelAndJoin()
        assertEquals(0, completions)
    }

    @Test fun anOwnerRetiredDuringTheRereadDoesNotComplete() = runBlocking {
        var current = true
        var completions = 0
        finishCommittedBackupUi(
            refresh = { current = false },
            stillCurrent = { current },
            complete = { completions++ },
        )
        assertEquals(0, completions)
    }

    @Test fun partialRefreshWarningHasAnActionInAllSixLanguages() {
        val localized = WeaveLanguage.entries.associateWith { language ->
            localizeWeaveText(BACKUP_RESTORE_REFRESH_WARNING, language)
        }
        assertEquals(6, localized.values.toSet().size)
        assertEquals(BACKUP_RESTORE_REFRESH_WARNING, localized[WeaveLanguage.SIMPLIFIED_CHINESE])
        assertEquals(
            "Some screens may not have updated. Close the app completely, then reopen it.",
            localized[WeaveLanguage.ENGLISH],
        )
    }
}
