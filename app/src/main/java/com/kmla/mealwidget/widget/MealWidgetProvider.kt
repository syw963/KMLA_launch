package com.kmla.mealwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.kmla.mealwidget.data.MealRepository
import com.kmla.mealwidget.data.MealType
import com.kmla.mealwidget.work.MealRefreshWorker

class MealWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetRenderer.update(context, mgr, it) }
        MealRefreshWorker.schedulePeriodic(context)
        if (MealRepository(context).needsRefresh()) {
            MealRefreshWorker.refreshNow(context, force = false)
        }
    }

    override fun onEnabled(context: Context) {
        MealRefreshWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        MealRefreshWorker.cancelPeriodic(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle,
    ) {
        // 위젯 크기를 바꾸면 줄 배치를 다시 계산합니다.
        WidgetRenderer.update(context, mgr, widgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SELECT -> {
                val type = intent.getStringExtra(EXTRA_MEAL)
                    ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                    ?: return
                WidgetState.select(context, type)
                WidgetRenderer.updateAll(context)
            }
            ACTION_REFRESH -> {
                WidgetRenderer.updateAll(context, refreshing = true)
                MealRefreshWorker.refreshNow(context, force = true)
            }
        }
    }

    companion object {
        private const val ACTION_SELECT = "com.kmla.mealwidget.action.SELECT_MEAL"
        private const val ACTION_REFRESH = "com.kmla.mealwidget.action.REFRESH"
        private const val EXTRA_MEAL = "meal"

        fun selectIntent(context: Context, type: MealType): PendingIntent {
            val intent = Intent(context, MealWidgetProvider::class.java)
                .setAction(ACTION_SELECT)
                .putExtra(EXTRA_MEAL, type.name)
            return PendingIntent.getBroadcast(
                context, 10 + type.ordinal, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, MealWidgetProvider::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
