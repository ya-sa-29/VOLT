
package com.example.volt

import kotlinx.coroutines.flow.Flow

class ScheduleRepository(
    private val scheduleDao: ScheduleDao
) {

    suspend fun saveSchedule(schedule: QueueSchedule) {
        val existing = scheduleDao.getScheduleByDate(
            schedule.scheduleDate
        )

        // Не дозволяємо старішому повідомленню
        // перезаписувати новіше.
        val incomingTime = schedule.publishedAt
        val existingTime = existing?.publishedAt

        if (
            incomingTime != null &&
            existingTime != null &&
            incomingTime < existingTime
        ) {
            return
        }

        // Якщо час публікації невідомий, а в базі
        // вже є відомий час, не перезаписуємо дані.
        if (
            incomingTime == null &&
            existingTime != null
        ) {
            return
        }

        val isComplete = when {
            schedule.isComplete -> true
            existing?.isComplete == true -> true
            else -> false
        }

        val entity = ScheduleEntity(
            scheduleDate = schedule.scheduleDate,
            queue = schedule.queue,
            intervalsJson = ScheduleSerializer.encode(
                schedule.intervals
            ),
            publishedAt = incomingTime ?: existingTime,
            isComplete = isComplete,
            savedAt = System.currentTimeMillis()
        )

        scheduleDao.saveSchedule(entity)
    }

    suspend fun getScheduleByDate(date: String): QueueSchedule? {
        val entity = scheduleDao.getScheduleByDate(date)
            ?: return null

        val intervals = ScheduleSerializer.decode(
            entity.intervalsJson
        ) ?: return null

        return QueueSchedule(
            queue = entity.queue,
            scheduleDate = entity.scheduleDate,
            publishedAt = entity.publishedAt,
            intervals = intervals,
            isComplete = entity.isComplete
        )
    }

    fun getAllSchedules(): Flow<List<ScheduleEntity>> {
        return scheduleDao.getAllSchedules()
    }
}
