package com.householdbudget.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.security.AppLockGate
import com.householdbudget.app.ui.BudgetNavHost
import com.householdbudget.app.ui.BudgetViewModel
import com.householdbudget.app.ui.BudgetViewModelFactory
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.calendar.CalendarViewModelFactory
import com.householdbudget.app.ui.theme.HouseholdBudgetTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// BiometricPrompt(AppLockGate)가 FragmentActivity 를 요구해 ComponentActivity 대신 상속한다.
class MainActivity : FragmentActivity() {
    /** 리마인더 알림으로 열렸으면 true. "확인 필요" 화면으로 옮긴 뒤 false 로 되돌린다(1회성). */
    private val openPending = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consumeOpenPending(intent)
        val app = application as BudgetApplication
        val preferences = app.container.userPreferencesRepository
        // shortcut: 첫 읽기를 막고 기다린다(수 ms). 느려지면 스플래시로 옮긴다
        val initialDark = runBlocking { preferences.darkTheme.first() }
        applySystemBars(initialDark)
        setContent {
            val dark by preferences.darkTheme.collectAsStateWithLifecycle(initialDark)
            LaunchedEffect(dark) { applySystemBars(dark) }
            HouseholdBudgetTheme(dark = dark) {
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
                        openPending = openPending.value,
                        onOpenPendingHandled = { openPending.value = false },
                    )
                }
            }
        }
    }

    // 시스템 다크모드와 무관하게 고른 테마에 맞춰 상태바/내비바 아이콘 색을 정한다.
    private fun applySystemBars(dark: Boolean) {
        val style =
            if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeOpenPending(intent)
    }

    private fun consumeOpenPending(intent: Intent?) {
        // 최근 앱에서 다시 열면 예전 인텐트가 그대로 오므로 무시한다.
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (!fromHistory && intent?.getBooleanExtra(EXTRA_OPEN_PENDING, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PENDING)
            openPending.value = true
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
    openPending: Boolean,
    onOpenPendingHandled: () -> Unit,
) {
    BudgetNavHost(
        budgetViewModel = budgetViewModel,
        calendarViewModel = calendarViewModel,
        repository = repository,
        openPending = openPending,
        onOpenPendingHandled = onOpenPendingHandled,
    )
}
