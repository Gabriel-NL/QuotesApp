package com.nog.quotesapp.data.repository

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.DailyQuoteEntity
import com.nog.quotesapp.data.entity.FifoEntity
import com.nog.quotesapp.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.TimeZone
import kotlin.random.Random

interface DailyQuoteStore {
    fun observeDailyQuote(): Flow<Quote?>
    suspend fun refresh(force: Boolean = false): Quote?
}

class DailyQuoteRepository(
    private val db: AppDatabase,
    private val historyLimit: Int = RECENT_QUOTE_LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault,
    private val randomIndex: (Int) -> Int = { Random.nextInt(it) },
    private val configured: (suspend () -> AppSettings)? = null
) : DailyQuoteStore {
    override fun observeDailyQuote(): Flow<Quote?> = combine(
        db.dailyQuoteDao().observe(), db.quoteDao().observeAll()
    ) { daily, rows ->
        rows.firstOrNull { it.id == daily?.quoteId }?.let { Quote(it.id, it.text) }
    }

    override suspend fun refresh(force: Boolean): Quote? = db.withTransaction {
        val time = now()
        val settings = configured?.invoke() ?: AppSettings(historyLimit = historyLimit)
        val period = dailyPeriod(time, zone(), settings.dailyMinutes)
        val allRows = db.quoteDao().getAll()
        val allowed = filterDailyQuotes(allRows.map { Quote(it.id,it.text) },
            db.tagDao().getAll().map { Tag(it.id,it.name) },
            db.quoteTagCrossRefDao().getAll().map { QuoteTagLink(it.quoteId,it.tagId) },settings).map { it.id }.toSet()
        val rows = allRows.filter { it.id in allowed }
        val fifo = db.fifoDao()
        fifo.deleteOrphans()
        val limit = effectiveHistoryLimit(settings.historyLimit, rows.size)
        fifo.trim(limit)
        val stored = db.dailyQuoteDao().get()
        if (rows.isEmpty()) {
            if (stored?.quoteId != null || stored?.localDate != period.key)
                db.dailyQuoteDao().save(DailyQuoteEntity(quoteId = null, localDate = period.key))
            return@withTransaction null
        }
        val existing = rows.firstOrNull { it.id == stored?.quoteId }
        if (!force && stored?.localDate == period.key && existing != null)
            return@withTransaction Quote(existing.id, existing.text)
        val candidates = eligibleQuoteIds(rows.map { it.id }, fifo.recentIds(limit))
        val id = candidates[randomIndex(candidates.size)]
        val chosen = rows.first { it.id == id }
        db.dailyQuoteDao().save(DailyQuoteEntity(quoteId = id, localDate = period.key))
        fifo.deleteForQuote(id)
        if (limit > 0) fifo.insert(FifoEntity(quoteId = id, insertedAt = time))
        fifo.trim(limit)
        Quote(chosen.id, chosen.text)
    }
}
