package com.nog.quotesapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nog.quotesapp.data.dao.*
import com.nog.quotesapp.data.entity.*

@Database(
    entities =
        [
            FifoEntity::class,
            QuoteEntity::class,
            TagEntity::class,
            QuoteTagCrossRefEntity::class
        ],
    version = 1
)
abstract class AppDatabase : RoomDatabase()
{
    abstract fun quoteDao(): QuoteDao
    abstract fun tagDao(): TagDao
    abstract fun quoteTagCrossRefDao(): QuoteTagCrossRefDao
    abstract fun fifoDao(): FifoDao
}