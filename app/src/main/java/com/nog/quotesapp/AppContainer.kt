package com.nog.quotesapp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.nog.quotesapp.data.db.DatabaseProvider
import com.nog.quotesapp.data.repository.QuoteRepository
import com.nog.quotesapp.data.repository.TagRepository
import com.nog.quotesapp.data.repository.SettingsRepository
import com.nog.quotesapp.backup.BackupRepository
import com.nog.quotesapp.platform.AndroidDocumentFiles
import com.nog.quotesapp.platform.DailyQuoteNotifications
import com.nog.quotesapp.viewmodel.*
import com.nog.quotesapp.data.repository.DailyQuoteRepository
import com.nog.quotesapp.background.DailyQuoteScheduler
import com.nog.quotesapp.widget.QuoteWidgetProvider
import com.nog.quotesapp.model.dailyPeriod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import android.util.Log

// Composition root: this is the only UI entry-point setup that knows Room.
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private val db = DatabaseProvider.getDatabase(context.applicationContext)
    private val quotes = QuoteRepository(db, scope)
    private val tags = TagRepository(db)

    val settings = SettingsRepository(db)
    private val backups = BackupRepository(db)
    private val files = AndroidDocumentFiles(appContext)
    val dailyQuotes = DailyQuoteRepository(db, configured = { settings.current() })
    val notifications = DailyQuoteNotifications(appContext,dailyQuotes,settings)

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(quotes.observeQuotes(),quotes.observeLinks(),tags.observeTags()) { _,_,_ -> Unit }
                .catch { Log.e("DailyQuote", "Quote observation failed", it) }.collect {
                try { dailyQuotes.refresh() }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { Log.e("DailyQuote", "Could not refresh quote", error) }
            }
        }
        scope.launch {
            dailyQuotes.observeDailyQuote().catch { Log.e("DailyQuote", "Daily quote observation failed", it) }.collect {
                try { QuoteWidgetProvider.syncAll(appContext); notifications.publish() }
                catch(error:CancellationException) { throw error }
                catch(error:Exception) { Log.e("DailyQuote","Could not update daily displays",error) }
            }
        }
        scope.launch {
            settings.observe().collectLatest { configuration ->
                while (isActive) {
                    try { dailyQuotes.refresh(); notifications.publish(); DailyQuoteScheduler.scheduleNext(appContext) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Log.e("DailyQuote", "Could not schedule quote", error) }
                    val wait = dailyPeriod(System.currentTimeMillis(), minutesOfDay = configuration.dailyMinutes).nextMidnightMillis - System.currentTimeMillis()
                    delay(wait.coerceAtLeast(1000))
                }
            }
        }
    }

    fun factory(quoteId: Int? = null): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
            SettingsViewModel::class.java -> SettingsViewModel(settings, backups, files, onChanged={ DailyQuoteScheduler.enqueue(appContext) }, tags=tags)
            HomeViewModel::class.java -> HomeViewModel(quotes, dailyQuotes, extras.createSavedStateHandle(),settings,tags,onRerolled={notifications.publish(force=true)})
            QuoteViewModel::class.java -> QuoteViewModel(quotes, tags)
            TagsViewModel::class.java -> TagsViewModel(tags, settings)
            QuoteEditorViewModel::class.java -> QuoteEditorViewModel(quotes, tags, quoteId, extras.createSavedStateHandle(), settings)
            else -> error("Unknown ViewModel: $modelClass")
        } as T
    }
}
