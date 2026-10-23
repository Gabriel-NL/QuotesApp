package com.nog.quotesapp.data.entity

import androidx.room.*

@Entity(tableName = "daily_quote", foreignKeys = [ForeignKey(
    entity = QuoteEntity::class, parentColumns = ["id"], childColumns = ["quoteId"],
    onDelete = ForeignKey.SET_NULL
)], indices = [Index("quoteId")])
data class DailyQuoteEntity(
    @PrimaryKey val id: Int = 1,
    val quoteId: Int?,
    val localDate: String
)
