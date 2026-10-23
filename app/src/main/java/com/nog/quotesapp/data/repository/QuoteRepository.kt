package com.nog.quotesapp.data.repository

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.QuoteEntity
import com.nog.quotesapp.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

class QuoteRepository(private val db: AppDatabase, scope: CoroutineScope? = null) : QuoteStore {
    private val quoteFlow = db.quoteDao().observeAll().map { rows -> rows.map { Quote(it.id, it.text) } }
    private val cachedQuotes = scope?.let { appScope ->
        quoteFlow.map { Result.success(it) }.catch { error ->
            if (error is CancellationException) throw error
            emit(Result.failure(error))
        }.shareIn(appScope, SharingStarted.Eagerly, replay = 1)
    }
    override fun observeQuotes(): Flow<List<Quote>> = cachedQuotes?.map { it.getOrThrow() } ?: quoteFlow

    override fun observeLinks() = db.quoteTagCrossRefDao().observeAll().map { rows ->
        rows.map { QuoteTagLink(it.quoteId, it.tagId) }
    }

    override suspend fun loadQuote(id: Int): QuoteDraft = db.withTransaction {
        val row = checkNotNull(db.quoteDao().getById(id)) { "Quote no longer exists" }
        QuoteDraft(Quote(row.id, row.text), db.quoteTagCrossRefDao().getAll()
            .filter { it.quoteId == id }.map { it.tagId })
    }

    override suspend fun updateQuote(id: Int, text: String, tagIds: List<Int>) {
        require(text.trim().isNotEmpty())
        db.withTransaction {
            val original=checkNotNull(db.quoteDao().getById(id)) { "Quote no longer exists" }
            val updated = if(text==original.text) original.text else text.trim()
            require(updated==original.text || updated.length<=SettingsRepository.read(db).quoteLimit) { "Quote exceeds configured limit." }
            db.quoteDao().updateQuoteTextUsingId(id, updated)
            db.quoteTagCrossRefDao().deleteForQuote(id)
            tagIds.distinct().forEach { db.quoteTagCrossRefDao().insert(id, it) }
        }
    }

    override suspend fun deleteQuotes(ids: List<Int>) {
        db.withTransaction {
            ids.distinct().forEach {
                db.fifoDao().deleteForQuote(it)
                db.quoteDao().deleteById(it)
            }
        }
    }

    override suspend fun createQuote(text: String, tagIds: List<Int>): Long {
        val trimmedText = text.trim()
        require(trimmedText.isNotEmpty()) { "Quote text cannot be empty" }
        return db.withTransaction {
            require(trimmedText.length<=SettingsRepository.read(db).quoteLimit) { "Quote exceeds configured limit." }
            val id = db.quoteDao().insert(QuoteEntity(text = trimmedText))
            check(id in 1..Int.MAX_VALUE.toLong()) { "Quote ID is out of range" }
            tagIds.distinct().forEach { tagId ->
                db.quoteTagCrossRefDao().insert(quoteId = id.toInt(), tagId = tagId)
            }
            id
        }
    }
}
