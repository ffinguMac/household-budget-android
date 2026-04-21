package com.householdbudget.app.data.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RecurringWorkScheduler {
    private const val UNIQUE_NAME = "household_budget_recurring_rules"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val request =
            PeriodicWorkRequestBuilder<RecurringRuleWorker>(1, TimeUnit.DAYS)
                .build()
        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }
}
