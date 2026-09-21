package top.rootu.lampa.sched

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.rootu.lampa.App
import top.rootu.lampa.BuildConfig
import top.rootu.lampa.channels.LampaChannels
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object Scheduler {
    private const val CARDS_JOB_ID = 1001
    private val isUpdate = AtomicBoolean(false)
    private val schedulerScope = CoroutineScope(Dispatchers.IO)

    /**
     * Schedules periodic updates for Android TV content.
     *
     * @param sched Whether to schedule updates or perform a one-shot update.
     */
    fun scheduleUpdate(sched: Boolean) {
        if (!isAndroidTV) return

        if (BuildConfig.DEBUG) Log.d("Scheduler", "scheduleUpdate(sched: $sched)")

        if (sched) {
            jobScheduler()
        } else {
            // Perform a one-shot update in a background thread
            schedulerScope.launch {
                if (BuildConfig.DEBUG) Log.d("Scheduler", "one-shot updateContent")
                updateContent(sync = false)
            }
        }
    }

    /**
     * Uses JobScheduler to schedule periodic updates.
     */
    private fun jobScheduler() {
        val context = App.context
        val jobScheduler = context.getSystemService(JobScheduler::class.java) ?: return

        // Configure the JobScheduler for periodic updates
        val jobInfo = JobInfo.Builder(
            CARDS_JOB_ID,
            ComponentName(context, ContentJobService::class.java)
        ).apply {
            setPeriodic(TimeUnit.MINUTES.toMillis(15)) // Schedule every 15 minutes
            setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // Require any network
            setPersisted(true) // Keep job across reboots
        }.build()

        if (BuildConfig.DEBUG) Log.d("Scheduler", "jobScheduler schedule periodic updates")
        jobScheduler.schedule(jobInfo)
    }

    /**
     * Updates the Android TV Home content.
     *
     * @param sync Whether to update TV channels sequentially or in parallel.
     */
    fun updateContent(sync: Boolean) {
        if (!isUpdate.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) Log.d("Scheduler", "updateContent already running, skipping")
            return
        }
        try {
            if (BuildConfig.DEBUG) Log.d("Scheduler", "updateContent call LampaChannels.update($sync)")
            LampaChannels.update(sync)
        } finally {
            isUpdate.set(false)
        }
    }
}
