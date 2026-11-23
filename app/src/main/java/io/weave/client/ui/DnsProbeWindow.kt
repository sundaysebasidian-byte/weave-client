package io.weave.client.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Limit concurrent profile measurements without blocking completed rows behind a slow profile. */
internal suspend fun <P, R> publishDnsProbeResults(
    profiles: List<P>,
    permits: Semaphore,
    probe: suspend (P) -> R,
    publish: (P, R) -> Unit,
): Unit = coroutineScope {
    profiles.map { profile ->
        async {
            val result = permits.withPermit { probe(profile) }
            ensureActive()
            publish(profile, result)
        }
    }.awaitAll()
    Unit
}
