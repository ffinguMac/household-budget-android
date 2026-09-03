package com.householdbudget.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.security.AppLockGate
import com.householdbudget.app.ui.BudgetNavHost
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.BudgetViewModelFactory
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.calendar.CalendarViewModelFactory
import com.householdbudget.app.ui.theme.HouseholdBudgetTheme

// BiometricPrompt(AppLockGate)가 FragmentActivity 를 요구해 ComponentActivity 대신 상속한다.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 미드나잇 다크 단일 테마: 시스템 설정과 무관하게 상태바/내비바 아이콘은 항상 라이트.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val app = application as BudgetApplication
        val preferences = app.container.userPreferencesRepository
        setContent {
            HouseholdBudgetTheme {
                AppLockGate(preferences = preferences) {
                    BudgetApp(
                        budgetViewModel =
                            viewModel(
                                factory =
                                    BudgetViewModelFactory(
                                        app.container.budgetRepository,
                                        preferences,
                                    ),
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
