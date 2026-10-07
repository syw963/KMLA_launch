package com.kmla.mealwidget.data

import android.content.Context

/** 글자 크기 단계. scale은 기본 크기에 곱하는 배율. */
enum class FontSize(val label: String, val scale: Float) {
    SMALL("작게", 0.85f),
    NORMAL("보통", 1.0f),
    LARGE("크게", 1.15f),
    XLARGE("아주 크게", 1.3f),
}

/** 사용자 설정(글자 크기, 기본 반찬 흐리게). 앱과 위젯이 같이 씁니다. */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_FONT = "font_size"
    private const val KEY_DIM = "dim_staples"

    fun fontSize(context: Context): FontSize {
        val name = prefs(context).getString(KEY_FONT, null)
        return FontSize.entries.firstOrNull { it.name == name } ?: FontSize.NORMAL
    }

    fun setFontSize(context: Context, size: FontSize) {
        prefs(context).edit().putString(KEY_FONT, size.name).apply()
    }

    fun dimStaples(context: Context): Boolean = prefs(context).getBoolean(KEY_DIM, true)

    fun setDimStaples(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DIM, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
