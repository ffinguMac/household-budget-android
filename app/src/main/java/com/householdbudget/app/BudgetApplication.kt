package com.householdbudget.app

import android.app.Application
import com.householdbudget.app.data.work.RecurringWorkScheduler
import com.householdbudget.app.data.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BudgetApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            runCatching {
                container.budgetRepository.applyDueRecurringRules()
                container.budgetRepository.tryArchiveCompletedPeriods()
            }
        }
        RecurringWorkScheduler.schedule(this)
        // 기록 리마인더가 켜져 있으면 예약을 재보장한다.
        applicationScope.launch {
            runCatching {
                val prefs = container.userPreferencesRepository
                if (prefs.reminderEnabled.first()) {
                    ReminderScheduler.schedule(this@BudgetApplication, prefs.reminderHour.first())
                }
            }
        }
    }
}
