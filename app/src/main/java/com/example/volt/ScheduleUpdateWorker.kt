
package com.example.volt

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduleUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = applicationContext.getSharedPreferences(
            STATUS_PREFERENCES,
            Context.MODE_PRIVATE
        )

        try {
            Log.d(TAG, "Починаємо фонове оновлення графіків")

            // Перевіряємо наявність підтвердженого інтернет-з'єднання.
            if (!hasValidatedInternet()) {
                Log.w(TAG, "Інтернет недоступний. Завантаження пропущено.")

                preferences.edit()
                    .putBoolean(KEY_CONNECTION_ERROR, true)
                    .apply()

                try {
                    VoltWidget().updateAll(applicationContext)
                    Log.d(TAG, "Віджет оновлено: інтернет недоступний")
                } catch (updateException: Exception) {
                    Log.e(
                        TAG,
                        "Не вдалося оновити віджет після втрати мережі",
                        updateException
                    )
                }

                // Наступна спроба відбудеться за розкладом.
                return@withContext Result.success()
            }

            // Завантажуємо Telegram.
            val html = TelegramClient.fetchChannelHtml()
            Log.d(TAG, "HTML отримано: ${html.length} символів")

            // Парсимо повідомлення.
            val messages = TelegramParser.parseMessages(html)
            Log.d(TAG, "Отримано повідомлень: ${messages.size}")

            // Сортуємо повідомлення від найстарішого до найновішого.
            val sortedMessages = messages
                .filter { !it.publishedAt.isNullOrBlank() }
                .sortedBy { it.publishedAt }

            val skippedMessages = messages.size - sortedMessages.size

            if (skippedMessages > 0) {
                Log.w(
                    TAG,
                    "Пропущено повідомлень без дати публікації: $skippedMessages"
                )
            }

            // Витягуємо графіки черги 2.1.
            val schedules = sortedMessages.mapNotNull { message ->
                TelegramParser.parseQueueSchedule(message)
            }

            Log.d(TAG, "Знайдено графіків: ${schedules.size}")

            // Зберігаємо графіки в Room.
            val database = VoltDatabase.getDatabase(applicationContext)
            val repository = ScheduleRepository(database.scheduleDao())

            schedules.forEach { schedule ->
                repository.saveSchedule(schedule)

                Log.d(
                    TAG,
                    "Збережено графік: ${schedule.scheduleDate}, " +
                            "інтервалів: ${schedule.intervals.size}, " +
                            "повний: ${schedule.isComplete}, " +
                            "опубліковано: ${schedule.publishedAt}"
                )
            }

            // Запам'ятовуємо час останнього успішного з'єднання.
            preferences.edit()
                .putLong(KEY_LAST_SUCCESSFUL_UPDATE, System.currentTimeMillis())
                .putBoolean(KEY_CONNECTION_ERROR, false)
                .apply()

            // Оновлюємо всі встановлені віджети.
            VoltWidget().updateAll(applicationContext)

            Log.d(TAG, "Усі віджети Volt оновлено")
            Log.d(TAG, "Фонове оновлення завершено успішно")

            Result.success()

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "ПОМИЛКА: ${exception.javaClass.simpleName}: ${exception.message}"
            )

            Log.e(
                TAG,
                "STACK TRACE:\n${Log.getStackTraceString(exception)}"
            )

            // Позначаємо невдалу спробу з'єднання.
            preferences.edit()
                .putBoolean(KEY_CONNECTION_ERROR, true)
                .apply()

            // Відображаємо попередження у віджеті.
            try {
                VoltWidget().updateAll(applicationContext)
            } catch (updateException: Exception) {
                Log.e(
                    TAG,
                    "Не вдалося оновити віджет після помилки",
                    updateException
                )
            }

            Result.retry()
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network = connectivityManager.activeNetwork
            ?: return false

        val capabilities =
            connectivityManager.getNetworkCapabilities(network)
                ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) && capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
    }

    companion object {
        private const val TAG = "VOLT_WORKER"

        const val STATUS_PREFERENCES = "volt_status"
        const val KEY_LAST_SUCCESSFUL_UPDATE = "last_successful_update"
        const val KEY_CONNECTION_ERROR = "connection_error"
    }
}
