package com.nog.quotesapp.data.repository

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.QuoteEntity

class QuoteRepository(private val db: AppDatabase) {
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
