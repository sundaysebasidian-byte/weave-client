package io.weave.client.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Transient status lines from feature ViewModels, shown by the shared snackbar. */
internal object StatusBus {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = flow.asSharedFlow()

    fun post(message: String) {
        flow.tryEmit(message)
    }
}

/** Bumped when local stores are replaced wholesale (backup restore) so holders re-read them. */
internal object LocalDataEvents {
    private val mutableRevision = MutableStateFlow(0)
    val revision = mutableRevision.asStateFlow()

    fun restored() = mutableRevision.update { it + 1 }
}
