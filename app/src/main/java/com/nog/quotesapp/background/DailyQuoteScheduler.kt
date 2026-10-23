package com.nog.quotesapp.background

import android.app.*
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.*
import android.os.Build
import com.nog.quotesapp.model.dailyPeriod
import com.nog.quotesapp.QuotesApplication

object DailyQuoteScheduler {
    private const val JOB_ID = 1120
    suspend fun scheduleNext(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, DailyQuoteReceiver::class.java).setAction("com.nog.quotesapp.MIDNIGHT")
        val pending = PendingIntent.getBroadcast(context, 1120, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val settings = (context.applicationContext as QuotesApplication).container.settings.current()
        val at = dailyPeriod(System.currentTimeMillis(), minutesOfDay = settings.dailyMinutes).nextMidnightMillis
        // Exact delivery needs special access on recent Android. Gracefully fall back.
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            try { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return }
            catch (_: SecurityException) { /* Access can be revoked between check and call. */ }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
    fun enqueue(context: Context) {
        context.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, DailyQuoteUpdateService::class.java))
                .setOverrideDeadline(0).build()
        )
    }
}

class DailyQuoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyQuoteScheduler.enqueue(context)
    }
}
