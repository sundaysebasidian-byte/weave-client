package io.weave.client.ui

import io.weave.client.domain.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A snackbar may finish before the shell notices a newer status from the ViewModel. */
internal fun MutableStateFlow<DashboardState>.clearDisplayedStatus(displayed: String) {
    update { current ->
        if (current.statusMessage == displayed) current.copy(statusMessage = null) else current
    }
}
