package com.skillexchange.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.skillexchange.app.data.local.dao.ProfileDao
import com.skillexchange.app.data.local.entity.ProfileEntity

@Database(
    entities = [ProfileEntity::class],
    version = 2,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
