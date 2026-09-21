package top.rootu.lampa.sched

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.rootu.lampa.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

class ContentJobService : JobService() {
    private val isJobComplete = AtomicBoolean(false)
    private val jobScope = CoroutineScope(Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        isJobComplete.set(false)

        jobScope.launch {
            try {
                if (BuildConfig.DEBUG) Log.i("ContentJobService", "onStartJob: updateContent")
                Scheduler.updateContent(sync = true)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("ContentJobService", "Error updating content", e)
            } finally {
                isJobComplete.set(true)
                jobFinished(params, false)
            }
        }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return !isJobComplete.get()
    }

    override fun onDestroy() {
        super.onDestroy()
        jobScope.cancel()
    }
}
