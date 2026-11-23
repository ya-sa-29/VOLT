
package com.example.volt

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.volt.ui.theme.VoltTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "VOLT_TELEGRAM"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Реєструємо автоматичне фонове оновлення
        ScheduleUpdateScheduler.schedule(applicationContext)

        setContent {
            VoltTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        refreshSchedules()
    }

    private fun refreshSchedules() {
        lifecycleScope.launch {
            val database = VoltDatabase.getDatabase(applicationContext)
            val repository = ScheduleRepository(database.scheduleDao())

            val targetDates = getTodayAndTomorrow()

            Log.d(TAG, "========== ЛОКАЛЬНИЙ КЕШ ==========")

            var cachedCount = 0

            for (date in targetDates) {
                val schedule = repository.getScheduleByDate(date)

                if (schedule != null) {
                    cachedCount++

                    Log.d(TAG, """
                        ЗНАЙДЕНО ЛОКАЛЬНИЙ ГРАФІК
                        Дата: ${schedule.scheduleDate}
                        Черга: ${schedule.queue}
                        Повний графік: ${schedule.isComplete}
                        Інтервалів: ${schedule.intervals.size}
                    """.trimIndent())

                    if (date == targetDates.first()) {
                        logScheduleStatus(schedule, "ЛОКАЛЬНИЙ КЕШ")
                    }
                } else {
                    Log.d(TAG, "Локального графіка за $date немає")
                }
            }

            Log.d(TAG, "У кеші знайдено графіків: $cachedCount")

            Log.d(TAG, "========== ОНОВЛЕННЯ TELEGRAM ==========")

            try {
                Log.d(TAG, "Завантажуємо HTML Telegram...")

                val html = withContext(Dispatchers.IO) {
                    TelegramClient.fetchChannelHtml()
                }

                Log.d(TAG, "HTML отримано: ${html.length} символів")

                val messages = withContext(Dispatchers.Default) {
                    TelegramParser.parseMessages(html)
                }

                Log.d(TAG, "Знайдено повідомлень: ${messages.size}")

                val schedules = withContext(Dispatchers.Default) {
                    messages.mapNotNull { message ->
                        TelegramParser.parseQueueSchedule(message)
                    }
                }.sortedBy { it.publishedAt.orEmpty() }

                Log.d(TAG, "Розібрано графіків: ${schedules.size}")

                if (schedules.isEmpty()) {
                    Log.w(TAG, "Telegram не повернув жодного графіка черги 2.1")
                }

                var savedCount = 0

                for (schedule in schedules) {
                    repository.saveSchedule(schedule)
                    savedCount++

                    Log.d(TAG, "Оновлено графік за ${schedule.scheduleDate}")
                }

                Log.d(TAG, "Успішно оновлено графіків: $savedCount")

                Log.d(TAG, "========== ПЕРЕВІРКА ПІСЛЯ ОНОВЛЕННЯ ==========")

                for (date in targetDates) {
                    val schedule = repository.getScheduleByDate(date)

                    if (schedule != null) {
                        Log.d(TAG, """
                            АКТУАЛЬНИЙ ГРАФІК
                            Дата: ${schedule.scheduleDate}
                            Черга: ${schedule.queue}
                            Повний графік: ${schedule.isComplete}
                            Інтервалів: ${schedule.intervals.size}
                        """.trimIndent())

                        if (date == targetDates.first()) {
                            logScheduleStatus(schedule, "ОНОВЛЕНИЙ ГРАФІК")
                        }
                    } else {
                        Log.w(TAG, "Графік за $date ще не опублікований")
                    }
                }

                Log.d(TAG, "Оновлення завершено успішно")

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Telegram недоступний. Використовуємо локальний кеш.",
                    e
                )

                Log.d(TAG, "Кількість графіків у кеші: $cachedCount")
            }
        }
    }

    private fun logScheduleStatus(
        schedule: QueueSchedule,
        source: String
    ) {
        val status = ScheduleStatusCalculator.calculate(schedule)

        Log.d(TAG, """
            ========== ІНТЕРВАЛИ ГРАФІКА ==========
            Джерело: $source
            Дата: ${schedule.scheduleDate}
            ${schedule.intervals.joinToString("\n") { "${it.start} - ${it.end}" }}

            ========== СТАН ЕЛЕКТРОПОСТАЧАННЯ ==========
            Статус: ${status.title}
            Наступна зміна: ${status.nextChange ?: "Немає"}
            Є світло: ${status.isPowerOn}
        """.trimIndent())
    }

    private fun getTodayAndTomorrow(): List<String> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val calendar = Calendar.getInstance()

        val today = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 1)

        val tomorrow = dateFormat.format(calendar.time)

        return listOf(today, tomorrow)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
