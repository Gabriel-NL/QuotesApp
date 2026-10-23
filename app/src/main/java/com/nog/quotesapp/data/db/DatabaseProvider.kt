package com.nog.quotesapp.data.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider
{
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase
    {
        return INSTANCE ?: synchronized(this)
        {
            val instance =
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quotes_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

            INSTANCE = instance
            instance
        }
    }
}