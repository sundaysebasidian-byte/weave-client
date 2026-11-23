package io.weave.client.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Register once per owner. Lifecycle catches up an already-resumed owner on attachment. */
internal fun Lifecycle.addForegroundResumeObserver(onResume: () -> Unit): LifecycleEventObserver =
    LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) onResume()
    }.also { addObserver(it) }
