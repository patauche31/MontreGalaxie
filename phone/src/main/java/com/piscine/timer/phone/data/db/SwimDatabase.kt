package com.piscine.timer.phone.data.db

import android.content.Context
import androidx.room.*

@Database(entities = [SessionEntity::class], version = 2, exportSchema = false)
abstract class SwimDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile private var INSTANCE: SwimDatabase? = null
        fun getInstance(context: Context): SwimDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, SwimDatabase::class.java, "swim_phone_db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
