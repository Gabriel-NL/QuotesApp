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
}