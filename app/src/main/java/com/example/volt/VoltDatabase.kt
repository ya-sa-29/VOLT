package com.example.volt

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ScheduleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VoltDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    companion object {

        @Volatile
        private var INSTANCE: VoltDatabase? = null

        fun getDatabase(context: Context): VoltDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoltDatabase::class.java,
                    "volt_database"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
