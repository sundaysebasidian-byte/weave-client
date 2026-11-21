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

    fun request() {
        if (state.value.running) {
            deferred = true
            return
        }
        val epoch = mutationEpoch
        val request = ++nextRequest
        scope.launch {
            val packs = read() ?: return@launch
            if (epoch != mutationEpoch || request < lastPublished || state.value.running) return@launch
            lastPublished = request
            state.update { it.copy(packs = packs) }
        }
    }

    fun mutationStarted() {
        mutationEpoch++
    }

    fun mutationFinished() {
        if (!deferred) return
        deferred = false
        request()
    }
}
