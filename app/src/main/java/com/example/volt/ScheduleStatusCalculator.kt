
package com.example.volt

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ScheduleStatus(
    val title: String,
    val nextChange: String?,
    val isPowerOn: Boolean?
)

object ScheduleStatusCalculator {

    private val kyivTimeZone = TimeZone.getTimeZone("Europe/Kyiv")

    fun calculate(
        schedule: QueueSchedule?,
        nowMinutes: Int = getCurrentMinutes()
    ): ScheduleStatus {

        if (schedule == null) {
            return ScheduleStatus(
                "Графік не опублікований",
                null,
                null
            )
        }

        if (!schedule.isComplete) {
            return ScheduleStatus(
                "Графік неповний",
                null,
                null
            )
        }

        val intervals = schedule.intervals
            .mapNotNull { interval ->
                val start = timeToMinutes(interval.start)
                    ?: return@mapNotNull null
                val end = timeToMinutes(interval.end)
                    ?: return@mapNotNull null

                if (start >= end) return@mapNotNull null

                start to end
            }
            .sortedBy { it.first }

        val currentOutage = intervals.firstOrNull { (start, end) ->
            nowMinutes >= start && nowMinutes < end
        }

        if (currentOutage != null) {
            val outageEnd = currentOutage.second

            return ScheduleStatus(
                title = "Немає світла",
                nextChange = formatNextChange(outageEnd),
                isPowerOn = false
            )
        }

        val nextOutage = intervals.firstOrNull { (start, _) ->
            start > nowMinutes
        }

        return ScheduleStatus(
            title = "Є світло",
            nextChange = nextOutage?.let {
                formatNextChange(it.first)
            },
            isPowerOn = true
        )
    }

    private fun getCurrentMinutes(): Int {
        val calendar = Calendar.getInstance(kyivTimeZone)

        return calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(Calendar.MINUTE)
    }

    private fun timeToMinutes(value: String): Int? {
        val parts = value.split(":")
        if (parts.size != 2) return null

        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null

        if (minutes !in 0..59) return null
        if (hours == 24 && minutes == 0) return 1440
        if (hours !in 0..23) return null

        return hours * 60 + minutes
    }

    private fun formatNextChange(minutes: Int): String {
        if (minutes == 1440) return "00:00 завтра"

        return String.format(
            Locale.ROOT,
            "%02d:%02d",
            minutes / 60,
            minutes % 60
        )
    }
}
