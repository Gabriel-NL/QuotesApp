package com.nog.quotesapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nog.quotesapp.data.dao.*
import com.nog.quotesapp.data.entity.*

@Database(
    entities =
        [
            SettingEntity::class,
            DailyQuoteEntity::class,
            FifoEntity::class,
            QuoteEntity::class,
            TagEntity::class,
            QuoteTagCrossRefEntity::class
        ],
    version = 3
)
abstract class AppDatabase : RoomDatabase()
{
    abstract fun settingsDao(): SettingsDao
    abstract fun dailyQuoteDao(): DailyQuoteDao
    abstract fun quoteDao(): QuoteDao
    abstract fun tagDao(): TagDao
    abstract fun quoteTagCrossRefDao(): QuoteTagCrossRefDao
    abstract fun fifoDao(): FifoDao
}