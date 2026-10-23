package com.nog.quotesapp.background

import android.app.job.JobParameters
import android.app.job.JobService
import com.nog.quotesapp.QuotesApplication
import com.nog.quotesapp.widget.QuoteWidgetProvider
import kotlinx.coroutines.*

class DailyQuoteUpdateService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            var retry = false
            try {
                (application as QuotesApplication).container.dailyQuotes.refresh()
                QuoteWidgetProvider.syncAll(this@DailyQuoteUpdateService)
                (application as QuotesApplication).container.notifications.publish()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { retry = true }
            finally { DailyQuoteScheduler.scheduleNext(this@DailyQuoteUpdateService) }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { running?.cancel(); return true }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
