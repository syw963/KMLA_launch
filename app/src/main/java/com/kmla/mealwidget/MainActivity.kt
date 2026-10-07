package com.kmla.mealwidget

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kmla.mealwidget.data.CachedMenu
import com.kmla.mealwidget.data.FontSize
import com.kmla.mealwidget.data.MealRepository
import com.kmla.mealwidget.data.MealType
import com.kmla.mealwidget.data.MenuFormatter
import com.kmla.mealwidget.data.Settings
import com.kmla.mealwidget.widget.WidgetRenderer
import com.kmla.mealwidget.work.MealRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 앱을 열면 오늘 세 끼를 한 화면에 보여주고, 글자 크기 등 설정을 바꿀 수 있습니다. */
class MainActivity : Activity() {

    private class MealCardViews(val card: LinearLayout, val title: TextView, val items: TextView)

    private val scope: CoroutineScope = MainScope()
    private lateinit var repo: MealRepository

    private lateinit var dateView: TextView
    private lateinit var statusView: TextView
    private lateinit var refreshButton: Button
    private val cards = mutableMapOf<MealType, MealCardViews>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = MealRepository(this)

        val root = findViewById<View>(R.id.root)
        val basePadding = root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = basePadding + bars.top, bottom = basePadding + bars.bottom)
            insets
        }

        dateView = findViewById(R.id.date)
        statusView = findViewById(R.id.status)
        refreshButton = findViewById(R.id.refresh)
        cards[MealType.BREAKFAST] = MealCardViews(
            findViewById(R.id.breakfast_card), findViewById(R.id.breakfast_title), findViewById(R.id.breakfast_items),
        )
        cards[MealType.LUNCH] = MealCardViews(
            findViewById(R.id.lunch_card), findViewById(R.id.lunch_title), findViewById(R.id.lunch_items),
        )
        cards[MealType.DINNER] = MealCardViews(
            findViewById(R.id.dinner_card), findViewById(R.id.dinner_title), findViewById(R.id.dinner_items),
        )

        refreshButton.setOnClickListener { refresh() }
        setupSettings()

        MealRefreshWorker.schedulePeriodic(this)
        render(repo.cached())
    }

    override fun onResume() {
        super.onResume()
        if (repo.cached()?.menu?.date != LocalDate.now()) refresh() else render(repo.cached())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun setupSettings() {
        val group = findViewById<RadioGroup>(R.id.font_size_group)
        val current = Settings.fontSize(this)
        val accent = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.widget_accent))
        FontSize.entries.forEach { size ->
            val button = RadioButton(this).apply {
                id = View.generateViewId()
                text = size.label
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.widget_text_primary))
                buttonTintList = accent
                layoutParams = RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f)
                isChecked = size == current
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        Settings.setFontSize(this@MainActivity, size)
                        render(repo.cached())
                        WidgetRenderer.updateAll(this@MainActivity)
                    }
                }
            }
            group.addView(button)
        }

        findViewById<Switch>(R.id.dim_staples).apply {
            isChecked = Settings.dimStaples(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                Settings.setDimStaples(this@MainActivity, checked)
                render(repo.cached())
                WidgetRenderer.updateAll(this@MainActivity)
            }
        }
    }

    private fun refresh() {
        refreshButton.isEnabled = false
        statusView.text = getString(R.string.refreshing)
        scope.launch {
            try {
                render(repo.refresh())
                WidgetRenderer.updateAll(this@MainActivity)
            } catch (e: Exception) {
                render(repo.cached())
                statusView.text = getString(R.string.refresh_error, e.message ?: e.javaClass.simpleName)
            } finally {
                refreshButton.isEnabled = true
            }
        }
    }

    private fun render(cached: CachedMenu?) {
        val scale = Settings.fontSize(this).scale
        val dim = if (Settings.dimStaples(this)) ContextCompat.getColor(this, R.color.widget_text_tertiary) else null
        val now = MealType.forTime(LocalTime.now())
        val isToday = cached?.menu?.date == LocalDate.now()

        for ((type, v) in cards) {
            v.items.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * scale)
            v.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * scale)
            val current = isToday && type == now
            v.title.text = if (current) getString(R.string.now_badge, type.label) else type.label
            v.card.setBackgroundResource(if (current) R.drawable.card_current_bg else R.drawable.card_bg)
            val items = cached?.menu?.itemsOf(type).orEmpty()
            v.items.text = when {
                cached == null -> "—"
                items.isEmpty() -> getString(R.string.no_menu, type.label)
                else -> MenuFormatter.format(items, "\n", dim)
            }
        }

        if (cached == null) {
            dateView.text = getString(R.string.app_name)
            statusView.text = ""
            return
        }
        dateView.text = cached.menu.date?.format(DATE_FMT) ?: cached.menu.dateLabel
        val time = Instant.ofEpochMilli(cached.fetchedAt).atZone(ZoneId.systemDefault())
        statusView.text = getString(R.string.updated_at, time.format(TIME_FMT))
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)
        private val TIME_FMT = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)
    }
}
