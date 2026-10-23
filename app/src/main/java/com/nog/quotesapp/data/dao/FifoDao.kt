package com.nog.quotesapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query

import com.nog.quotesapp.data.entity.FifoEntity

@Dao
interface FifoDao
{
    @Insert
    suspend fun insert(quote: FifoEntity)

    @Update
    suspend fun update(quote: FifoEntity)

    @Delete
    suspend fun delete(quote: FifoEntity)

    @Query("SELECT * FROM quote_fifo")
    suspend fun getAll(): List<FifoEntity>

    @Query("DELETE FROM quote_fifo WHERE quoteId = :quoteId")
    suspend fun deleteForQuote(quoteId: Int)

    @Query("DELETE FROM quote_fifo WHERE quoteId NOT IN (SELECT id FROM quotes)")
    suspend fun deleteOrphans()

    @Query("SELECT quoteId FROM quote_fifo ORDER BY id DESC LIMIT :limit")
    suspend fun recentIds(limit: Int): List<Int>

    @Query("DELETE FROM quote_fifo WHERE id NOT IN (SELECT id FROM quote_fifo ORDER BY id DESC LIMIT :limit)")
    suspend fun trim(limit: Int)

    @Query("DELETE FROM quote_fifo")
    suspend fun deleteAll()
}
