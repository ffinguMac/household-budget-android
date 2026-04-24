package com.householdbudget.app

import android.content.Context
import com.householdbudget.app.data.local.AppDatabase
import com.householdbudget.app.data.preferences.UserPreferencesRepository
import com.householdbudget.app.data.repository.BudgetRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
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
