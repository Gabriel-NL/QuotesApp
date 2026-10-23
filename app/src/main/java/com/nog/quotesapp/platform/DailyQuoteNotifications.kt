package com.nog.quotesapp.platform

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nog.quotesapp.MainActivity
import com.nog.quotesapp.R
import com.nog.quotesapp.data.repository.DailyQuoteStore
import com.nog.quotesapp.model.SettingsStore
import com.nog.quotesapp.model.dailyPeriod
import com.nog.quotesapp.model.notificationDelivery
import com.nog.quotesapp.model.NotificationDelivery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DailyQuoteNotifications(context:Context,private val daily:DailyQuoteStore,private val settings:SettingsStore) {
    private val context=context.applicationContext
    private val lock=Mutex()
    private val prefs=this.context.getSharedPreferences("daily_notifications",Context.MODE_PRIVATE)
    private val manager=this.context.getSystemService(NotificationManager::class.java)
    suspend fun publish(force:Boolean=false)=withContext(Dispatchers.IO) {
        lock.withLock {
            val quote=daily.refresh()
            if(quote==null) { manager.cancel(ID);return@withLock }
            if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(
                NotificationChannel(CHANNEL,"Quote of the day",NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description="The daily quote shared with Home and widgets."
                })
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return@withLock
            if(!manager.areNotificationsEnabled()) return@withLock
            if(Build.VERSION.SDK_INT>=26 && manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) return@withLock
            val period=dailyPeriod(System.currentTimeMillis(),minutesOfDay=settings.current().dailyMinutes).key
            val visible=manager.activeNotifications.any { it.id==ID }
            val delivery=notificationDelivery(prefs.getString("delivered_period",null),period,visible,force)
            // Never recreate a dismissed notification within the same quote day.
            if(delivery==NotificationDelivery.SKIP) return@withLock
            val pending=PendingIntent.getActivity(context,ID,
                Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_quote_notification)
                .setContentTitle("Quote of the day").setContentText(quote.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(quote.text))
                .setContentIntent(pending).setAutoCancel(false)
                .setSilent(delivery==NotificationDelivery.UPDATE_SILENTLY)
                .build()
            try {
                manager.notify(ID,notification)
                check(prefs.edit().putString("delivered_period",period).commit()) { "Could not save notification delivery." }
            } catch (_:SecurityException) { /* Permission can be revoked while publishing. */ }
        }
    }
    companion object { private const val CHANNEL="daily_quote";private const val ID=1121 }
}
