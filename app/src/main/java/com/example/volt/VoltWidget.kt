
package com.example.volt

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val SHOW_TOMORROW = booleanPreferencesKey("show_tomorrow")
private val CURRENT_DATE = stringPreferencesKey("current_date")
private val KYIV_TIME_ZONE = TimeZone.getTimeZone("Europe/Kyiv")

@SuppressLint("RestrictedApi")
class VoltWidget : GlanceAppWidget() {

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = KYIV_TIME_ZONE
        }

        val calendar = Calendar.getInstance(KYIV_TIME_ZONE)
        val today = dateFormat.format(calendar.time)

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = dateFormat.format(calendar.time)

        // Якщо настала нова дата, повертаємо перемикач на сьогодні.
        updateAppWidgetState(context, id) { preferences ->
            val previousDate = preferences[CURRENT_DATE]

            if (previousDate != today) {
                preferences[SHOW_TOMORROW] = false
                preferences[CURRENT_DATE] = today
            }
        }

        val database = VoltDatabase.getDatabase(context)
        val repository = ScheduleRepository(database.scheduleDao())

        val todaySchedule = repository.getScheduleByDate(today)
        val tomorrowSchedule = repository.getScheduleByDate(tomorrow)

        val statusPreferences = context.getSharedPreferences(
            ScheduleUpdateWorker.STATUS_PREFERENCES,
            Context.MODE_PRIVATE
        )

        val connectionError = statusPreferences.getBoolean(
            ScheduleUpdateWorker.KEY_CONNECTION_ERROR,
            false
        )

        val lastSuccessfulUpdate = statusPreferences.getLong(
            ScheduleUpdateWorker.KEY_LAST_SUCCESSFUL_UPDATE,
            0L
        )

        val updateTimeText = if (lastSuccessfulUpdate > 0L) {
            val updateFormat = SimpleDateFormat(
                "dd.MM HH:mm",
                Locale("uk", "UA")
            ).apply {
                timeZone = KYIV_TIME_ZONE
            }

            updateFormat.format(Date(lastSuccessfulUpdate))
        } else {
            null
        }

        provideContent {
            val preferences = currentState<Preferences>()
            val showTomorrow = preferences[SHOW_TOMORROW] ?: false

            val selectedSchedule = if (showTomorrow) {
                tomorrowSchedule
            } else {
                todaySchedule
            }

            val activeColor = ColorProvider(Color(0xFFF1FEC8))
            val inactiveColor = ColorProvider(Color(0xFF777482))
            val secondaryColor = ColorProvider(Color(0xFFAAA7B8))
            val outageColor = ColorProvider(Color(0xFFB85C68))
            val powerColor = ColorProvider(Color(0xFF8CBA83))
            val warningColor = ColorProvider(Color(0xFFE5A45B))

            val status = if (showTomorrow) {
                ScheduleStatusCalculator.calculate(
                    selectedSchedule,
                    nowMinutes = 0
                )
            } else {
                ScheduleStatusCalculator.calculate(selectedSchedule)
            }

            val statusText = when {
                selectedSchedule == null -> "Графік не опублікований"
                !selectedSchedule.isComplete -> "Графік неповний"
                showTomorrow -> "Графік на завтра"
                else -> status.title
            }

            val nextChangeText = when {
                selectedSchedule == null -> "--:--"
                !selectedSchedule.isComplete -> "--:--"
                status.nextChange != null -> status.nextChange
                else -> "Немає"
            }

            val nextChangeCaption = when {
                selectedSchedule == null -> "очікуємо графік"
                !selectedSchedule.isComplete -> "неповні дані"

                showTomorrow && status.nextChange != null ->
                    "Перша зміна завтра"

                showTomorrow ->
                    "Змін не заплановано"

                status.isPowerOn == false ->
                    "Наступне ввімкнення"

                status.isPowerOn == true && status.nextChange != null ->
                    "Наступне вимкнення"

                else ->
                    "Змін більше немає"
            }

            val intervals = selectedSchedule?.intervals.orEmpty()
                .sortedBy { it.start.toMinutes() ?: Int.MAX_VALUE }

            val intervalsText = when {
                selectedSchedule == null -> "Час відключень невідомий"
                !selectedSchedule.isComplete -> "Очікуємо повний графік"
                intervals.isEmpty() -> "Відключень немає"
                else -> intervals.joinToString("  ·  ") {
                    "${it.start}–${it.end}"
                }
            }

            val scheduleDateText = when {
                selectedSchedule == null ->
                    "Графік за ${if (showTomorrow) tomorrow else today} не опублікований"

                !selectedSchedule.isComplete ->
                    "Графік за ${selectedSchedule.scheduleDate} · неповний"

                showTomorrow ->
                    "Завтра · ${selectedSchedule.scheduleDate}"

                else ->
                    "Сьогодні · ${selectedSchedule.scheduleDate}"
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF23212C))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {

                // 1. Заголовок із кнопкою ручного оновлення
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = "VOLT",
                        style = TextStyle(
                            color = activeColor,
                            fontSize = 18.sp
                        )
                    )

                    Text(
                        text = "  ЧЕРГА 2.1",
                        style = TextStyle(
                            color = secondaryColor,
                            fontSize = 10.sp
                        )
                    )

                    Spacer(
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Box(
                        modifier = GlanceModifier
                            .background(Color(0xFF41404B))
                            .clickable(
                                onClick = actionRunCallback<ManualRefreshAction>()
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 2.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↻",
                            style = TextStyle(
                                color = activeColor,
                                fontSize = 16.sp
                            )
                        )
                    }
                }

                // 2. Статус і перемикач дати
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = when {
                                    selectedSchedule == null -> inactiveColor
                                    !selectedSchedule.isComplete -> secondaryColor
                                    showTomorrow -> activeColor
                                    status.isPowerOn == true -> activeColor
                                    else -> secondaryColor
                                },
                                fontSize = 15.sp
                            )
                        )

                        Column(
                            horizontalAlignment = Alignment.Horizontal.End
                        ) {
                            Text(
                                text = nextChangeText,
                                style = TextStyle(
                                    color = activeColor,
                                    fontSize = 20.sp
                                )
                            )

                            Text(
                                text = nextChangeCaption,
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }

                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(Color(0xFF41404B))
                                .clickable(
                                    onClick = actionRunCallback<ShowTodayAction>()
                                )
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "СЬОГОДНІ",
                                style = TextStyle(
                                    color = if (!showTomorrow) activeColor else inactiveColor,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .padding(start = 5.dp)
                                .background(
                                    if (showTomorrow) Color(0xFF41404B)
                                    else Color(0xFF23212C)
                                )
                                .clickable(
                                    onClick = actionRunCallback<ShowTomorrowAction>()
                                )
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ЗАВТРА →",
                                style = TextStyle(
                                    color = if (showTomorrow) activeColor else inactiveColor,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // 3. Погодинна шкала
                Column(
                    modifier = GlanceModifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(11.dp)
                    ) {
                        repeat(3) { chunkIndex ->
                            Row(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .height(11.dp)
                            ) {
                                repeat(8) { offset ->
                                    val hour = chunkIndex * 8 + offset
                                    val hourStart = hour * 60
                                    val hourEnd = hourStart + 60

                                    val isOutage = intervals.any { interval ->
                                        val start = interval.start.toMinutes()
                                        val end = interval.end.toMinutes()

                                        start != null &&
                                                end != null &&
                                                start < hourEnd &&
                                                end > hourStart
                                    }

                                    Box(
                                        modifier = GlanceModifier
                                            .defaultWeight()
                                            .height(11.dp)
                                            .padding(
                                                end = if (hour < 23) 1.dp else 0.dp
                                            )
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxSize()
                                                .background(
                                                    if (isOutage) outageColor else powerColor
                                                )
                                        ) {}
                                    }
                                }
                            }
                        }
                    }

                    // Підписи часу
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Row(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "00",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Start
                                )
                            )

                            Text(
                                text = "04",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Start
                                )
                            )
                        }

                        Row(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "08",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Start
                                )
                            )

                            Text(
                                text = "12",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Start
                                )
                            )
                        }

                        Row(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "16",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Start
                                )
                            )

                            Text(
                                text = "20",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center
                                )
                            )

                            Text(
                                text = "24",
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = secondaryColor,
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.End
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // 4. Години відключень, дата та стан з'єднання
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                ) {
                    Text(
                        text = "ГОДИНИ ВІДКЛЮЧЕНЬ",
                        style = TextStyle(
                            color = secondaryColor,
                            fontSize = 10.sp
                        )
                    )

                    Text(
                        text = intervalsText,
                        modifier = GlanceModifier.padding(top = 2.dp),
                        style = TextStyle(
                            color = activeColor,
                            fontSize = 12.sp
                        )
                    )

                    Text(
                        text = scheduleDateText,
                        modifier = GlanceModifier.padding(top = 3.dp),
                        style = TextStyle(
                            color = inactiveColor,
                            fontSize = 8.sp
                        )
                    )

                    Text(
                        text = updateTimeText?.let {
                            "Оновлено: $it"
                        } ?: "Ще не оновлювалося",
                        modifier = GlanceModifier.padding(top = 2.dp),
                        style = TextStyle(
                            color = inactiveColor,
                            fontSize = 8.sp
                        )
                    )

                    if (connectionError) {
                        val warningText = if (updateTimeText != null) {
                            "Telegram недоступний · останнє оновлення: $updateTimeText"
                        } else {
                            "Telegram недоступний · графік ще не оновлювався"
                        }

                        Text(
                            text = warningText,
                            modifier = GlanceModifier.padding(top = 3.dp),
                            style = TextStyle(
                                color = warningColor,
                                fontSize = 8.sp
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

private fun String.toMinutes(): Int? {
    val parts = split(":")
    if (parts.size != 2) return null

    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null

    if (minutes !in 0..59) return null
    if (hours == 24 && minutes == 0) return 1440
    if (hours !in 0..23) return null

    return hours * 60 + minutes
}

class ManualRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        val request =
            OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "volt_manual_schedule_update",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

class ShowTodayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[SHOW_TOMORROW] = false
        }

        VoltWidget().update(context, glanceId)
    }
}

class ShowTomorrowAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { preferences ->
            preferences[SHOW_TOMORROW] = true
        }

        VoltWidget().update(context, glanceId)
    }
}
