package com.householdbudget.app

import android.app.Application
import com.householdbudget.app.data.preferences.ProfileManager
import com.householdbudget.app.data.work.RecurringWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class BudgetApplication : Application() {
    lateinit var profileManager: ProfileManager
        private set

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        profileManager = ProfileManager(this)
        val profileId = runBlocking {
            profileManager.ensureDefaultProfile()
            profileManager.getCurrentProfileIdSnapshot()
        }
        container = AppContainer(this, profileId)
        applicationScope.launch {
            runCatching {
                container.budgetRepository.applyDueRecurringRules()
                container.budgetRepository.tryArchiveCompletedPeriods()
            }
        }
        RecurringWorkScheduler.schedule(this)
    }

    /** 프로필 전환 시 호출 — 새 profileId에 맞는 AppContainer로 교체. */
    fun reinitializeForProfile(profileId: Long) {
        container = AppContainer(this, profileId)
    }
}
