package com.householdbudget.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.householdbudget.app.BudgetApplication

class RecurringRuleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? BudgetApplication ?: return Result.failure()
            app.container.budgetRepository.applyDueRecurringRules()
            app.container.budgetRepository.tryArchiveCompletedPeriods()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
