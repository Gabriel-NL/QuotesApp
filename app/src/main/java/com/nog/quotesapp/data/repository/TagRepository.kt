package com.nog.quotesapp.data.repository

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.TagEntity
import com.nog.quotesapp.model.EntryLimits
import com.nog.quotesapp.model.Tag
import com.nog.quotesapp.model.tagFilterKey
import kotlinx.coroutines.flow.map

class TagRepository(private val db: AppDatabase) : TagStore {
    override fun observeTags() = db.tagDao().observeAll().map { rows ->
        rows.map { Tag(it.id, it.name) }
    }

    override suspend fun saveTag(id: Int?, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        db.withTransaction {
            val old = id?.let { db.tagDao().getById(it) }
            val updated = if(old?.name==name) name else trimmed
            require(old?.name==updated || updated.length<=SettingsRepository.read(db).tagLimit) { "Tag exceeds configured limit." }
            if (id == null) db.tagDao().insert(TagEntity(name = trimmed))
            else {
                check(db.tagDao().exists(id)) { "Tag no longer exists" }
                db.tagDao().updateTagNameUsingId(id, updated)
                val configuration=SettingsRepository.read(db)
                if(old!=null && configuration.dailyTagNames.any { tagFilterKey(it)==tagFilterKey(old.name) }) {
                    SettingsRepository.write(db,configuration.copy(dailyTagNames=configuration.dailyTagNames.map {
                        if(tagFilterKey(it)==tagFilterKey(old.name)) updated else it
                    }.toSet()))
                }
            }
        }
    }

    override suspend fun deleteTags(ids: List<Int>) {
        db.withTransaction { ids.distinct().forEach { db.tagDao().deleteById(it) } }
    }
}
