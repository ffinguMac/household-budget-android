package com.householdbudget.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.AutoEntryRepository
import com.householdbudget.app.data.repository.BudgetRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val userPreferencesRepository = UserPreferencesRepository(appContext)

    private val debuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // 인스턴스는 하나만 둔다(Mutex가 인스턴스별이다).
    val autoEntryRepository = AutoEntryRepository(
        database,
        // 사용자가 고르지 않는다: 설치된 정해 둔 금융 앱 전부.
        watchedPackages = { WatchedApps.defaults(appContext) },
        isCandidate = { WatchedApps.isAllowed(it, debuggable) },
    )

    val budgetRepository: BudgetRepository =
        BudgetRepository(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            archivedPeriodDao = database.archivedPeriodDao(),
            categoryBudgetDao = database.categoryBudgetDao(),
            userPreferencesRepository = userPreferencesRepository,
            onAutoRecategorized = autoEntryRepository::learnFromCorrection,
        )
}
