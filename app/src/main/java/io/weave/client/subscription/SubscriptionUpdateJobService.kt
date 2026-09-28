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
import io.weave.client.data.AppRouteStore
import io.weave.client.data.RuntimeSettingsStore
import io.weave.client.domain.RouteKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
        if (pending == null || pending.intervalMillis != job.intervalMillis ||
            pending.networkType != job.networkType
        ) {
            scheduler.schedule(job)
        }
    }
}

class SubscriptionUpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WeaveLocales.wrap(newBase))
    }

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try {
                SubscriptionAutoUpdater(this@SubscriptionUpdateJobService).run()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Never log the exception message: it can contain a subscription host.
                Log.w(LOG_TAG, "Scheduled refresh failed: ${error.javaClass.simpleName}")
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
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
    private val repository = SubscriptionRepository(context)

    suspend fun run() {
        val hours = settings.networkPreferences().subscriptionAutoUpdateHours
        if (hours <= 0) return
        val now = System.currentTimeMillis()
        val due = repository.loadMetadata().filter { subscription ->
            subscription.remote &&
                (subscription.updatedAtMillis == null ||
                    now - subscription.updatedAtMillis >= TimeUnit.HOURS.toMillis(hours.toLong()) - EARLY_TOLERANCE_MS)
        }
        if (due.isEmpty()) return

        val pinned = pinnedNodeIds()
        var failed = 0
        var applied = false
        due.forEach { subscription ->
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
                context.getString(R.string.update_failed_body, failed),
            )
        }
        if (applied) WeaveVpnService.reloadIfRunning(context)
    }

    /** Node IDs referenced by fixed app routes or the fixed default exit, per subscription. */
    private fun pinnedNodeIds(): Map<String, Set<String>> {
        val targets = AppRouteStore(context).load().map { it.target } + listOfNotNull(settings.defaultRouteTarget())
        return targets
            .filter { it.kind == RouteKind.FIXED && it.subscriptionId != null && it.nodeId != null }
            .groupBy({ it.subscriptionId!! }, { it.nodeId!! })
            .mapValues { it.value.toSet() }
    }

    private fun notify(id: Int, title: String, body: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.updates_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.updates_channel_description) },
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_weave)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "subscription_updates"
        const val FAILURE_NOTIFICATION_ID = 4108
        // JobScheduler may run a periodic job slightly early inside its flex window.
        val EARLY_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(30)
    }
}
