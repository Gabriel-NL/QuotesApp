package com.nog.quotesapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quote_fifo")
data class FifoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val quoteId: Int,

    val insertedAt: Long
)