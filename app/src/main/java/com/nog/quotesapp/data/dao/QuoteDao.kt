package com.nog.quotesapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nog.quotesapp.data.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao
{
    // INSERT (must stay annotation-based in Room)
    @Insert
    suspend fun insert(quote: QuoteEntity): Long

    @Query("UPDATE quotes SET text = :text WHERE id = :id")
    suspend fun updateQuoteTextUsingId(id: Int, text: String)


    // DELETE by ID (your preferred style)
    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM quotes WHERE id = :id)")
    suspend fun exists(id: Int): Boolean

    // READ
    @Query("SELECT * FROM quotes")
    suspend fun getAll(): List<QuoteEntity>

    @Query("SELECT * FROM quotes ORDER BY text COLLATE NOCASE, id")
    fun observeAll(): Flow<List<QuoteEntity>>

    // CLEAR TABLE
    @Query("DELETE FROM quotes")
    suspend fun deleteAll()
}
