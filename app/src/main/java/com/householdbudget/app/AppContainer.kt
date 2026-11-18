package com.householdbudget.app

import android.content.Context
import com.householdbudget.app.autoentry.WatchedApps
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.AutoEntryRepository
import com.householdbudget.app.data.repository.BudgetRepository
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)

    val userPreferencesRepository = UserPreferencesRepository(appContext)

    // 인스턴스는 하나만 둔다(Mutex가 인스턴스별이다).
    val autoEntryRepository = AutoEntryRepository(
        database,
        watchedPackages = { WatchedApps.effective(userPreferencesRepository.watchedPackagesOrNull.first(), appContext) },
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
