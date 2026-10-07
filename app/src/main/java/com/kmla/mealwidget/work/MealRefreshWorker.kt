package com.kmla.mealwidget.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kmla.mealwidget.data.MealRepository
import com.kmla.mealwidget.widget.WidgetRenderer
import java.util.concurrent.TimeUnit

/**
 * 30분마다 실행되어
 *  1) 필요하면(날짜가 바뀌었거나 오래됐으면) 사이트에서 메뉴를 새로 받고
 *  2) 시간대에 맞는 끼니로 위젯을 다시 그립니다.
 * 네트워크가 없어도 2)는 실행되도록 주기 작업에는 네트워크 조건을 걸지 않습니다.
 */
class MealRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = MealRepository(applicationContext)
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (force || repo.needsRefresh()) {
            try {
                repo.refresh()
            } catch (e: Exception) {
                Log.w(TAG, "급식 불러오기 실패", e)
            }
        }
        WidgetRenderer.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "MealRefreshWorker"
        private const val KEY_FORCE = "force"
        private const val PERIODIC_NAME = "meal_periodic"
        private const val ONE_SHOT_NAME = "meal_refresh_now"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MealRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }

        fun refreshNow(context: Context, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<MealRefreshWorker>()
                .setInputData(workDataOf(KEY_FORCE to force))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
