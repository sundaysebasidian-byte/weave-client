package io.weave.client.ui

import io.weave.client.policy.PolicyPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Request, mutation, and publication callbacks share the ViewModel's Main dispatcher. */
internal class PolicyPackReload(
    private val state: MutableStateFlow<PolicyPackState>,
    private val scope: CoroutineScope,
    private val read: suspend () -> List<PolicyPack>?,
) {
    private var mutationEpoch = 0L
    private var nextRequest = 0L
    private var lastPublished = 0L
    private var deferred = false
    private var readsInEpoch = 0
    private var interruptedRead = false

    fun request() {
        if (state.value.running) {
            deferred = true
            return
        }
        val epoch = mutationEpoch
        val request = ++nextRequest
        readsInEpoch++
        scope.launch {
            val packs = try {
                read()
            } finally {
                if (epoch == mutationEpoch) readsInEpoch--
            }
            if (packs == null || epoch != mutationEpoch || request < lastPublished || state.value.running) return@launch
            lastPublished = request
            state.update { it.copy(packs = packs) }
        }
    }

    fun mutationStarted() {
        interruptedRead = readsInEpoch > 0
        readsInEpoch = 0
        mutationEpoch++
    }

    fun mutationFinished(succeeded: Boolean) {
        // Successful mutations publish their own fresh list; failures may have retired the only read.
        val needsRead = deferred || (!succeeded && interruptedRead)
        deferred = false
        interruptedRead = false
        if (needsRead) request()
    }
}
