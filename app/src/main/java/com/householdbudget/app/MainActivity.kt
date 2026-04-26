package com.householdbudget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.BudgetNavHost
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.BudgetViewModelFactory
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.calendar.CalendarViewModelFactory
import com.householdbudget.app.ui.onboarding.OnboardingScreen
import com.householdbudget.app.ui.theme.HouseholdBudgetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BudgetApplication
        setContent {
            HouseholdBudgetTheme {
                val onboardingDone by app.profileManager.onboardingCompleted
                    .collectAsStateWithLifecycle(initialValue = null)
                when (onboardingDone) {
                    null ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        )
                    false ->
                        OnboardingScreen(
                            profileManager = app.profileManager,
                            webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                        )
                    true ->
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
