package com.householdbudget.app.ui

import androidx.compose.runtime.Composable
import com.householdbudget.app.ui.edit.EditTransactionScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.calendar.CalendarViewModel

@Composable
fun BudgetNavHost(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_TABS) {
        composable(ROUTE_TABS) {
            MainTabScaffold(
                budgetViewModel = budgetViewModel,
                calendarViewModel = calendarViewModel,
                repository = repository,
                onNavigateAdd = { navController.navigate(ROUTE_ADD) },
                onNavigateEdit = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(ROUTE_ADD) {
            EditTransactionScreen(
                budgetViewModel = budgetViewModel,
                repository = repository,
                transactionId = null,
                onClose = { navController.popBackStack() },
            )
        }
        composable(
            route = "edit/{transactionId}",
            arguments =
                listOf(
                    navArgument("transactionId") { type = NavType.LongType },
                ),
        ) { entry ->
            val id = entry.arguments!!.getLong("transactionId")
            EditTransactionScreen(
                budgetViewModel = budgetViewModel,
                repository = repository,
                transactionId = id,
                onClose = { navController.popBackStack() },
            )
        }
    }
}

private const val ROUTE_TABS = "tabs"
private const val ROUTE_ADD = "add"
