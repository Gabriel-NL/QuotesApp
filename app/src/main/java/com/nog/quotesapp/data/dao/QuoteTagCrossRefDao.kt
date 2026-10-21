package com.nog.quotesapp.data.dao

import androidx.room.Dao
import androidx.room.Query

import com.nog.quotesapp.data.entity.QuoteTagCrossRefEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteTagCrossRefDao
{
    @Query("""
        INSERT INTO quote_tags_cross_ref (quoteId, tagId)
        VALUES (:quoteId, :tagId)
    """)
    suspend fun insert(quoteId: Int, tagId: Int)

    @Query("""
        DELETE FROM quote_tags_cross_ref
        WHERE quoteId = :quoteId AND tagId = :tagId
    """)
    suspend fun delete(quoteId: Int, tagId: Int)

    @Query("SELECT * FROM quote_tags_cross_ref")
    suspend fun getAll(): List<QuoteTagCrossRefEntity>

    @Query("SELECT * FROM quote_tags_cross_ref")
    fun observeAll(): Flow<List<QuoteTagCrossRefEntity>>

    @Query("DELETE FROM quote_tags_cross_ref WHERE quoteId = :quoteId")
    suspend fun deleteForQuote(quoteId: Int)

    @Query("DELETE FROM quote_tags_cross_ref")
    suspend fun deleteAll()
}
