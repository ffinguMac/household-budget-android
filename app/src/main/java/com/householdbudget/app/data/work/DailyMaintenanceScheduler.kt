package com.householdbudget.app.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DailyMaintenanceScheduler {
    private const val UNIQUE_NAME = "household_budget_daily_maintenance"
    private const val LEGACY_UNIQUE_NAME = "household_budget_recurring_rules"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        // 업데이트한 기기에 남은 옛 주기 작업이 삭제된 워커 클래스를 찾다 실패하지 않게 취소한다.
        workManager.cancelUniqueWork(LEGACY_UNIQUE_NAME)
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
