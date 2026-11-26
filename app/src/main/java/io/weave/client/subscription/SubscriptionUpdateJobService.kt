package io.weave.client.subscription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import io.weave.client.MainActivity
import io.weave.client.R
import io.weave.client.WeaveLocales
import io.weave.client.core.vpn.WeaveVpnService
import io.weave.client.core.ipc.CoreClient
import io.weave.client.data.AppRouteStore
import io.weave.client.data.ProfileStorage
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.RouteKind
import io.weave.client.domain.RouteTarget
import io.weave.client.routing.CustomProxyGroup
import io.weave.client.routing.CustomProxyGroupStore
import io.weave.client.routing.NodeRef
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional scheduled refresh of HTTPS subscriptions; off unless the user enables it. */
object SubscriptionUpdateScheduler {
    private const val JOB_ID = 4107

    fun sync(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val preferences = RuntimeSettingsStore(context).networkPreferences()
        val hours = preferences.subscriptionAutoUpdateHours
        if (hours <= 0) {
            scheduler.cancel(JOB_ID)
            return
        }
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, SubscriptionUpdateJobService::class.java))
            .setPeriodic(TimeUnit.HOURS.toMillis(hours.toLong()))
            .setRequiredNetworkType(
                if (preferences.subscriptionAutoUpdateUnmeteredOnly) JobInfo.NETWORK_TYPE_UNMETERED
                else JobInfo.NETWORK_TYPE_ANY,
            )
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        val pending = scheduler.getPendingJob(JOB_ID)
        // Rescheduling restarts the period, so only replace the job when its settings changed.
        @Suppress("DEPRECATION")
        if (pending == null || pending.intervalMillis != job.intervalMillis ||
            pending.networkType != job.networkType
        ) {
            scheduler.schedule(job)
        }
    }
}

class SubscriptionUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null
    private var jobRevision = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WeaveLocales.wrap(newBase))
    }

    override fun onStartJob(params: JobParameters): Boolean {
        running?.cancel()
        val revision = ++jobRevision
        running = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SubscriptionAutoUpdater(this@SubscriptionUpdateJobService).run()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Never log the exception message: it can contain a subscription host.
                Log.w(LOG_TAG, "Scheduled refresh failed: ${error.javaClass.simpleName}")
            }
            if (revision == jobRevision) {
                running = null
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobRevision++
        running?.cancel()
        running = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val LOG_TAG = "WeaveSubscriptionJob"
    }
}

/** One unattended pass over due subscriptions. */
internal class SubscriptionAutoUpdater(private val context: Context) {
    private val settings = RuntimeSettingsStore(context)
    private val repository by lazy { SubscriptionRepository(context) }
    private val notificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
            ?.takeIf { it.areNotificationsEnabled() }
            ?.also { manager ->
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.updates_channel_name),
                        NotificationManager.IMPORTANCE_LOW).apply {
                        description = context.getString(R.string.updates_channel_description)
                    },
                )
            }
    }
    private val openAppIntent by lazy {
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    suspend fun run() {
        val hours = settings.networkPreferences().subscriptionAutoUpdateHours
        if (hours <= 0) return
        val now = System.currentTimeMillis()
        val intervalMillis = TimeUnit.HOURS.toMillis(hours.toLong()) - EARLY_TOLERANCE_MS
        val due = repository.loadMetadata().filter { subscription ->
            subscription.remote && isScheduledRefreshDue(now, subscription.updatedAtMillis, intervalMillis)
        }
        if (due.isEmpty()) return

        // A background-only UI process has no mirrored VPN state until it binds to :vpn.
        // Obtain that state before choosing the subscription DNS route.
        withContext(Dispatchers.Main.immediate) { CoreClient.attach(context) }
        try {
            if (!CoreClient.awaitConnected()) throw SubscriptionImportException("无法确认 VPN 状态，已推迟订阅更新")
            refreshDue(due)
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { CoreClient.detach() }
        }
    }

    private suspend fun refreshDue(due: List<io.weave.client.domain.Subscription>) {

        val pinned = pinnedNodeIds()
        var failed = 0
        var applied = false
        try {
            due.forEach { subscription ->
                currentCoroutineContext().ensureActive()
                val outcome = runCatching {
                    repository.autoRefreshRemote(subscription.id, pinned[subscription.id].orEmpty())
                }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                when (outcome) {
                    is AutoRefreshOutcome.Updated -> {
                        applied = true
                        if (outcome.diff.added > 0 || outcome.diff.removed > 0) {
                            notify(
                                subscription.id.hashCode(),
                                context.getString(R.string.update_applied_title, outcome.name),
                                context.getString(
                                    R.string.update_applied_body,
                                    outcome.diff.added,
                                    outcome.diff.removed,
                                    outcome.diff.unchanged,
                                ),
                            )
                        }
                    }
                    is AutoRefreshOutcome.NeedsReview -> notify(
                        subscription.id.hashCode(),
                        context.getString(R.string.update_review_title, outcome.name),
                        context.getString(R.string.update_review_body),
                    )
                    null -> failed++
                }
            }
            if (failed > 0) {
                notify(
                    FAILURE_NOTIFICATION_ID,
                    context.getString(R.string.update_failed_title),
                    context.resources.getQuantityString(R.plurals.update_failed_body, failed, failed),
                )
            }
        } finally {
            if (applied) WeaveVpnService.reloadIfRunning(context)
        }
    }

    private fun pinnedNodeIds(): Map<String, Set<String>> = ProfileStorage.access(context) {
        scheduledRefreshPins(
            AppRouteStore(context).snapshot().map { it.target }, settings.defaultRouteTarget(),
            CustomProxyGroupStore(context).snapshot(),
        )
    }

    private fun notify(id: Int, title: String, body: String) {
        try {
            val manager = notificationManager ?: return
            manager.notify(
                id,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_weave)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent)
                    .build(),
            )
        } catch (_: SecurityException) {
            // Notification permission can be revoked during a long refresh batch.
        }
    }

    private companion object {
        const val CHANNEL_ID = "subscription_updates"
        const val FAILURE_NOTIFICATION_ID = 4108
        // JobScheduler may run a periodic job slightly early inside its flex window.
        val EARLY_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(30)
    }
}

internal fun isScheduledRefreshDue(now: Long, updatedAt: Long?, interval: Long): Boolean =
    now > 0 && (updatedAt == null || updatedAt > now || updatedAt <= now - interval)

internal fun scheduledRefreshPins(
    appTargets: List<RouteTarget>,
    defaultTarget: RouteTarget?,
    groups: List<CustomProxyGroup>,
): Map<String, Set<String>> {
    val pins = LinkedHashMap<String, MutableSet<String>>()
    fun add(subscriptionId: String, nodeId: String) {
        pins.getOrPut(subscriptionId) { linkedSetOf() }.add(nodeId)
    }
    fun addFixed(target: RouteTarget) {
        if (target.kind == RouteKind.FIXED && target.subscriptionId != null && target.nodeId != null) {
            add(target.subscriptionId, target.nodeId)
        }
    }
    fun addRef(ref: NodeRef) = add(ref.subscriptionId, ref.nodeId)
    appTargets.forEach(::addFixed)
    defaultTarget?.let(::addFixed)
    for (group in groups) {
        group.members.forEach(::addRef)
        group.entry?.let(::addRef)
    }
    return pins
}
