package com.nog.quotesapp.data.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName="app_settings")
data class SettingEntity(@PrimaryKey val key:String, val valueType:String, val value:String)
