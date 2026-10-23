package com.nog.quotesapp.data.repository

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.SettingEntity
import com.nog.quotesapp.model.*
import kotlinx.coroutines.flow.map

class SettingsRepository(private val db:AppDatabase):SettingsStore {
    override fun observe()=db.settingsDao().observe().map { rows -> AppSettings.from(rows.map { SettingValue(it.key,it.valueType,it.value) }) }
    override suspend fun current()=read(db)
    override suspend fun save(settings:AppSettings) {
        settings.validated()
        db.withTransaction {
            val old=read(db)
            write(db,settings)
            if(old.dailyMinutes!=settings.dailyMinutes) {
                db.dailyQuoteDao().get()?.let { db.dailyQuoteDao().save(it.copy(localDate=dailyPeriod(System.currentTimeMillis(), minutesOfDay=settings.dailyMinutes).key)) }
            }
            db.fifoDao().trim(effectiveHistoryLimit(settings.historyLimit,db.quoteDao().getAll().size))
        }
    }
    override suspend fun clearHistory()=db.withTransaction { db.fifoDao().deleteAll() }
    companion object {
        suspend fun read(db:AppDatabase)=AppSettings.from(db.settingsDao().getAll().map { SettingValue(it.key,it.valueType,it.value) })
        suspend fun write(db:AppDatabase,settings:AppSettings) {
            db.settingsDao().save(settings.values().map { SettingEntity(it.key,it.type,it.value) })
        }
    }
}
