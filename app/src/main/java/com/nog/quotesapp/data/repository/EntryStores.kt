package com.nog.quotesapp.data.repository

import com.nog.quotesapp.model.*
import kotlinx.coroutines.flow.Flow

interface QuoteStore {
    fun observeQuotes(): Flow<List<Quote>>
    fun observeLinks(): Flow<List<QuoteTagLink>>
    suspend fun loadQuote(id: Int): QuoteDraft
    suspend fun createQuote(text: String, tagIds: List<Int>): Long
    suspend fun updateQuote(id: Int, text: String, tagIds: List<Int>)
    suspend fun deleteQuotes(ids: List<Int>)
}

interface TagStore {
    fun observeTags(): Flow<List<Tag>>
    suspend fun saveTag(id: Int?, name: String)
    suspend fun deleteTags(ids: List<Int>)
}
