
package com.example.volt

import org.jsoup.Jsoup
import java.util.Calendar
import java.util.Locale

data class TelegramMessage(
    val id: String,
    val text: String,
    val publishedAt: String?
)

data class ScheduleInterval(
    val start: String,
    val end: String
)

data class QueueSchedule(
    val queue: String,
    val scheduleDate: String,
    val publishedAt: String?,
    val intervals: List<ScheduleInterval>,
    val isComplete: Boolean
)

object TelegramParser {

    private val monthNumbers = mapOf(
        "січня" to 1,
        "лютого" to 2,
        "березня" to 3,
        "квітня" to 4,
        "травня" to 5,
        "червня" to 6,
        "липня" to 7,
        "серпня" to 8,
        "вересня" to 9,
        "жовтня" to 10,
        "листопада" to 11,
        "грудня" to 12
    )

    private val dateRegex = Regex(
        """(?<!\d)(\d{1,2})\s+(січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня)(?:\s+(\d{4})\s+року)?""",
        RegexOption.IGNORE_CASE
    )

    private val mainScheduleHeading = Regex(
        """Години відсутності електропостачання""",
        RegexOption.IGNORE_CASE
    )

    private val partialUpdateHeading = Regex(
        """Оновлення графіка погодинних відключень\s*\(ГПВ\)\s*на""",
        RegexOption.IGNORE_CASE
    )

    private val queueRegex = Regex(
        """(?<!\d)2\.1(?!\d)\s+"""
    )

    private val nextQueueRegex = Regex(
        """\s+\d\.\d(?:\s|$)"""
    )

    private val intervalRegex = Regex(
        """(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})"""
    )

    fun parseMessages(html: String): List<TelegramMessage> {
        val document = Jsoup.parse(html)
        val messageElements = document.select("div.tgme_widget_message")

        return messageElements.mapNotNull { element ->
            val textElement = element.selectFirst(
                "div.tgme_widget_message_text"
            ) ?: return@mapNotNull null

            val text = textElement.text().trim()
            if (text.isBlank()) return@mapNotNull null

            val id = element.attr("data-post")
                .ifBlank {
                    element.selectFirst("a.tgme_widget_message_date")
                        ?.attr("href")
                        .orEmpty()
                }

            val publishedAt = element.selectFirst(
                "time[datetime]"
            )?.attr("datetime")

            TelegramMessage(id, text, publishedAt)
        }
    }

    fun parseQueueSchedule(message: TelegramMessage): QueueSchedule? {
        val text = message.text

        val mainMatch = mainScheduleHeading.find(text)
        val partialMatch = partialUpdateHeading.find(text)

        // Повідомлення повинно бути або основним графіком,
        // або частковим оновленням.
        val isPartial = mainMatch == null && partialMatch != null

        val contentStart = when {
            mainMatch != null -> mainMatch.range.last + 1
            partialMatch != null -> partialMatch.range.last + 1
            else -> return null
        }

        val scheduleDate = parseScheduleDate(text) ?: return null
        val scheduleText = text.substring(contentStart)

        // Шукаємо саме підчергу 2.1.
        val queueMatch = queueRegex.find(scheduleText) ?: return null

        val remainingText = scheduleText.substring(
            queueMatch.range.last + 1
        )

        // Відокремлюємо інтервали 2.1 від наступної підчерги.
        val nextQueueMatch = nextQueueRegex.find(remainingText)

        val queueText = if (nextQueueMatch != null) {
            remainingText.substring(0, nextQueueMatch.range.first)
        } else {
            remainingText
        }

        val matches = intervalRegex.findAll(queueText).toList()
        if (matches.isEmpty()) return null

        val intervals = matches.map { match ->
            ScheduleInterval(
                start = match.groupValues[1],
                end = match.groupValues[2]
            )
        }

        val sortedIntervals = intervals.sortedBy {
            timeToMinutes(it.start)
        }

        // Перевіряємо коректність кожного інтервалу.
        for (interval in sortedIntervals) {
            val start = timeToMinutes(interval.start) ?: return null
            val end = timeToMinutes(interval.end) ?: return null

            if (start >= end) return null
        }

        // Перевіряємо, щоб інтервали не перетиналися.
        for (index in 1 until sortedIntervals.size) {
            val previousEnd = timeToMinutes(
                sortedIntervals[index - 1].end
            ) ?: return null

            val currentStart = timeToMinutes(
                sortedIntervals[index].start
            ) ?: return null

            if (currentStart < previousEnd) return null
        }

        return QueueSchedule(
            queue = "2.1",
            scheduleDate = scheduleDate,
            publishedAt = message.publishedAt,
            intervals = sortedIntervals,
            isComplete = !isPartial
        )
    }

    private fun parseScheduleDate(text: String): String? {
        val match = dateRegex.find(text) ?: return null

        val day = match.groupValues[1].toIntOrNull() ?: return null
        val monthName = match.groupValues[2].lowercase(Locale.ROOT)
        val month = monthNumbers[monthName] ?: return null

        val explicitYear = match.groupValues[3]
            .takeIf { it.isNotBlank() }
            ?.toIntOrNull()

        val year = explicitYear
            ?: Calendar.getInstance().get(Calendar.YEAR)

        val calendar = Calendar.getInstance().apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }

        return try {
            calendar.time

            String.format(
                Locale.ROOT,
                "%04d-%02d-%02d",
                year,
                month,
                day
            )
        } catch (_: IllegalArgumentException) {
            null
        }
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
}
