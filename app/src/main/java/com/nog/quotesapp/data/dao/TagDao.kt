package com.nog.quotesapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nog.quotesapp.data.entity.TagEntity

@Dao
interface TagDao
{
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun updateTagNameUsingId(id: Int, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM tags WHERE id = :id)")
    suspend fun exists(id: Int): Boolean

    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<TagEntity>

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}