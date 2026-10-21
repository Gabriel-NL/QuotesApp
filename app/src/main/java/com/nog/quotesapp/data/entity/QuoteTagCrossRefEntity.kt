package com.nog.quotesapp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "quote_tags_cross_ref",

    primaryKeys = ["quoteId", "tagId"],

    foreignKeys =
        [
            ForeignKey(
                entity = QuoteEntity::class,
                parentColumns = ["id"],
                childColumns = ["quoteId"],
                onDelete = ForeignKey.CASCADE
            ),

            ForeignKey(
                entity = TagEntity::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE
            )
        ]
)
data class QuoteTagCrossRefEntity(
    val quoteId: Int,
    val tagId: Int
)