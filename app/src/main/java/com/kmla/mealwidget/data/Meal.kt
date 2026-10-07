package com.kmla.mealwidget.data

import java.time.LocalDate
import java.time.LocalTime

/** 끼니 종류. htmlId는 kmlaonline.net 메인 페이지에서 각 끼니 메뉴가 들어 있는 div의 id. */
enum class MealType(val label: String, val htmlId: String) {
    BREAKFAST("아침", "food-breakfast"),
    LUNCH("점심", "food-lunch"),
    DINNER("저녁", "food-dinner");

    companion object {
        /** 지금 시각에 보여줄 끼니. 경계 시각은 여기서 바꾸면 됩니다. */
        private val LUNCH_FROM: LocalTime = LocalTime.of(8, 30)
        private val DINNER_FROM: LocalTime = LocalTime.of(13, 30)

        fun forTime(time: LocalTime): MealType = when {
            time < LUNCH_FROM -> BREAKFAST
            time < DINNER_FROM -> LUNCH
            else -> DINNER
        }
    }
}

/** 하루치 급식. date는 페이지의 "2026년 10월 7일 (수)" 문구에서 읽은 날짜. */
data class DailyMenu(
    val date: LocalDate?,
    val dateLabel: String,
    val meals: Map<MealType, List<String>>,
) {
    fun itemsOf(type: MealType): List<String> = meals[type].orEmpty()
}

/** 캐시에 저장되는 단위. fetchedAt은 epoch millis. */
data class CachedMenu(
    val menu: DailyMenu,
    val fetchedAt: Long,
)

class MealParseException(message: String) : Exception(message)
