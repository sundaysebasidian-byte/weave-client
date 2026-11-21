package io.weave.client.ui

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
