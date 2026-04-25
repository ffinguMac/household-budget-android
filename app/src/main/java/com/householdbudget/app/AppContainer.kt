package com.householdbudget.app

import android.content.Context
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.ProfileManager
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.BudgetRepository

class AppContainer(
    context: Context,
    profileId: Long = ProfileManager.DEFAULT_PROFILE_ID,
) {
    private val appContext = context.applicationContext
    private val dbName = ProfileManager.dbNameForProfile(profileId)
    private val database = AppDatabase.getInstance(appContext, dbName)
    private val userPreferencesRepository = UserPreferencesRepository(appContext)

    val budgetRepository: BudgetRepository =
        BudgetRepository(
            database = database,
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            recurringRuleDao = database.recurringRuleDao(),
            archivedPeriodDao = database.archivedPeriodDao(),
            categoryBudgetDao = database.categoryBudgetDao(),
            userPreferencesRepository = userPreferencesRepository,
        )
}
