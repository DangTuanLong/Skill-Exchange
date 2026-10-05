package com.skillexchange.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.skillexchange.app.data.local.dao.ProfileDao
import com.skillexchange.app.data.local.entity.ProfileEntity

@Database(
    entities = [ProfileEntity::class],
    version = 2,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN availabilityJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}

