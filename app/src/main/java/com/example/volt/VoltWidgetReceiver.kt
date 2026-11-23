
package com.example.volt

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class VoltWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = VoltWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        // Перший віджет додано на головний екран.
        // Запускаємо початкове та періодичне оновлення.
        ScheduleUpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        // Останній віджет видалено.
        // Більше немає потреби оновлювати графік.
        ScheduleUpdateScheduler.cancel(context)
    }
}
