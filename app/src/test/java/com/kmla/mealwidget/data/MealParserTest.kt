package com.kmla.mealwidget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2026-10-07 kmlaonline.net 메인 페이지의 급식 영역 구조를 그대로 옮긴 HTML로 파서를 검증합니다.
 * Android Studio에서 이 파일을 열고 클래스 옆 ▶ 버튼으로 실행하세요.
 */
class MealParserTest {

    private val html = """
        <html><body><div id="total-content"><form><div>
          <div>
            <div>2026년 10월 7일 (수)</div>
            <div> <a>아침</a> | <a>점심</a> | <a>저녁</a> </div>
            <div id="food-breakfast" class="morning">
              <div class="food-votes">평점 없음</div>
              로그인해서 평점을 매기세요.
              <hr>
              백미밥<br>누룽지닭곰탕<br>방풍나물무침<br>잡채말이어묵볶음<br>고등어오븐구이<br>섞박지<br>시리얼우유식빵주스김요거트<br>생과일<br>
            </div>
            <div id="food-lunch" class="afternoon">
              <div class="food-votes">평점 없음</div>
              로그인해서 평점을 매기세요.
              <hr>
              날치알김치볶음밥<br>유부잔치국수<br>양배추샐러드참깨드레싱<br>통살새우까스적채타르소스<br>알배추겉절이<br>얇은피만두찜<br>거봉<br>
            </div>
            <div id="food-dinner" class="night">
              <div class="food-votes">평점 없음</div>
              로그인해서 평점을 매기세요.
              <hr>
              백미밥<br>해물순두부찌개<br>시금치크래미무침<br>진미채버터볶음<br>양상추샐러드야채D<br>고사리제육볶음<br>배추김치<br>귤생과<br>
            </div>
          </div>
        </div></form></div></body></html>
    """.trimIndent()

    @Test
    fun parsesDate() {
        val menu = MealParser.parse(html)
        assertEquals(LocalDate.of(2026, 10, 7), menu.date)
        assertEquals("2026년 10월 7일 (수)", menu.dateLabel)
    }

    @Test
    fun parsesAllMeals() {
        val menu = MealParser.parse(html)
        assertEquals(
            listOf("백미밥", "누룽지닭곰탕", "방풍나물무침", "잡채말이어묵볶음",
                "고등어오븐구이", "섞박지", "시리얼우유식빵주스김요거트", "생과일"),
            menu.itemsOf(MealType.BREAKFAST),
        )
        assertEquals(7, menu.itemsOf(MealType.LUNCH).size)
        assertEquals("날치알김치볶음밥", menu.itemsOf(MealType.LUNCH).first())
        assertEquals("귤생과", menu.itemsOf(MealType.DINNER).last())
    }

    @Test
    fun skipsRatingNotices() {
        val all = MealParser.parse(html).meals.values.flatten()
        assertTrue(all.none { it.contains("평점") })
    }

    @Test
    fun emptyMealIsEmptyList() {
        val menu = MealParser.parse(
            """<div>2026년 10월 10일 (토)</div>
               <div id="food-breakfast"><div class="food-votes">평점 없음</div><hr></div>"""
        )
        assertTrue(menu.itemsOf(MealType.BREAKFAST).isEmpty())
        assertTrue(menu.itemsOf(MealType.LUNCH).isEmpty())
    }

    @Test(expected = MealParseException::class)
    fun failsWhenNoMealSection() {
        MealParser.parse("<html><body>점검 중입니다</body></html>")
    }
}
