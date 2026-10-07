package com.kmla.mealwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.kmla.mealwidget.MainActivity
import com.kmla.mealwidget.R
import com.kmla.mealwidget.data.MealRepository
import com.kmla.mealwidget.data.MealType
import com.kmla.mealwidget.data.MenuFormatter
import com.kmla.mealwidget.data.Settings
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

    // 기본 글자 크기(sp). 설정의 배율이 곱해집니다.
    private const val MENU_SP = 15f
    private const val TAB_SP = 13f
    private const val DATE_SP = 13f
    private const val STATUS_SP = 11f

    private val DATE_FMT = DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)
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
        val scale = Settings.fontSize(context).scale
        val views = RemoteViews(context.packageName, R.layout.widget_meal)

        // ── 머리글: 날짜 ──
        val title = cached?.menu?.date?.format(DATE_FMT)
            ?: cached?.menu?.dateLabel?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        views.setTextViewText(R.id.widget_date, title)
        views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, DATE_SP * scale)

        // ── 아침/점심/저녁 세그먼트 탭 ──
        for ((type, viewId) in TABS) {
            val selected = type == slot
            views.setInt(
                viewId, "setBackgroundResource",
                if (selected) R.drawable.tab_selected_bg else 0,
            )
            setTextColorRes(
                context, views, viewId,
                if (selected) R.color.widget_on_accent else R.color.widget_text_secondary,
            )
            views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, TAB_SP * scale)
            views.setOnClickPendingIntent(viewId, MealWidgetProvider.selectIntent(context, type))
        }

        // ── 메뉴 본문 ──
        val menuSp = MENU_SP * scale
        views.setTextViewTextSize(R.id.widget_menu, TypedValue.COMPLEX_UNIT_SP, menuSp)
        views.setTextViewTextSize(R.id.widget_menu_2, TypedValue.COMPLEX_UNIT_SP, menuSp)
        views.setViewVisibility(R.id.widget_menu_2, View.GONE)

        val items = cached?.menu?.itemsOf(slot).orEmpty()
        val dim = if (Settings.dimStaples(context)) {
            ContextCompat.getColor(context, R.color.widget_text_tertiary)
        } else null

        when {
            cached == null -> views.setTextViewText(
                R.id.widget_menu,
                context.getString(if (refreshing) R.string.loading else R.string.load_failed),
            )
            items.isEmpty() -> views.setTextViewText(
                R.id.widget_menu, context.getString(R.string.no_menu, slot.label),
            )
            else -> {
                val (lines, width) = widgetSpace(mgr, widgetId, menuSp, scale)
                when {
                    // 1) 한 줄에 하나씩 다 들어감
                    items.size <= lines ->
                        views.setTextViewText(R.id.widget_menu, MenuFormatter.format(items, "\n", dim))
                    // 2) 두 칸으로 나누면 들어감
                    items.size <= lines * 2 && width >= 220 -> {
                        val half = (items.size + 1) / 2
                        views.setTextViewText(
                            R.id.widget_menu, MenuFormatter.format(items.take(half), "\n", dim),
                        )
                        views.setTextViewText(
                            R.id.widget_menu_2, MenuFormatter.format(items.drop(half), "\n", dim),
                        )
                        views.setViewVisibility(R.id.widget_menu_2, View.VISIBLE)
                    }
                    // 3) 공간이 부족하면 이어 붙이기
                    else ->
                        views.setTextViewText(R.id.widget_menu, MenuFormatter.format(items, "  ·  ", dim))
                }
            }
        }

        // ── 상태줄 ──
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
        views.setTextViewTextSize(
            R.id.widget_status, TypedValue.COMPLEX_UNIT_SP,
            STATUS_SP * scale.coerceAtMost(1.15f),
        )

        views.setOnClickPendingIntent(R.id.widget_refresh, MealWidgetProvider.refreshIntent(context))
        views.setOnClickPendingIntent(R.id.widget_menu_area, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_date, openAppIntent(context))

        mgr.updateAppWidget(widgetId, views)
    }

    /**
     * 위젯 크기로 메뉴를 몇 줄, 몇 dp 폭으로 보여줄 수 있는지 대략 계산합니다.
     * 세로 화면에서는 폭=MIN_WIDTH, 높이=MAX_HEIGHT가 실제 크기입니다.
     */
    private fun widgetSpace(
        mgr: AppWidgetManager,
        widgetId: Int,
        menuSp: Float,
        scale: Float,
    ): Pair<Int, Int> {
        val opts = mgr.getAppWidgetOptions(widgetId)
        val height = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            .takeIf { it > 0 }
            ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 }
            ?: 180
        val width = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 } ?: 250
        // 여백 32 + 날짜줄 26 + 탭 34 + 간격 14 + 상태줄 18 ≈ 124dp (글자 크기에 따라 조금 늘어남)
        val overhead = 124 + (scale - 1f) * 30
        val lineHeight = menuSp * 1.5f
        val lines = ((height - overhead) / lineHeight).toInt().coerceAtLeast(1)
        return lines to (width - 32)
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
