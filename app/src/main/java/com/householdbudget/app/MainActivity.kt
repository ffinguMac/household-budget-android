package com.householdbudget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.BudgetNavHost
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.BudgetViewModelFactory
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.calendar.CalendarViewModelFactory
import com.householdbudget.app.ui.theme.HouseholdBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BudgetApplication
        setContent {
            HouseholdBudgetTheme {
                BudgetApp(
                    budgetViewModel =
                        viewModel(
                            factory = BudgetViewModelFactory(app.container.budgetRepository),
                        ),
                    calendarViewModel =
                        viewModel(
                            factory = CalendarViewModelFactory(app.container.budgetRepository),
                        ),
                    repository = app.container.budgetRepository,
                )
            }
        }
    }
}

@Composable
private fun BudgetApp(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
) {
    BudgetNavHost(
        budgetViewModel = budgetViewModel,
        calendarViewModel = calendarViewModel,
        repository = repository,
    )
}
