package com.householdbudget.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.householdbudget.app.data.repository.BudgetRepository
import com.householdbudget.app.ui.calendar.CalendarViewModel
import com.householdbudget.app.ui.edit.EditTransactionScreen

/** 라우트 문자열 정의와 조립을 한곳에 모은다. */
private object BudgetRoutes {
    const val TABS = "tabs"
    const val ADD = "add"
    const val ARG_TRANSACTION_ID = "transactionId"
    const val EDIT_PATTERN = "edit/{$ARG_TRANSACTION_ID}"

    fun edit(transactionId: Long): String = "edit/$transactionId"
}

/** 거래 편집기 진입/이탈 전환 지속시간. */
private const val EDITOR_TRANSITION_MS = 220

@Composable
fun BudgetNavHost(
    budgetViewModel: BudgetViewModel,
    calendarViewModel: CalendarViewModel,
    repository: BudgetRepository,
    openPending: Boolean = false,
    onOpenPendingHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(openPending) {
        // 알림으로 열면 편집 화면이 떠 있어도 탭 화면으로 돌아간다. 처리 완료는 탭 화면이 알린다.
        if (openPending) navController.popBackStack(BudgetRoutes.TABS, inclusive = false)
    }
    NavHost(navController = navController, startDestination = BudgetRoutes.TABS) {
        composable(
            route = BudgetRoutes.TABS,
            exitTransition = { fadeOut(animationSpec = tween(EDITOR_TRANSITION_MS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(EDITOR_TRANSITION_MS)) },
        ) {
            MainTabScaffold(
                budgetViewModel = budgetViewModel,
                calendarViewModel = calendarViewModel,
                repository = repository,
                onNavigateAdd = { navController.navigate(BudgetRoutes.ADD) },
                onNavigateEdit = { id -> navController.navigate(BudgetRoutes.edit(id)) },
                openPending = openPending,
                onOpenPendingHandled = onOpenPendingHandled,
            )
        }
        composable(
            route = BudgetRoutes.ADD,
            enterTransition = {
                slideInVertically(animationSpec = tween(EDITOR_TRANSITION_MS)) { it } +
                    fadeIn(animationSpec = tween(EDITOR_TRANSITION_MS))
            },
            popExitTransition = {
                slideOutVertically(animationSpec = tween(EDITOR_TRANSITION_MS)) { it } +
                    fadeOut(animationSpec = tween(EDITOR_TRANSITION_MS))
            },
        ) {
            EditTransactionScreen(
                budgetViewModel = budgetViewModel,
                repository = repository,
                transactionId = null,
                onClose = { navController.popBackStack(BudgetRoutes.TABS, inclusive = false) },
            )
        }
        composable(
            route = BudgetRoutes.EDIT_PATTERN,
            arguments =
                listOf(
                    navArgument(BudgetRoutes.ARG_TRANSACTION_ID) { type = NavType.LongType },
                ),
            enterTransition = {
                slideInVertically(animationSpec = tween(EDITOR_TRANSITION_MS)) { it } +
                    fadeIn(animationSpec = tween(EDITOR_TRANSITION_MS))
            },
            popExitTransition = {
                slideOutVertically(animationSpec = tween(EDITOR_TRANSITION_MS)) { it } +
                    fadeOut(animationSpec = tween(EDITOR_TRANSITION_MS))
            },
        ) { entry ->
            val transactionId =
                entry.arguments?.getLong(BudgetRoutes.ARG_TRANSACTION_ID) ?: return@composable
            EditTransactionScreen(
                budgetViewModel = budgetViewModel,
                repository = repository,
                transactionId = transactionId,
                onClose = { navController.popBackStack(BudgetRoutes.TABS, inclusive = false) },
            )
        }
    }
}
