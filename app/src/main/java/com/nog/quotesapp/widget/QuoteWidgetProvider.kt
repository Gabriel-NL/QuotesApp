package com.nog.quotesapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.nog.quotesapp.MainActivity
import com.nog.quotesapp.QuotesApplication
import com.nog.quotesapp.R
import com.nog.quotesapp.background.DailyQuoteScheduler
import com.nog.quotesapp.model.Quote
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuoteWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == REROLL) {
            update(context, force = true)
        } else super.onReceive(context, intent)
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { update(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        update(context)
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPreferences.delete(context, it) }
    }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val appearances = oldIds.map { WidgetPreferences.load(context, it) }
        newIds.zip(appearances).forEach { (new, appearance) ->
            WidgetPreferences.save(context, new, appearance)
        }
        oldIds.filter { it !in newIds }.forEach { WidgetPreferences.delete(context, it) }
        update(context)
    }
    private fun update(context: Context, force: Boolean = false) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as QuotesApplication).container.dailyQuotes.refresh(force)
                syncAll(context)
                DailyQuoteScheduler.scheduleNext(context)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { DailyQuoteScheduler.enqueue(context) }
            finally { pending.finish() }
        }
    }
    companion object {
        private const val REROLL = "com.nog.quotesapp.WIDGET_REROLL"
        private val updateMutex = Mutex()
        suspend fun syncAll(context: Context) = updateMutex.withLock {
            val manager = AppWidgetManager.getInstance(context)
            if (manager.getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java)).isNotEmpty()) {
                val daily = (context.applicationContext as QuotesApplication).container.dailyQuotes.refresh()
                renderAll(context, daily)
            }
        }
        private fun renderAll(context: Context, quote: Quote?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuoteWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id ->
                val appearance = WidgetPreferences.load(context, id)
                val views = RemoteViews(context.packageName, R.layout.quote_widget)
                views.setInt(R.id.widget_root, "setBackgroundColor", if (appearance.transparent) Color.TRANSPARENT else appearance.color)
                val foreground = if (appearance.lightText) Color.WHITE else Color.BLACK
                views.setTextColor(R.id.widget_quote, foreground)
                views.setTextColor(R.id.widget_title, foreground)
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
                val compact = height < 150
                views.setViewVisibility(R.id.widget_title, if (compact) View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_actions, if (compact) View.GONE else View.VISIBLE)
                views.setTextViewTextSize(R.id.widget_quote, android.util.TypedValue.COMPLEX_UNIT_SP, if (compact) 14f else 18f)
                val quoteHeight = (height - 24 - if (compact) 0 else 70).coerceAtLeast(20)
                val fontScale = context.resources.configuration.fontScale
                views.setInt(R.id.widget_quote, "setMaxLines", (quoteHeight / ((if (compact) 14f else 18f) * 1.3f * fontScale)).toInt().coerceAtLeast(1))
                views.setTextViewText(R.id.widget_quote, quote?.text ?: "No quote available. Check your quotes and Home tag filter in the app.")
                views.setBoolean(R.id.widget_reroll, "setEnabled", quote != null)
                views.setFloat(R.id.widget_reroll, "setAlpha", if (quote == null) 0.4f else 1f)
                views.setOnClickPendingIntent(R.id.widget_quote, PendingIntent.getActivity(context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setOnClickPendingIntent(R.id.widget_reroll, PendingIntent.getBroadcast(context, 1,
                    Intent(context, QuoteWidgetProvider::class.java).setAction(REROLL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setOnClickPendingIntent(R.id.widget_settings, PendingIntent.getActivity(context, id,
                    Intent(context, WidgetSettingsActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .setData(android.net.Uri.parse("quotesapp://widget/$id/appearance")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id, views)
            }
        }
    }
}
