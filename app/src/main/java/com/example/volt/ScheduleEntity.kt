package com.example.volt

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey
    val scheduleDate: String,

    val queue: String,

    val intervalsJson: String,

    val publishedAt: String?,

    val isComplete: Boolean,

    val savedAt: Long
)
