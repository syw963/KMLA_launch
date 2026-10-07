package com.kmla.mealwidget.data

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * 메뉴 목록을 화면용 텍스트로 만듭니다.
 * 매일 나오는 밥·김치류는 흐린 색으로 표시해서 그날의 주요 반찬이 눈에 먼저 들어오게 합니다.
 */
object MenuFormatter {

    private val STAPLES = setOf(
        "백미밥", "쌀밥", "흑미밥", "잡곡밥", "현미밥", "보리밥", "기장밥", "귀리밥",
        "배추김치", "깍두기", "섞박지", "총각김치", "열무김치", "김치", "깍두기김치",
    )

    fun isStaple(item: String): Boolean {
        val name = item.replace(" ", "")
        return name in STAPLES || (name.endsWith("김치") && name.length <= 5)
    }

    /** dimColor가 null이면 흐리게 처리하지 않습니다. */
    fun format(items: List<String>, separator: String, dimColor: Int?): CharSequence {
        val sb = SpannableStringBuilder()
        items.forEachIndexed { i, item ->
            if (i > 0) sb.append(separator)
            val start = sb.length
            sb.append(item)
            if (dimColor != null && isStaple(item)) {
                sb.setSpan(
                    ForegroundColorSpan(dimColor), start, sb.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return sb
    }
}
