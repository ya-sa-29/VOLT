package com.example.volt

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Upsert
    suspend fun saveSchedule(schedule: ScheduleEntity)

    @Query(
        "SELECT * FROM schedules WHERE scheduleDate = :date LIMIT 1"
    )
    suspend fun getScheduleByDate(date: String): ScheduleEntity?

    @Query(
        "SELECT * FROM schedules ORDER BY scheduleDate ASC"
    )
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    @Query("DELETE FROM schedules WHERE scheduleDate = :date")
    suspend fun deleteScheduleByDate(date: String)
}
