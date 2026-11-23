
package com.example.volt

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ScheduleUpdateScheduler {

    private const val PERIODIC_WORK_NAME = "volt_schedule_update"
    private const val INITIAL_WORK_NAME = "volt_initial_schedule_update"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        // Перше оновлення після додавання віджета.
        val initialRequest =
            OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
                .build()

        workManager.enqueueUniqueWork(
            INITIAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            initialRequest
        )

        // Подальше автоматичне оновлення кожні 15 хвилин.
        val periodicRequest =
            PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(
                15,
                TimeUnit.MINUTES
            ).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        Log.d(
            "VOLT_SCHEDULER",
            "Початкове та періодичне оновлення зареєстровано"
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(
            context.applicationContext
        )

        workManager.cancelUniqueWork(INITIAL_WORK_NAME)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)

        Log.d(
            "VOLT_SCHEDULER",
            "Оновлення скасовано: віджетів більше немає"
        )
    }
}
