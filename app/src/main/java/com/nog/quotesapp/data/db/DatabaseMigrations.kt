package com.nog.quotesapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `daily_quote` (
            `id` INTEGER NOT NULL, `quoteId` INTEGER, `localDate` TEXT NOT NULL,
            PRIMARY KEY(`id`), FOREIGN KEY(`quoteId`) REFERENCES `quotes`(`id`)
            ON UPDATE NO ACTION ON DELETE SET NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_quote_quoteId` ON `daily_quote` (`quoteId`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_settings` (`key` TEXT NOT NULL, `valueType` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
    }
}
