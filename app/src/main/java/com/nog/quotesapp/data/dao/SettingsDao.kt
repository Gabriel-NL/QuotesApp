package com.nog.quotesapp.data.dao
import androidx.room.*
import com.nog.quotesapp.data.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
@Dao interface SettingsDao {
    @Query("SELECT * FROM app_settings ORDER BY `key`") fun observe():Flow<List<SettingEntity>>
    @Query("SELECT * FROM app_settings ORDER BY `key`") suspend fun getAll():List<SettingEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(values:List<SettingEntity>)
}
