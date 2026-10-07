package com.kmla.mealwidget.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuFormatterTest {
    @Test
    fun staplesAreDetected() {
        listOf("백미밥", "배추김치", "섞박지", "깍두기", "열무김치").forEach {
            assertTrue(it, MenuFormatter.isStaple(it))
        }
    }

    @Test
    fun mainDishesAreNotStaples() {
        listOf("날치알김치볶음밥", "고사리제육볶음", "해물순두부찌개", "김치찌개", "알배추겉절이").forEach {
            assertFalse(it, MenuFormatter.isStaple(it))
        }
    }
}
