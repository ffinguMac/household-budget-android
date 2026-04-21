package com.householdbudget.app

import android.app.Application
import com.householdbudget.app.data.work.RecurringWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    }
}
