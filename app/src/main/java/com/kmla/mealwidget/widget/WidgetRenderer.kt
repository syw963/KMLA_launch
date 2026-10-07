package com.kmla.mealwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.kmla.mealwidget.MainActivity
import com.kmla.mealwidget.R
import com.kmla.mealwidget.data.MealRepository
import com.kmla.mealwidget.data.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 캐시된 급식 데이터로 위젯 화면(RemoteViews)을 그립니다. */
object WidgetRenderer {

    private val TABS = mapOf(
        MealType.BREAKFAST to R.id.tab_breakfast,
        MealType.LUNCH to R.id.tab_lunch,
        MealType.DINNER to R.id.tab_dinner,
    )

    private val DATE_FMT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val SHORT_DATE_FMT = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    fun updateAll(context: Context, refreshing: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, MealWidgetProvider::class.java))
        ids.forEach { update(context, mgr, it, refreshing) }
    }

    fun update(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        refreshing: Boolean = false,
    ) {
        val repo = MealRepository(context)
        val cached = repo.cached()
        val slot = WidgetState.currentSlot(context)
        val today = LocalDate.now()
        val views = RemoteViews(context.packageName, R.layout.widget_meal)

        // 머리글: 날짜
        val title = cached?.menu?.date?.format(DATE_FMT)
            ?: cached?.menu?.dateLabel?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        views.setTextViewText(R.id.widget_date, title)

        // 아침/점심/저녁 탭
        for ((type, viewId) in TABS) {
            val selected = type == slot
            views.setInt(
                viewId, "setBackgroundResource",
                if (selected) R.drawable.tab_selected_bg else 0,
            )
            setTextColorRes(
                context, views, viewId,
                if (selected) R.color.widget_tab_selected_text else R.color.widget_text_secondary,
            )
            views.setOnClickPendingIntent(viewId, MealWidgetProvider.selectIntent(context, type))
        }

        // 메뉴 본문
        val items = cached?.menu?.itemsOf(slot).orEmpty()
        val body = when {
            cached == null && refreshing -> context.getString(R.string.loading)
            cached == null -> context.getString(R.string.load_failed)
            items.isEmpty() -> context.getString(R.string.no_menu, slot.label)
            items.size > availableLines(mgr, widgetId) -> items.joinToString("  ·  ")
            else -> items.joinToString("\n")
        }
        views.setTextViewText(R.id.widget_menu, body)

        // 상태줄
        val status = buildString {
            if (refreshing) {
                append(context.getString(R.string.refreshing))
                return@buildString
            }
            if (cached == null) return@buildString
            val date = cached.menu.date
            if (date != null && date != today) {
                append("⚠ ${date.format(SHORT_DATE_FMT)} 메뉴 · ")
            }
            val time = Instant.ofEpochMilli(cached.fetchedAt).atZone(ZoneId.systemDefault())
            append("${time.format(TIME_FMT)} 업데이트")
            val errorAt = repo.lastErrorAt()
            if (errorAt != null && errorAt > cached.fetchedAt) append(" · 연결 실패")
        }
        views.setTextViewText(R.id.widget_status, status)

        views.setOnClickPendingIntent(R.id.widget_refresh, MealWidgetProvider.refreshIntent(context))
        views.setOnClickPendingIntent(R.id.widget_menu, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_date, openAppIntent(context))

        mgr.updateAppWidget(widgetId, views)
    }

    /**
     * 위젯 높이로 메뉴를 몇 줄 보여줄 수 있는지 대략 계산합니다.
     * 줄 수보다 메뉴가 많으면 한 줄씩 대신 " · "로 이어 붙입니다.
     */
    private fun availableLines(mgr: AppWidgetManager, widgetId: Int): Int {
        val opts = mgr.getAppWidgetOptions(widgetId)
        val height = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            .takeIf { it > 0 }
            ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 }
            ?: 180
        // 머리글+탭+상태줄+여백 ≈ 100dp, 본문 한 줄 ≈ 20dp
        return ((height - 100) / 20).coerceAtLeast(1)
    }

    /** Android 12+는 다크 모드 전환 시에도 맞는 색이 나오도록 리소스로 지정합니다. */
    private fun setTextColorRes(context: Context, views: RemoteViews, viewId: Int, @ColorRes res: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(viewId, "setTextColor", res)
        } else {
            views.setTextColor(viewId, ContextCompat.getColor(context, res))
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
