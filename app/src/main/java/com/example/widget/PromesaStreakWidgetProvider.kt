package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.PromesaPreferences
import java.util.concurrent.TimeUnit

class PromesaStreakWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, PromesaStreakWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateAppWidget(context, manager, id)
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(
                PromesaPreferences.WIDGET_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val referenceEpochMs = prefs.getLong(PromesaPreferences.KEY_WIDGET_STREAK_REF_MS, 0L)
            val days = if (referenceEpochMs > 0L) {
                val diff = (System.currentTimeMillis() - referenceEpochMs).coerceAtLeast(0L)
                TimeUnit.MILLISECONDS.toDays(diff).toInt()
            } else {
                0
            }

            val views = RemoteViews(context.packageName, R.layout.widget_promesa_streak)
            views.setTextViewText(R.id.widget_days_count, days.toString())
            val label = if (days == 1) "día sin intentos" else "días sin intentos"
            views.setTextViewText(R.id.widget_days_label, label)

            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
