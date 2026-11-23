package io.weave.client.ui

import android.content.Intent
import io.weave.client.MainActivity
import io.weave.client.isReplayedExitChoice
import io.weave.client.subscription.ExternalImport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRequestClaimTest {
    @Test
    fun onlyReplayedChooserIntentIsIgnoredBeforeStartupQueueing() {
        val fresh = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val restored = fresh or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        assertFalse(isReplayedExitChoice(MainActivity.ACTION_CHOOSE_EXIT, fresh))
        assertTrue(isReplayedExitChoice(MainActivity.ACTION_CHOOSE_EXIT, restored))
        for (action in listOf(Intent.ACTION_MAIN, MainActivity.ACTION_CONNECT, MainActivity.ACTION_IMPORT, null)) {
            assertFalse(isReplayedExitChoice(action, restored))
        }
    }

    @Test
    fun staleConnectDoesNotConsumeOrRunAheadOfNewerImportShortcut() = runBlocking {
        val pending = MutableStateFlow<ExternalRequest?>(ExternalRequest.Connect)
        val releaseReadiness = CompletableDeferred<Unit>()
        val staleClaim = async(start = CoroutineStart.UNDISPATCHED) {
            claimExternalRequest(pending, ExternalRequest.Connect) { releaseReadiness.await() }
        }
        assertFalse(staleClaim.isCompleted)

        pending.value = ExternalRequest.OpenImport
        releaseReadiness.complete(Unit)

        assertFalse(staleClaim.await())
        assertSame(ExternalRequest.OpenImport, pending.value)
        assertTrue(claimExternalRequest(pending, ExternalRequest.OpenImport) {
            error("The import shortcut does not wait for the core")
        })
        assertNull(pending.value)
    }

    @Test
    fun currentConnectIsClaimedOnceOnlyAfterReadiness() = runBlocking {
        val pending = MutableStateFlow<ExternalRequest?>(ExternalRequest.Connect)
        val releaseReadiness = CompletableDeferred<Unit>()
        val claim = async(start = CoroutineStart.UNDISPATCHED) {
            claimExternalRequest(pending, ExternalRequest.Connect) { releaseReadiness.await() }
        }
        assertFalse(claim.isCompleted)
        assertSame(ExternalRequest.Connect, pending.value)

        releaseReadiness.complete(Unit)
        assertTrue(claim.await())
        assertNull(pending.value)
        assertFalse(claimExternalRequest(pending, ExternalRequest.Connect) {})
    }

    @Test
    fun canceledReadinessWaitLeavesRequestAvailable() = runBlocking {
        val pending = MutableStateFlow<ExternalRequest?>(ExternalRequest.Connect)
        val releaseReadiness = CompletableDeferred<Unit>()
        val claim = async(start = CoroutineStart.UNDISPATCHED) {
            claimExternalRequest(pending, ExternalRequest.Connect) { releaseReadiness.await() }
        }
        claim.cancelAndJoin()
        assertSame(ExternalRequest.Connect, pending.value)
    }

    @Test
    fun canceledOwnerCannotClaimWhenReadinessReturnsWithoutSuspending() = runBlocking {
        val pending = MutableStateFlow<ExternalRequest?>(ExternalRequest.Connect)
        val claim = async(start = CoroutineStart.UNDISPATCHED) {
            claimExternalRequest(pending, ExternalRequest.Connect) {
                currentCoroutineContext().cancel()
            }
        }
        claim.join()
        assertTrue(claim.isCancelled)
        assertSame(ExternalRequest.Connect, pending.value)
    }

    @Test
    fun chooseExitWaitsBeforeClaimAndCannotConsumeASuccessor() = runBlocking {
        val pending = MutableStateFlow<ExternalRequest?>(ExternalRequest.ChooseExit)
        val releaseReadiness = CompletableDeferred<Unit>()
        val stale = async(start = CoroutineStart.UNDISPATCHED) {
            claimExternalRequest(pending, ExternalRequest.ChooseExit) { releaseReadiness.await() }
        }
        assertFalse(stale.isCompleted)
        assertSame(ExternalRequest.ChooseExit, pending.value)

        pending.value = ExternalRequest.OpenImport
        releaseReadiness.complete(Unit)

        assertFalse(stale.await())
        assertSame(ExternalRequest.OpenImport, pending.value)
    }

    @Test
    fun currentChooseExitClaimsAfterReadinessOnlyOnce() = runBlocking {
        val pending = MutableStateFlow<ExternalRequest?>(ExternalRequest.ChooseExit)
        val ready = CompletableDeferred<Unit>()
        val claim = async(start = CoroutineStart.UNDISPATCHED) {
            claimExternalRequest(pending, ExternalRequest.ChooseExit) { ready.await() }
        }
        assertSame(ExternalRequest.ChooseExit, pending.value)
        ready.complete(Unit)
        assertTrue(claim.await())
        assertNull(pending.value)
        assertFalse(claimExternalRequest(pending, ExternalRequest.ChooseExit) {})
    }

    @Test
    fun equivalentShareClaimsRespectExistingStateFlowEquality() = runBlocking {
        val earlier = ExternalRequest.Import(ExternalImport.Inline("fixture subscription"))
        val pending = MutableStateFlow<ExternalRequest?>(
            ExternalRequest.Import(ExternalImport.Inline("fixture subscription")),
        )
        assertTrue(claimExternalRequest(pending, earlier) {
            error("Shared import does not wait for the core")
        })
        assertNull(pending.value)
    }
}
