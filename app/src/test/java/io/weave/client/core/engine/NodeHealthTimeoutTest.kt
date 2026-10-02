package io.weave.client.core.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeHealthTimeoutTest {
    @Test fun `large subscription is bounded to one complete native round`() {
        assertEquals(NodeHealthWorkload(1, 82_000), NodeHealthWorkloadPolicy.forNodeCount(132))
        assertEquals(NodeHealthWorkload(1, 47_000), NodeHealthWorkloadPolicy.forNodeCount(65))
    }

    @Test fun `small subscriptions retain repeated sampling`() {
        assertEquals(NodeHealthWorkload(3, 17_000), NodeHealthWorkloadPolicy.forNodeCount(10))
        assertEquals(NodeHealthWorkload(2, 27_000), NodeHealthWorkloadPolicy.forNodeCount(25))
    }

    @Test fun `a slow health round reports timeout rather than network change`() = runBlocking {
        val error = runCatching {
            withNodeHealthTimeout(10) { delay(100); Unit }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("节点测速超过") == true)
        assertTrue(error !is CancellationException)
    }

    @Test fun `an external cancellation remains cancellation`() = runBlocking {
        val job = async {
            withNodeHealthTimeout(5_000) { awaitCancellation() }
        }
        job.cancel()

        assertTrue(runCatching { job.await() }.exceptionOrNull() is CancellationException)
    }

    @Test fun `a completed round returns its result`() = runBlocking {
        assertEquals(42, withNodeHealthTimeout(1_000) { 42 })
    }
}
