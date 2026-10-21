package com.nog.quotesapp.data.repository

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.QuoteEntity

class QuoteRepository(private val db: AppDatabase) {
    suspend fun updateQuote(id: Int, text: String, tagIds: List<Int>) {
        require(text.trim().isNotEmpty())
        db.withTransaction {
            check(db.quoteDao().exists(id)) { "Quote no longer exists" }
            db.quoteDao().updateQuoteTextUsingId(id, text.trim())
            db.quoteTagCrossRefDao().deleteForQuote(id)
            tagIds.distinct().forEach { db.quoteTagCrossRefDao().insert(id, it) }
        }
    }

    suspend fun deleteQuotes(ids: List<Int>) {
        db.withTransaction {
            ids.distinct().forEach {
                db.fifoDao().deleteForQuote(it)
                db.quoteDao().deleteById(it)
            }
        }
    }

    suspend fun createQuote(text: String, tagIds: List<Int>): Long {
        val trimmedText = text.trim()
        require(trimmedText.isNotEmpty()) { "Quote text cannot be empty" }
        return db.withTransaction {
            val id = db.quoteDao().insert(QuoteEntity(text = trimmedText))
            check(id in 1..Int.MAX_VALUE.toLong()) { "Quote ID is out of range" }
            tagIds.distinct().forEach { tagId ->
                db.quoteTagCrossRefDao().insert(quoteId = id.toInt(), tagId = tagId)
            }
            id
        }
    }
}
