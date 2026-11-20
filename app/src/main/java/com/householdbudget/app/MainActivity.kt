package com.householdbudget.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
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
    /** 리마인더 알림으로 열릴 때마다 1씩 늘려 "확인 필요" 화면으로 보낸다. */
    private val openPendingSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consumeOpenPending(intent)
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
                        openPendingSignal = openPendingSignal.intValue,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeOpenPending(intent)
    }

    private fun consumeOpenPending(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PENDING, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PENDING)
            openPendingSignal.intValue++
        }
    }

    override fun onResume() {
        super.onResume()
        // shortcut: 앱을 켠 채 자정을 넘기면 다음 복귀 때 갱신된다, 자정 틱이 필요해지면 추가
        (application as BudgetApplication).container.budgetRepository.refreshToday()
    }

    companion object {
        const val EXTRA_OPEN_PENDING = "open_pending"
    }
}

@Composable
private fun BudgetApp(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
    openPendingSignal: Int,
) {
    BudgetNavHost(
        budgetViewModel = budgetViewModel,
        calendarViewModel = calendarViewModel,
        repository = repository,
        openPendingSignal = openPendingSignal,
    )
}
