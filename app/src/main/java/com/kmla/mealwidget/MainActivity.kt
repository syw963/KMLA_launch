package com.kmla.mealwidget

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.kmla.mealwidget.data.CachedMenu
import com.kmla.mealwidget.data.MealRepository
import com.kmla.mealwidget.data.MealType
import com.kmla.mealwidget.widget.WidgetRenderer
import com.kmla.mealwidget.work.MealRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 앱을 열면 오늘 세 끼를 한 화면에 보여주고, 위젯 추가 방법을 안내합니다. */
class MainActivity : Activity() {

    private val scope: CoroutineScope = MainScope()
    private lateinit var repo: MealRepository

    private lateinit var dateView: TextView
    private lateinit var statusView: TextView
    private lateinit var refreshButton: Button
    private val mealViews = mutableMapOf<MealType, TextView>()

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
        mealViews[MealType.BREAKFAST] = findViewById(R.id.breakfast_items)
        mealViews[MealType.LUNCH] = findViewById(R.id.lunch_items)
        mealViews[MealType.DINNER] = findViewById(R.id.dinner_items)

        refreshButton.setOnClickListener { refresh() }

        MealRefreshWorker.schedulePeriodic(this)
        render(repo.cached())
    }

    override fun onResume() {
        super.onResume()
        if (repo.cached()?.menu?.date != LocalDate.now()) refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
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
        if (cached == null) {
            dateView.text = getString(R.string.app_name)
            mealViews.values.forEach { it.text = "—" }
            statusView.text = ""
            return
        }
        val menu = cached.menu
        dateView.text = menu.date?.format(DATE_FMT) ?: menu.dateLabel
        for ((type, view) in mealViews) {
            val items = menu.itemsOf(type)
            view.text = if (items.isEmpty()) getString(R.string.no_menu, type.label)
            else items.joinToString("\n")
        }
        val time = Instant.ofEpochMilli(cached.fetchedAt).atZone(ZoneId.systemDefault())
        statusView.text = getString(R.string.updated_at, time.format(TIME_FMT))
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
        private val TIME_FMT = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN)
    }
}
