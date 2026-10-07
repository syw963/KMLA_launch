package com.kmla.mealwidget.widget

import android.content.Context
import com.kmla.mealwidget.data.MealType
import java.time.LocalDate
import java.time.LocalTime

/**
 * 위젯에 어느 끼니를 보여줄지.
 * 기본은 시간에 따라 자동(아침→점심→저녁)이고, 탭을 누르면 그 끼니로 고정됩니다.
 * 고정은 다음 자동 전환 시점(또는 날짜가 바뀌면)에 풀립니다.
 */
object WidgetState {
    private const val PREFS = "widget_state"
    private const val KEY_SLOT = "override_slot"
    private const val KEY_SCOPE = "override_scope"

    fun currentSlot(context: Context): MealType {
        val auto = MealType.forTime(LocalTime.now())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scope = prefs.getString(KEY_SCOPE, null)
        val slot = prefs.getString(KEY_SLOT, null)
        if (slot != null && scope == scopeKey(auto)) {
            runCatching { return MealType.valueOf(slot) }
        }
        return auto
    }

    fun select(context: Context, type: MealType) {
        val auto = MealType.forTime(LocalTime.now())
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SLOT, type.name)
            .putString(KEY_SCOPE, scopeKey(auto))
            .apply()
    }

    private fun scopeKey(auto: MealType) = "${LocalDate.now()}_${auto.name}"
}
