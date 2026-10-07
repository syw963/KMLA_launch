package com.kmla.mealwidget.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.LocalDate

/**
 * kmlaonline.net 메인(로그인) 페이지 HTML에서 급식표를 뽑아냅니다.
 *
 * 2026-10 기준 페이지 구조:
 * ```
 * <div>2026년 10월 7일 (수)</div>
 * <div><a>아침</a><a>점심</a><a>저녁</a></div>
 * <div id="food-breakfast" class="morning">
 *   <div class="food-votes">평점 없음</div>
 *   로그인해서 평점을 매기세요.
 *   <hr>
 *   백미밥<br>누룽지닭곰탕<br>...
 * </div>
 * <div id="food-lunch" class="afternoon">...</div>
 * <div id="food-dinner" class="night">...</div>
 * ```
 * 사이트 구조가 바뀌면 이 파일(그리고 MealType.htmlId)만 고치면 됩니다.
 */
object MealParser {

    private val DATE_REGEX =
        Regex("""(\d{4})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*일(?:\s*\([^)]*\))?""")

    /** 메뉴가 아닌 안내 문구들. */
    private val NOISE = listOf("로그인해서 평점", "평점 없음", "평점:")

    fun parse(html: String): DailyMenu {
        val doc = Jsoup.parse(html)

        val sections = MealType.entries.associateWith { doc.getElementById(it.htmlId) }
        if (sections.values.all { it == null }) {
            throw MealParseException("페이지에서 급식 영역을 찾을 수 없습니다 (사이트 구조 변경?)")
        }
        val meals = sections.mapValues { (_, el) -> el?.let(::extractItems).orEmpty() }

        // 날짜는 급식 영역을 감싼 부모에서 먼저 찾고, 없으면 페이지 전체에서 찾습니다.
        val container = sections.values.firstNotNullOfOrNull { it }?.parent()
        val match = DATE_REGEX.find(container?.text().orEmpty())
            ?: DATE_REGEX.find(doc.body()?.text().orEmpty())
        val date = match?.let {
            runCatching {
                LocalDate.of(
                    it.groupValues[1].toInt(),
                    it.groupValues[2].toInt(),
                    it.groupValues[3].toInt(),
                )
            }.getOrNull()
        }

        return DailyMenu(date = date, dateLabel = match?.value.orEmpty(), meals = meals)
    }

    /** `<hr>` 뒤의 텍스트를 `<br>` 기준으로 나눠 메뉴 목록을 만듭니다. */
    internal fun extractItems(section: Element): List<String> {
        val nodes = section.childNodes()
        val hrIndex = nodes.indexOfFirst { it is Element && it.normalName() == "hr" }
        val start = if (hrIndex >= 0) hrIndex + 1 else 0

        val sb = StringBuilder()
        for (node in nodes.subList(start, nodes.size)) {
            when (node) {
                is TextNode -> sb.append(node.wholeText)
                is Element -> when {
                    node.normalName() == "br" -> sb.append('\n')
                    node.hasClass("food-votes") -> Unit
                    else -> sb.append('\n').append(node.wholeText()).append('\n')
                }
            }
        }

        return sb.split('\n')
            .map { it.replace(' ', ' ').trim() }
            .filter { line -> line.isNotEmpty() && NOISE.none { line.contains(it) } }
    }
}
