package com.kmla.mealwidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** 급식 데이터를 사이트에서 받아오고, 기기에 캐시합니다. */
class MealRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun cached(): CachedMenu? =
        prefs.getString(KEY_MENU, null)?.let { runCatching { decode(it) }.getOrNull() }

    /** 마지막 실패 시각(epoch millis). 성공하면 지워집니다. */
    fun lastErrorAt(): Long? =
        prefs.getLong(KEY_ERROR_AT, 0L).takeIf { it > 0L }

    /**
     * 새로 받아올 필요가 있는지.
     * - 캐시가 없거나 오늘 날짜가 아니면 받아옴 (단, 직전 시도 후 MIN_INTERVAL이 지나야 함)
     * - 오늘 메뉴여도 STALE_AFTER가 지나면 한 번 더 확인 (메뉴가 바뀌는 경우 대비)
     */
    fun needsRefresh(now: Long = System.currentTimeMillis()): Boolean {
        val lastAttempt = maxOf(cached()?.fetchedAt ?: 0L, lastErrorAt() ?: 0L)
        if (now - lastAttempt < MIN_INTERVAL_MS) return false
        val c = cached() ?: return true
        if (c.menu.date != LocalDate.now()) return true
        return now - c.fetchedAt > STALE_AFTER_MS
    }

    suspend fun refresh(): CachedMenu = withContext(Dispatchers.IO) {
        try {
            val menu = MealParser.parse(download())
            val result = CachedMenu(menu, System.currentTimeMillis())
            prefs.edit()
                .putString(KEY_MENU, encode(result))
                .remove(KEY_ERROR_AT)
                .apply()
            result
        } catch (e: Exception) {
            prefs.edit().putLong(KEY_ERROR_AT, System.currentTimeMillis()).apply()
            throw e
        }
    }

    private fun download(): String {
        val conn = URL(SITE_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "KMLAMealWidget/1.0 (Android)")
            conn.setRequestProperty("Accept-Language", "ko-KR,ko")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(c: CachedMenu): String {
        val meals = JSONObject()
        c.menu.meals.forEach { (type, items) -> meals.put(type.name, JSONArray(items)) }
        return JSONObject()
            .put("date", c.menu.date?.toString() ?: "")
            .put("dateLabel", c.menu.dateLabel)
            .put("fetchedAt", c.fetchedAt)
            .put("meals", meals)
            .toString()
    }

    private fun decode(s: String): CachedMenu {
        val o = JSONObject(s)
        val mealsJson = o.getJSONObject("meals")
        val meals = MealType.entries.associateWith { type ->
            val arr = mealsJson.optJSONArray(type.name) ?: JSONArray()
            List(arr.length()) { arr.getString(it) }
        }
        val date = o.optString("date").takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        return CachedMenu(
            menu = DailyMenu(date, o.optString("dateLabel"), meals),
            fetchedAt = o.getLong("fetchedAt"),
        )
    }

    companion object {
        const val SITE_URL = "https://kmlaonline.net/"
        private const val PREFS_NAME = "meal_cache"
        private const val KEY_MENU = "menu"
        private const val KEY_ERROR_AT = "error_at"
        private const val MIN_INTERVAL_MS = 20 * 60 * 1000L       // 20분
        private const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L    // 3시간
    }
}
