package com.piscine.timer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class],
    version = 2,          // ⬆ v1→v2 : ajout strokeCountsJson
    exportSchema = false
)
abstract class SwimDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: SwimDatabase? = null

        fun getInstance(context: Context): SwimDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SwimDatabase::class.java,
                    "swim_database"
                )
                .fallbackToDestructiveMigration()   // Dev : recréer si schema change
                .build().also { INSTANCE = it }
            }
        }
    }
}
