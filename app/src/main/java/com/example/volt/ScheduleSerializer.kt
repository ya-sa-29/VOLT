
package com.example.volt

import org.json.JSONArray
import org.json.JSONObject

object ScheduleSerializer {

    fun encode(intervals: List<ScheduleInterval>): String {
        val jsonArray = JSONArray()

        intervals.forEach { interval ->
            val item = JSONObject().apply {
                put("start", interval.start)
                put("end", interval.end)
            }

            jsonArray.put(item)
        }

        return jsonArray.toString()
    }

    fun decode(json: String): List<ScheduleInterval>? {
        return try {
            val jsonArray = JSONArray(json)
            val intervals = mutableListOf<ScheduleInterval>()

            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)

                val start = item.getString("start")
                val end = item.getString("end")

                intervals.add(
                    ScheduleInterval(
                        start = start,
                        end = end
                    )
                )
            }

            intervals
        } catch (_: Exception) {
            null
        }
    }
}
