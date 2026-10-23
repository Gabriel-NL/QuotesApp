package com.nog.quotesapp.data.dao

import androidx.room.*
import com.nog.quotesapp.data.entity.DailyQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyQuoteDao {
    @Query("SELECT * FROM daily_quote WHERE id = 1")
    fun observe(): Flow<DailyQuoteEntity?>
    @Query("SELECT * FROM daily_quote WHERE id = 1")
    suspend fun get(): DailyQuoteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: DailyQuoteEntity)

    @Query("DELETE FROM daily_quote")
    suspend fun deleteAll()
}
