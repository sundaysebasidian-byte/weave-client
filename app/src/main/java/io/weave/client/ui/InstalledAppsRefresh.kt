package io.weave.client.ui

import io.weave.client.apps.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** The picker and migration dialog share this Main-owned in-memory snapshot. */
internal class InstalledAppsRefresh(
    private val scope: CoroutineScope,
    private val read: suspend () -> List<InstalledApp>,
    private val publish: (List<InstalledApp>) -> Unit,
) {
    private var job: Job? = null
    private var loaded = false
    private var rescanPending = false

    fun ensure(forceRefresh: Boolean) {
        if (job?.isActive == true) {
            if (forceRefresh) rescanPending = true
            return
        }
        if (loaded && !forceRefresh) return

        val request = scope.launch(start = CoroutineStart.LAZY) {
            do {
                rescanPending = false
                try {
                    val apps = read()
                    ensureActive()
                    loaded = true
                    publish(apps)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ensureActive()
                    loaded = false
                }
            } while (rescanPending)
        }
        job = request
        request.start()
    }

    fun release() {
        rescanPending = false
        job?.cancel()
        job = null
        loaded = false
        publish(emptyList())
    }
}
